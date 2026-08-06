package com.example.feedlite.data

import java.util.regex.Pattern

/**
 * 轻量 HTML → 纯文本转换。
 *
 * RSS description 多为富文本 HTML 片段，详情页展示纯文本：
 * 1. 去标签；2. 解码常见实体；3. 归一化空白。
 */
object HtmlText {

    private val TAG = Regex("""<[^>]+>""")
    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
    )

    fun toPlainText(html: String): String {
        if (html.isEmpty()) return ""
        var s = html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")
        s = TAG.replace(s, "")
        for ((k, v) in ENTITIES) s = s.replace(k, v)
        // 数字实体（&#NNN;）兜底解码
        s = Pattern.compile("&#(\\d+);").matcher(s).replaceAll { mr ->
            val code = mr.group(1).toIntOrNull() ?: return@replaceAll ""
            if (code in 32..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return s.replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    /** 摘要：去 HTML + 截断。 */
    fun excerpt(html: String, maxLength: Int = 90): String {
        val plain = toPlainText(html).replace('\n', ' ')
        return if (plain.length <= maxLength) plain else plain.take(maxLength).trimEnd() + "…"
    }
}
