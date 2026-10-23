package cn.ac.oac.glimbrowser.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 错误映射单测（附件 C.8 错误码映射 + D.3 文案表；PRD §3.2.6 降级矩阵）。
 */
class HttpErrorMapperTest {

    @Test
    fun statusMappingMatchesAttachmentC8() {
        assertEquals(AiError.AUTH_INVALID, HttpErrorMapper.fromStatus(401))
        assertEquals(AiError.AUTH_INVALID, HttpErrorMapper.fromStatus(403))
        assertEquals(AiError.QUOTA_EXCEEDED, HttpErrorMapper.fromStatus(402))
        assertEquals(AiError.NOT_FOUND, HttpErrorMapper.fromStatus(404))   // D.3：Base URL 漏 /v1
        assertEquals(AiError.RATE_LIMITED, HttpErrorMapper.fromStatus(429))
        assertEquals(AiError.UNKNOWN, HttpErrorMapper.fromStatus(500))
    }

    @Test
    fun retryabilityMatchesMatrix() {
        // PRD §3.2.6：429 退避重试、超时可重试；401/402/404 不重试
        assertTrue(HttpErrorMapper.shouldRetry(AiError.RATE_LIMITED))
        assertTrue(HttpErrorMapper.shouldRetry(AiError.TIMEOUT))
        assertTrue(HttpErrorMapper.shouldRetry(AiError.NETWORK_UNREACHABLE))
        assertFalse(HttpErrorMapper.shouldRetry(AiError.AUTH_INVALID))
        assertFalse(HttpErrorMapper.shouldRetry(AiError.QUOTA_EXCEEDED))
        assertFalse(HttpErrorMapper.shouldRetry(AiError.NOT_FOUND))
    }

    @Test
    fun userMessagesAreHumanized_noStackTraces() {
        // 附件 D.3：文案人话；不得含堆栈/类名
        for (e in AiError.entries) {
            assertFalse(e.userMessage.contains("Exception"))
            assertFalse(e.userMessage.contains("at cn."))
            assertTrue(e.userMessage.isNotBlank())
        }
        assertTrue(AiError.AUTH_INVALID.userMessage.contains("API Key"))
        assertTrue(AiError.NOT_FOUND.userMessage.contains("/v1"))   // D.3 专条提示
    }

    @Test
    fun throwableMappingCoversNetworkClasses() {
        assertEquals(AiError.NETWORK_UNREACHABLE, HttpErrorMapper.fromThrowable(javaUnknownHost()))
        assertEquals(AiError.TIMEOUT, HttpErrorMapper.fromThrowable(javaTimeout()))
        assertEquals(AiError.TLS_FAILED, HttpErrorMapper.fromThrowable(javaSsl()))
        assertEquals(AiError.UNKNOWN, HttpErrorMapper.fromThrowable(RuntimeException("boom")))
    }

    // 用类名模拟平台异常（字符串匹配逻辑跨平台一致，PRD 附录 B 行为差异抹平）
    private fun javaUnknownHost(): Throwable = java.net.UnknownHostException("api.example")
    private fun javaTimeout(): Throwable = java.net.SocketTimeoutException("timeout")
    private fun javaSsl(): Throwable = javax.net.ssl.SSLHandshakeException("bad cert")
}
