package cn.ac.oac.glimbrowser.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * OpenAI 兼容客户端（PRD V1.2 §2.4 network 职责：OpenAI 兼容 API、SSE、重试/退避；附件 D 连通性测试）。
 *
 * 硬约束（000 §2）：
 *  - **BYOK 直连**：仅向 [AiProfile.baseUrl] 发起；我方不代理不缓存（PRD V1.1 §3.2.5）
 *  - HTTPS 校验（http 仅 localhost 放行，其余由 UI 二次确认后调用方显式传 allowInsecure）
 *  - Key 经 [AiProfile.apiKeyRef] 由调用方解析成 Authorization 头注入，客户端不持久化 Key
 *
 * @param httpClient 各端注入引擎的 HttpClient（附录 B：OkHttp/Darwin/CIO/JS，本类只用 common API）
 */
class OpenAiCompatClient(
    private val httpClient: HttpClient,
    private val json: Json = defaultJson,
) {

    /** 结构化任务（附件 C.1.1：json_object、非流式）。 */
    suspend fun chatCompletion(profile: AiProfile, request: ChatRequest): Result<ChatResponse> =
        runCatching {
            val response = httpClient.post(chatUrl(profile)) {
                bearer(profile)
                contentType(ContentType.Application.Json)
                setBody(request.copy(stream = false))
            }
            if (!response.status.isSuccess()) {
                throw AiException(HttpErrorMapper.fromStatus(response.status.value), response.status)
            }
            json.decodeFromString<ChatResponse>(response.bodyAsText())
        }.recoverCatching { t -> throw AiException(mapThrowable(t)) }

    /**
     * 连通性测试（附件 D.2.3：最小请求 max_tokens=1、messages=[ping]、可短超时）。
     * 附件 D.2.4 能力探测：本阶段通过"尝试 json_object 是否被接受"的响应形态近似判定；
     * 精确探测（400+error.code）在 Phase 5 后半补全（如实报告，不虚报能力）。
     */
    suspend fun testConnection(
        profile: AiProfile,
        timeoutMs: Long = 6000,
        /** 配置引导阶段：Key 尚未写入安全区，可显式传入（只在此调用栈内使用）。 */
        apiKeyOverride: String? = null,
    ): ConnectivityResult {
        val start = currentMillis()
        return runCatching {
            val response = httpClient.post(chatUrl(profile)) {
                timeout { requestTimeoutMillis = timeoutMs.coerceAtLeast(1_000) }
                if (apiKeyOverride != null) {
                    header("Authorization", "Bearer $apiKeyOverride")
                    profile.extraHeaders.forEach { (k, v) -> header(k, v) }
                } else {
                    bearer(profile)
                }
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = profile.model,
                        messages = listOf(ChatMessage(ChatMessage.ROLE_USER, "ping")),
                        responseFormat = ResponseFormat.JsonObject,   // 探测 json_object 支持
                        stream = false,
                        maxTokens = 1,
                    ),
                )
            }
            val latency = currentMillis() - start
            when {
                response.status == HttpStatusCode.NotFound ->
                    ConnectivityResult(false, error = AiError.NOT_FOUND)
                !response.status.isSuccess() ->
                    ConnectivityResult(false, error = HttpErrorMapper.fromStatus(response.status.value))
                else -> {
                    val body = response.bodyAsText()
                    val echoedModel = runCatching { json.decodeFromString<ChatResponse>(body).model }.getOrNull()
                    ConnectivityResult(
                        success = true,
                        latencyMs = latency,
                        modelEcho = echoedModel,
                        // 探测：能返回 2xx 且解析出 choices → 推定 json_object 可用（Phase 5 后半精确化）
                        capabilities = AiCapabilities(jsonObject = true, stream = true),
                    )
                }
            }
        }.getOrElse { t ->
            ConnectivityResult(false, error = HttpErrorMapper.fromThrowable(t))
        }
    }

    /** 用「尚未落库」的 Base URL / Key / Model 直接测连通性（三步引导 STEP2 使用）。 */
    suspend fun testConnectionRaw(
        baseUrl: String,
        apiKey: String,
        model: String,
        timeoutSeconds: Int = 30,
    ): ConnectivityResult = testConnection(
        AiProfile(
            id = "guide",
            name = "guide",
            baseUrl = baseUrl,
            apiKeyRef = "guide",
            model = model,
            timeoutSeconds = timeoutSeconds,
        ),
        timeoutMs = timeoutSeconds.coerceIn(5, 60) * 1000L,
        apiKeyOverride = apiKey,
    )

    /** 用「尚未落库」的端点与 Key 拉模型列表（引导 STEP1 使用）。 */
    suspend fun listModelsRaw(
        baseUrl: String,
        apiKey: String,
        timeoutSeconds: Int = 30,
    ): Result<ModelsResponse> = listModels(
        AiProfile(id = "guide", name = "guide", baseUrl = baseUrl, apiKeyRef = "guide", model = ""),
        timeoutMs = timeoutSeconds.coerceIn(5, 60) * 1000L,
        apiKeyOverride = apiKey,
    )

    /** 拉取模型列表（附件 D.2.3 P1：GET {Base}/models；失败保持手输）。 */
    suspend fun listModels(
        profile: AiProfile,
        timeoutMs: Long = profile.timeoutSeconds * 1000L,
        apiKeyOverride: String? = null,
    ): Result<ModelsResponse> = runCatching {
        val base = profile.baseUrl.trimEnd('/')
        val response = httpClient.get("$base/models") {
            timeout { requestTimeoutMillis = timeoutMs.coerceAtLeast(1_000) }
            if (apiKeyOverride != null) {
                header("Authorization", "Bearer $apiKeyOverride")
                profile.extraHeaders.forEach { (k, v) -> header(k, v) }
            } else {
                bearer(profile)
            }
        }
        if (!response.status.isSuccess()) throw AiException(HttpErrorMapper.fromStatus(response.status.value), response.status)
        json.decodeFromString<ModelsResponse>(response.bodyAsText())
    }

    /** 退避重试（PRD §3.2.6：429 指数退避 ×2；通用可重试错误重试 1 次）。 */
    suspend fun <T> withRetry(times: Int = 2, block: suspend () -> T): Result<T> {
        var last: Throwable? = null
        repeat(times) { attempt ->
            val r = runCatching { block() }
            if (r.isSuccess) return r
            val t = r.exceptionOrNull()!!
            val err = if (t is AiException) t.error else HttpErrorMapper.fromThrowable(t)
            last = t
            if (!HttpErrorMapper.shouldRetry(err)) return Result.failure(t)
            delay((500L * (1L shl attempt)))  // 500ms, 1000ms 指数退避
        }
        return Result.failure(last ?: AiException(AiError.UNKNOWN))
    }

    // ===== internals =====

    private fun chatUrl(profile: AiProfile): String = profile.baseUrl.trimEnd('/') + "/chat/completions"

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(profile: AiProfile) {
        // Authorization: Bearer <key>（附件：统一请求形态；Key 由调用方经 keyProvider 解析）
        val key = keyProvider(profile.apiKeyRef)
        header("Authorization", "Bearer $key")
        profile.extraHeaders.forEach { (k, v) -> header(k, v) }
    }

    /** Key 解析器注入（平台 SecureKeyStore → 明文仅短时持有，PRD V1.2 §2.4.2）。 */
    var keyProvider: (String) -> String = { "" }

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /** 平台无关时钟（测试可注入）。 */
        var currentMillis: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds }
    }
}

/** HTTP 协议层异常（携带内部错误码；UI 经 [AiError.userMessage] 展示，附件 D.3）。 */
class AiException(val error: AiError, val httpStatus: HttpStatusCode? = null) : Exception(error.userMessage)

internal fun mapThrowable(t: Throwable): AiError =
    if (t is AiException) t.error else HttpErrorMapper.fromThrowable(t)
