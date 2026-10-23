package cn.ac.oac.glimbrowser.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SSE 增量解析单测（附件 C.4.4/C.5 对话类流式；转义处理防注入，PRD §3.2.6）。
 * 直接测 [SseDeltaParser]（纯函数，无客户端/引擎依赖）。
 */
class OpenAiSseParseTest {

    @Test
    fun parsesPlainContent() {
        val data = """{"choices":[{"delta":{"content":"你好"}}]}"""
        assertEquals("你好", SseDeltaParser.parseDeltaContent(data))
    }

    @Test
    fun parsesEscapedChars() {
        val bs = "\\"  // 源码级：一个反斜杠字符
        val data = "{\"choices\":[{\"delta\":{\"content\":\"a" + bs + "n" + "b" + bs + "t" + "c" + bs + "\"" + "d\"}}]}"
        assertEquals("a\nb\tc\"d", SseDeltaParser.parseDeltaContent(data))  // a\n b \t c \" d
    }

    @Test
    fun parsesUnicodeEscape() {
        val bs = "\\"  // 源码级：一个反斜杠字符
        val data = "{\"choices\":[{\"delta\":{\"content\":\"" + bs + "u4f60" + bs + "u597d\"}}]}"
        assertEquals("你好", SseDeltaParser.parseDeltaContent(data))
    }

    @Test
    fun emptyWhenNoContentField() {
        assertEquals("", SseDeltaParser.parseDeltaContent("""{"choices":[{"delta":{}}]}"""))
        assertEquals("", SseDeltaParser.parseDeltaContent("[DONE]"))
        assertEquals("", SseDeltaParser.parseDeltaContent(""))
    }

    @Test
    fun deltaTextIsPlainTextOnly_injectionSafeAtRenderLayer() {
        // 解析层原样取出（不执行/不解释），防注入由渲染层纯文本化保证（PRD §3.2.6）
        val data = """{"choices":[{"delta":{"content":"<script>alert(1)</script>"}}]}"""
        assertEquals("<script>alert(1)</script>", SseDeltaParser.parseDeltaContent(data))
    }
}
