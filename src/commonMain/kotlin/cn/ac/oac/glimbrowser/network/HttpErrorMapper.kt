package cn.ac.oac.glimbrowser.network

/**
 * HTTP/传输错误 → [AiError] 映射（附件 C.8 错误码映射 + D.3 文案表；全平台一致）。
 * 红线：不向用户暴露堆栈/原始响应体（PRD §3.2.6 + §7 埋点红线）。
 */
object HttpErrorMapper {

    /** HTTP 状态码映射（PRD §3.2.6 降级矩阵）。 */
    fun fromStatus(code: Int): AiError = when (code) {
        401, 403 -> AiError.AUTH_INVALID
        402 -> AiError.QUOTA_EXCEEDED
        404 -> AiError.NOT_FOUND
        429 -> AiError.RATE_LIMITED
        in 500..599 -> AiError.UNKNOWN
        else -> AiError.UNKNOWN
    }

    /** Ktor 异常类名 → 错误码（字符串匹配避免 commonMain 依赖具体异常类型层级）。 */
    fun fromThrowable(t: Throwable): AiError {
        val n = t::class.simpleName ?: ""
        val msg = t.message ?: ""
        return when {
            n.contains("UnknownHost", true) || msg.contains("UnknownHost", true) -> AiError.NETWORK_UNREACHABLE
            n.contains("Connect", true) && !n.contains("ConnectTimeout", true) -> AiError.NETWORK_UNREACHABLE
            n.contains("ConnectTimeout", true) || n.contains("SocketTimeout", true) || n.contains("Timeout", true) -> AiError.TIMEOUT
            n.contains("SSL", true) || n.contains("Certificate", true) -> AiError.TLS_FAILED
            n.contains("IOException", true) -> AiError.NETWORK_UNREACHABLE
            else -> AiError.UNKNOWN
        }
    }

    /** 附件 C.8：结构化工具是否应走重试（RATE_LIMITED/TIMEOUT/网络类）。 */
    fun shouldRetry(error: AiError): Boolean = error.isRetryable
}
