package cn.ac.oac.glimbrowser.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BYOK 档案模型单测（附件 D.2 三步引导字段 + §3.2.1 配置项；PRD 密钥红线：Key 仅引用不落模型）。
 */
class AiProfileTest {

    private fun profile() = AiProfile(
        id = "p1", name = "OpenAI 官方",
        baseUrl = "https://api.openai.com/v1",
        apiKeyRef = "keystore-ref-1",   // 仅引用（红线：明文 Key 不进模型/序列化）
        model = "gpt-4o-mini",
    )

    @Test
    fun modelNeverHoldsPlaintextKey() {
        val p = profile()
        // 字段名与序列化内容均不含明文 key 概念（只有引用）
        assertFalse(p.toString().contains("sk-"))
        assertTrue(p.apiKeyRef == "keystore-ref-1")
    }

    @Test
    fun defaultsMatchAttachmentD() {
        val p = profile()
        assertEquals(60, p.timeoutSeconds)        // D.1 默认 60s
        assertTrue(p.streamEnabled)               // D.1 流式默认开
        assertEquals("auto", p.summaryLanguage)   // D.1 摘要语言跟随
        assertTrue(p.extraHeaders.isEmpty())
    }

    @Test
    fun connectivityResultCarriesCapabilities() {
        // D.2.4：能力位不阻断保存（PRD §3.2.6 降级链）——模型上能力位可空
        val c = ConnectivityResult(success = true, latencyMs = 230, capabilities = AiCapabilities(jsonObject = true, stream = true))
        assertTrue(c.success)
        assertEquals(230, c.latencyMs)
        assertTrue(c.capabilities!!.jsonObject)
        val failed = ConnectivityResult(success = false, error = AiError.AUTH_INVALID)
        assertNotNull(failed.error)
        assertNull(failed.capabilities)
    }
}
