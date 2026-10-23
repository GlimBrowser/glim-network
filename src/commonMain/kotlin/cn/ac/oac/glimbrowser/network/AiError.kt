package cn.ac.oac.glimbrowser.network

import kotlinx.serialization.Serializable

/**
 * 内部错误码（附件 C.8 错误对象与错误码映射 + D.3 用户文案翻译表；PRD §3.2.6 降级矩阵）。
 * 网络/协议错误统一映射到本枚举，UI 文案经 [userMessage] 全平台一致（附件 D.3 MUST）。
 */
@Serializable
enum class AiError(val userMessage: String, val retryable: Boolean) {
    /** 401/403（附件 D.3：主按钮"返回修改"，自动定位 Key 字段）。 */
    AUTH_INVALID("API Key 无效或无权限访问该模型。", false),

    /** 402 额度不足。 */
    QUOTA_EXCEEDED("该账户额度不足，请前往服务商处充值或更换配置。", false),

    /** 429 限流（指数退避重试 ×2，PRD §3.2.6）。 */
    RATE_LIMITED("请求过于频繁，请稍后再试。", true),

    /** 超时（默认 60s，可"继续等待/取消"）。 */
    TIMEOUT("连接超时（超过配置的秒数）。", true),

    /** DNS/连接失败。 */
    NETWORK_UNREACHABLE("无法连接到该地址，请检查 Base URL 或网络。", true),

    /** 404（常见 Base URL 漏 /v1，附件 D.3 专条）。 */
    NOT_FOUND("接口不存在。若使用 OpenAI 兼容服务，Base URL 通常以 /v1 结尾。", false),

    /** 响应非 JSON（非流式）。 */
    INVALID_RESPONSE("服务返回了无法识别的内容，可能不是 OpenAI 兼容接口。", true),

    /** 能力探测失败：端点不支持 response_format（降级链 C.1.5）。 */
    SCHEMA_UNSUPPORTED("该服务暂不支持结构化输出，将使用降级模式，仍可使用。", false),

    /** 结构化输出解析链全失败（C.1.5 末级 → 纯文本渲染降级）。 */
    PARSE_FAILED("结构化结果解析失败，已降级为纯文本展示。", false),

    /** 输出命中安全过滤（PRD §3.2.6 注入风险）。 */
    CONTENT_BLOCKED("内容已安全过滤。", false),

    /** HTTP 证书校验失败（附件 D.3）。 */
    TLS_FAILED("安全证书校验失败，连接已阻止。", false),

    /** 未知错误（兜底，不暴露堆栈）。 */
    UNKNOWN("发生未知错误，请稍后重试。", true),
    ;

    /** 附件 C.8：结构化工具错误行为（停止/降级）是否可重试（与 retryable 同义但语义化命名）。 */
    val isRetryable: Boolean get() = retryable
}
