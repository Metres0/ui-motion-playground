package com.example.feedlite.data

import java.util.regex.Pattern

/**
 * 轻量 HTML → 纯文本转换。
 *
 * 1. 去标签；2. 解码常见实体；3. 解码十进制/十六进制数字实体；
 * 4. 归一化空白。
 *
 * 安全防护：数字实体解码时过滤代理区（0xD800-0xDFFF）与非法 code point，
 * 否则 `Character.toChars` 会抛 IllegalArgumentException 导致闪退。
 */
object HtmlText {

    private val TAG = Regex("""<[^>]+>""")
    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&middot;" to "·", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
    )

    private val DEC_ENTITY = Pattern.compile("&#(\\d+);")
    private val HEX_ENTITY = Pattern.compile("&#[xX]([0-9a-fA-F]+);")

    fun toPlainText(html: String): String {
        if (html.isEmpty()) return ""
        var s = html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "\n")
        s = TAG.replace(s, "")
        for ((k, v) in ENTITIES) s = s.replace(k, v)
        s = DEC_ENTITY.matcher(s).replaceAll { m -> decodeEntity(m.group(1).toIntOrNull()) }
        s = HEX_ENTITY.matcher(s).replaceAll { m ->
            m.group(1).toIntOrNull(16)?.let { decodeEntity(it) } ?: ""
        }
        return s.replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    /** 安全解码 code point；非法（代理区/越界/控制字符）返回空格，绝不让 toChars 抛异常。 */
    private fun decodeEntity(code: Int?): String {
        val c = code ?: return ""
        if (c < 32) return " "            // 控制字符
        if (c in 0xD800..0xDFFF) return " " // 代理区：Character.toChars 会抛异常
        if (c > 0x10FFFF) return " "
        return try {
            String(Character.toChars(c))
        } catch (e: IllegalArgumentException) {
            " "
        }
    }

    /** 摘要：去 HTML + 截断。 */
    fun excerpt(html: String, maxLength: Int = 90): String {
        val plain = toPlainText(html).replace('\n', ' ')
        return if (plain.length <= maxLength) plain else plain.take(maxLength).trimEnd() + "…"
    }
}
