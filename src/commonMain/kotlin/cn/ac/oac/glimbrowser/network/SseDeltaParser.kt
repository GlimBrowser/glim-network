package cn.ac.oac.glimbrowser.network

/**
 * SSE delta.content 解析（附件 C.4.4/C.5 对话类流式；纯函数，无客户端依赖，便于单测）。
 * 增量片段原样取出（渲染层按纯文本处理，PRD §3.2.6 防注入）。
 */
internal object SseDeltaParser {

    /** 从 SSE data 行解析 choices[0].delta.content。 */
    fun parseDeltaContent(data: String): String {
        val marker = "\"content\":\""
        val idx = data.indexOf(marker)
        if (idx < 0) return ""
        val sb = StringBuilder()
        var i = idx + marker.length
        while (i < data.length) {
            val c = data[i]
            if (c == '\\') {
                val pair = decodeEscape(data, i)
                sb.append(pair.first)
                i = pair.second
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** 解析 data[i]=='\\' 开始的转义对；返回 (解码字符, 下一游标)。 */
    private fun decodeEscape(data: String, i: Int): Pair<Char, Int> {
        if (i + 1 >= data.length) return '\\' to (i + 1)
        return when (val e = data[i + 1]) {
            'n' -> '\n' to i + 2
            't' -> '\t' to i + 2
            'r' -> '\r' to i + 2
            '"' -> '"' to i + 2
            '\\' -> '\\' to i + 2
            '/' -> '/' to i + 2
            'u' -> {
                if (i + 5 < data.length) {
                    val hex = data.substring(i + 2, i + 6)
                    val ch = hex.toIntOrNull(16)?.let { it.toChar() } ?: '�'
                    ch to i + 6
                } else {
                    'u' to i + 2
                }
            }
            else -> e to i + 2
        }
    }
}
