package com.example.feedlite.ui.reader

import com.example.feedlite.data.HtmlText

/**
 * 轻量 HTML → 排版块 渲染器。
 *
 * 支持：h1-h6 / 段落 / 无序有序列表 / 引用 / 分隔线 / 代码块（pre、含 language）。
 * 行内样式：加粗、斜体、行内代码、链接（链接文本展示为 primary 色）、图片→[图片: alt]。
 * 代码块保持原文（**翻译时也保留**，见 CodeBlockExtractor）。
 */
object HtmlBlocks {

    sealed class Block {
        data class Paragraph(val spans: List<Span>) : Block()
        data class Heading(val level: Int, val spans: List<Span>) : Block()
        data class CodeBlock(val code: String, val language: String?) : Block()
        data class UnorderedList(val items: List<List<Span>>) : Block()
        data class OrderedList(val items: List<List<Span>>) : Block()
        data class Quote(val spans: List<Span>) : Block()
        data object Divider : Block()
    }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    private val BLOCK_LEVEL = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "ul", "ol", "li",
        "blockquote", "hr", "pre", "br", "table", "tr", "td", "th", "section", "article",
    )

    /** 把 RSS description HTML 解析为块序列。 */
    fun parse(html: String): List<Block> {
        val blocks = ArrayList<Block>()
        var i = 0
        val len = html.length
        val text = StringBuilder()

        fun flushText() {
            if (text.isNotBlank()) {
                val spans = inline(text.toString())
                if (spans.isNotEmpty()) blocks += Block.Paragraph(spans)
                text.setLength(0)
            }
        }

        while (i < len) {
            val open = html.indexOf('<', i)
            if (open < 0) {
                text.append(html, i, len)
                break
            }
            text.append(html, i, open)
            val close = html.indexOf('>', open)
            if (close < 0) {
                text.append(html, open, len)
                break
            }
            val tagLine = html.substring(open + 1, close).trim()
            val rawName = tagLine.substringBefore(' ').removePrefix("/").lowercase()
            val tagName = rawName.substringBefore('.') // 处理 h3.foo 之类

            when {
                tagName in listOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                    flushText()
                    val level = tagName[1].digitToInt()
                    val endTag = "</$tagName>"
                    val endIdx = html.indexOf(endTag, close)
                    val content = if (endIdx >= 0) html.substring(close + 1, endIdx) else ""
                    blocks += Block.Heading(level, inline(content))
                    i = if (endIdx >= 0) endIdx + endTag.length else len
                }

                tagName == "pre" -> {
                    flushText()
                    val endIdx = html.indexOf("</pre>", close)
                    val code = if (endIdx >= 0) html.substring(close + 1, endIdx) else ""
                    blocks += Block.CodeBlock(decodeCode(code), extractLang(tagLine))
                    i = if (endIdx >= 0) endIdx + "</pre>".length else len
                }

                tagName == "ul" || tagName == "ol" -> {
                    flushText()
                    val ordered = tagName == "ol"
                    val endTag = if (ordered) "</ol>" else "</ul>"
                    val listEnd = html.indexOf(endTag, close)
                    val segEnd = if (listEnd >= 0) listEnd else len
                    val seg = html.substring(close + 1, segEnd)
                    val items = ArrayList<List<Span>>()
                    val liRegex = Regex("""<li[^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE)
                    for (m in liRegex.findAll(seg)) {
                        val spans = inline(m.groupValues[1])
                        if (spans.isNotEmpty()) items += spans
                    }
                    if (items.isNotEmpty()) {
                        blocks += if (ordered) Block.OrderedList(items) else Block.UnorderedList(items)
                    }
                    i = if (listEnd >= 0) listEnd + endTag.length else segEnd
                }

                tagName == "blockquote" -> {
                    flushText()
                    val endIdx = html.indexOf("</blockquote>", close)
                    val content = if (endIdx >= 0) html.substring(close + 1, endIdx) else ""
                    blocks += Block.Quote(inline(content))
                    i = if (endIdx >= 0) endIdx + "</blockquote>".length else len
                }

                tagName == "hr" -> {
                    flushText()
                    blocks += Block.Divider
                    i = close + 1
                }

                tagName == "br" -> {
                    text.append('\n')
                    i = close + 1
                }

                tagName in BLOCK_LEVEL -> {
                    // p/div/li/table 等边界：结束标签补一个换行，分隔段落
                    if (tagLine.startsWith("/")) text.append('\n')
                    i = close + 1
                }

                else -> {
                    // 内联标签（a/strong/em/code/img…）原样保留，交给 inline 解析
                    i = close + 1
                }
            }
        }
        flushText()
        return blocks
    }

    /** 行内样式解析：strong/b、em/i、code、a、img、文本。 */
    fun inline(html: String): List<Span> {
        var s = html
            .replace(Regex("""(?i)<img[^>]*>""")) { m ->
                Regex("""(?i)alt=["']([^"']*)["']""").find(m.value)?.groupValues?.get(1)?.let { "[图片: $it]" } ?: ""
            }

        val pattern = Regex(
            """(?is)(<a\b[^>]*>.*?</a>|<strong>.*?</strong>|<b>.*?</b>|<em>.*?</em>|<i>.*?</i>|<code>.*?</code>|<br\s*/?>|[^<]+|<[^>]+>)"""
        )
        val spans = ArrayList<Span>()

        for (m in pattern.findAll(s)) {
            val t = m.value
            when {
                t.startsWith("<a", ignoreCase = true) -> {
                    val url = Regex("""(?i)href=["']([^"']+)["']""").find(t)?.groupValues?.get(1) ?: ""
                    val inner = t.replace(Regex("""(?i)</?a[^>]*>"""), "")
                    val plain = HtmlText.toPlainText(inner)
                    if (plain.isNotBlank()) spans += Span(plain, link = url)
                }
                t.startsWith("<strong>", true) || t.startsWith("<b>", true) -> {
                    val inner = t.substring(t.indexOf('>') + 1, t.lastIndexOf('<'))
                    val plain = HtmlText.toPlainText(inner)
                    if (plain.isNotBlank()) spans += Span(plain, bold = true)
                }
                t.startsWith("<em>", true) || t.startsWith("<i>", true) -> {
                    val inner = t.substring(t.indexOf('>') + 1, t.lastIndexOf('<'))
                    val plain = HtmlText.toPlainText(inner)
                    if (plain.isNotBlank()) spans += Span(plain, italic = true)
                }
                t.startsWith("<code>", true) -> {
                    val inner = t.substring(t.indexOf('>') + 1, t.lastIndexOf('<'))
                    val plain = HtmlText.toPlainText(inner)
                    if (plain.isNotBlank()) spans += Span(plain, code = true)
                }
                t.equals("<br>", true) || t.equals("<br/>", true) || t.equals("<br />", true) ->
                    spans += Span("\n")
                !t.startsWith("<") -> {
                    val plain = HtmlText.toPlainText(t)
                    if (plain.isNotBlank()) spans += Span(plain)
                }
                // 其余未知标签丢弃
            }
        }
        return spans
    }

    /** 代码块解码：实体还原、去首尾空行、统一行尾。 */
    private fun decodeCode(raw: String): String {
        var s = raw
            .replace(Regex("""(?is)<code[^>]*>"""), "")
            .replace(Regex("""(?i)</code>"""), "")
            .trim()
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        return s.replace("\r\n", "\n")
    }

    private fun extractLang(tagLine: String): String? {
        val m = Regex("""(?i)class=["'][^"']*(?:language-|lang-)([a-zA-Z0-9_+-]+)["']""").find(tagLine)
            ?: Regex("""(?i)lang=["']([a-zA-Z0-9_+-]+)["']""").find(tagLine)
        return m?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
