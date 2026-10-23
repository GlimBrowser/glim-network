package cn.ac.oac.glimbrowser.network

import kotlinx.serialization.Serializable

/**
 * BYOK 配置档案（PRD V1.1 §3.2.1 API 配置 P0 + 附件 D.1/D.2 三步引导）。
 *
 * 多档案支持（PRD §3.2.1"多配置档案"）：列表持久化在 Phase 6（shared:data）；本模型为纯数据。
 * 安全红线（000 §2）：[apiKeyEncrypted] 为**平台 SecureKeyStore 加密后的密文**（或占位），
 * 任何 shared 明文接口不得要求明文 Key；展示层默认掩码（附件 D"默认掩码显示"）。
 *
 * @property baseUrl OpenAI 兼容 Base URL（如 https://api.openai.com/v1；以 /v1 结尾惯例，附件 D.3 404 提示）
 * @property apiKeyRef Key 引用标识（明文 Key 存平台 SecureKeyStore，此处仅引用名，防 Key 进入序列化/日志）
 * @property model 模型名（如 gpt-4o-mini / deepseek-chat）
 * @property timeoutSeconds 请求超时（默认 60s，PRD §3.2.6）
 * @property streamEnabled 流式输出开关（对话类默认开，附件 D.1）
 * @property summaryLanguage 摘要语言（跟随页面 / 指定；附件 D.1 高级设置）
 */
@Serializable
data class AiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    /** Key 引用（非明文）；真实 Key 存平台 SecureKeyStore（PRD V1.2 §2.4.2 SecureKeyStore）。 */
    val apiKeyRef: String,
    val model: String,
    val timeoutSeconds: Int = 60,
    val streamEnabled: Boolean = true,
    val summaryLanguage: String = "auto",
    /** 自定义 Header（PRD §3.2.1 可选）。 */
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 上次连通性测试结果（附件 D 状态卡）。 */
    val lastConnectivity: ConnectivityResult? = null,
) {
    /** 掩码展示（附件 D"Key 默认掩码，查看需生物识别"——此处仅展示用途，真实值不外泄）。 */
    val maskedBaseUrl: String get() = baseUrl

    companion object {
        /** 三步引导默认值（附件 D.2）。 */
        const val DEFAULT_NAME = "我的配置"
        const val DEFAULT_TIMEOUT_SECONDS = 60
    }
}

/**
 * 连通性测试结果（附件 D.2.3 + D.3 错误翻译表）。
 */
@Serializable
data class ConnectivityResult(
    val success: Boolean,
    /** 延迟 ms（成功时）。 */
    val latencyMs: Long? = null,
    /** 服务端返回的模型标识（如有）。 */
    val modelEcho: String? = null,
    /** 失败时的内部错误码（附件 C.8 / D.3 映射）。 */
    val error: AiError? = null,
    /** 能力位探测结果（附件 D.2.4：json_object / tools / stream）。 */
    val capabilities: AiCapabilities? = null,
)

/**
 * 端点能力位（附件 D.2.4：探测不支持**不阻断保存**，按降级链运行，PRD §3.2.6）。
 */
@Serializable
data class AiCapabilities(
    /** 支持 response_format=json_object（结构化任务）。 */
    val jsonObject: Boolean = false,
    /** 支持 tools/function calling（本版本未用，预留 PRD §3.2.6）。 */
    val tools: Boolean = false,
    /** 支持流式（对话类必需降级判断）。 */
    val stream: Boolean = true,
)
