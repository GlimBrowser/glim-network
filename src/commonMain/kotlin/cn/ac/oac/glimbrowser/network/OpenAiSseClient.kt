package cn.ac.oac.glimbrowser.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.client.request.url as setUrl
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * SSE 流式客户端（PRD V1.1 §3.2.0 对话类 stream=true + 附件 C.1.1「对话类不带 response_format」；
 * 附录 B：SSE 行为差异由 SseClient 抹平——心跳/中断/半包由 ktor-sse/ktor-client-core SSE 插件处理）。
 *
 * 用法（Phase 5 agent 层）：`streamChat(profile, request)` 返回增量文本 Flow；
 * 对话类工具（page_qa/global_chat/write_assist/selection_enhance.ask）走此路径，结构化工具走非流式。
 *
 * ⚠️ 调用方注入的 HttpClient 必须 `install(SSE)`（ktor-client-core 插件），否则 sse{} 抛 SSEClientException。
 */
class OpenAiSseClient(
    private val httpClient: HttpClient,
) {

    /**
     * 流式对话（附件 C.1.1 对话类矩阵：stream=true、无 response_format）。
     * @return 增量文本片段流（choices[].delta.content）；网络/协议错误 → [AiException]（上层经 [AiError.userMessage] 兜底）
     */
    suspend fun streamChat(profile: AiProfile, request: ChatRequest): Flow<String> = flow {
        httpClient.sse(
            request = {
                setUrl(profile.baseUrl.trimEnd('/') + "/chat/completions")
                header("Authorization", "Bearer ${resolveKey(profile)}")
                profile.extraHeaders.forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                // 对话类红线：stream=true 且不带 response_format（附件 C.1.1）
                setBody(request.copy(stream = true, responseFormat = null))
            },
        ) {
            incoming.collect { event: ServerSentEvent ->
                val data = event.data ?: return@collect
                if (data.trim() == "[DONE]") return@collect
                val piece = parseDeltaContent(data)
                if (piece.isNotEmpty()) emit(piece)
            }
        }
    }

    /** Key 解析（与 OpenAiCompatClient 一致，注入式，防明文持久化，PRD V1.2 §2.4.2）。 */
    var keyProvider: (String) -> String = { "" }
    private fun resolveKey(profile: AiProfile) = keyProvider(profile.apiKeyRef)

    /** 委托给 [SseDeltaParser]（同模块 internal；纯函数便于单测）。 */
    internal fun parseDeltaContent(data: String): String = SseDeltaParser.parseDeltaContent(data)
}
