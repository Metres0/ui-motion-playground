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

    /**
     * 判断正文是否有实质内容。
     * 过滤「点击查看原文」「查看全文」「阅读全文」等纯链接噪音后，剩余文本不足阈值视为无内容
     * （如 InfoQ 的 description 只有"点击查看原文"，应识别为无摘要）。
     */
    fun hasMeaningfulContent(html: String): Boolean {
        val plain = toPlainText(html).replace('\n', ' ').trim()
        if (plain.isEmpty()) return false
        var s = plain
        s = s.replace(Regex("""(点击|查看|阅读|打开|继续)?原文?|查看全文|阅读全文|点击查看"""), "")
        // 去掉纯 URL
        s = s.replace(Regex("""https?://\S+"""), "")
        s = s.replace(Regex("""[>\s]+"""), " ").trim()
        return s.length >= 20 || (plain.length >= 20)
    }
}
