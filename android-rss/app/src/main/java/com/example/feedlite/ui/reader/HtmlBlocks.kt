package com.example.feedlite.ui.reader

import com.example.feedlite.data.HtmlText

/**
 * 轻量 HTML → 排版块 渲染器（v1.3 重写：正则级扫描，段落独立，正文图片真实渲染）。
 *
 * 支持块级：h1-h6 / 段落（每 <p> 独立） / 有序无序列表 / 引用 / 分隔线 / 代码块 / 图片。
 * 行内：加粗、斜体、行内代码、链接、换行。
 * 正文图片（含段落内嵌）拆分为独立 Image 块，由详情页用 ProgressiveImage 真实渲染。
 * 代码块保持原文（翻译时也不参与，见 CodeBlockExtractor）。
 */
object HtmlBlocks {

    sealed class Block {
        data class Paragraph(val spans: List<Span>) : Block()
        data class Heading(val level: Int, val spans: List<Span>) : Block()
        data class CodeBlock(val code: String, val language: String?) : Block()
        data class UnorderedList(val items: List<List<Span>>) : Block()
        data class OrderedList(val items: List<List<Span>>) : Block()
        data class Quote(val spans: List<Span>) : Block()
        data class Image(val url: String, val alt: String) : Block()
        data object Divider : Block()
    }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    /** 引号感知的标签匹配：跳过引号内的 `>`，杜绝 `data-v-...>` 属性残留被当文本。 */
    private val OPEN_BLOCK =
        Regex("""(?is)<(h[1-6]|pre|ul|ol|blockquote|img|hr|p|div|section|article|figure)\b(?:[^'">]|"[^"]*"|'[^']*')*>""")

    /** 引号感知的任意标签（用于 inline 丢弃未知标签）。 */
    private val ANY_TAG = Regex("""(?is)<(?:[a-zA-Z/!][^'">]*|"[^"]*"|'[^']*')*>""")

    /** 判断图片是否像 emoji / 图标（不应渲染成大图）。 */
    private fun looksLikeEmoji(url: String, alt: String): Boolean {
        if (url.contains("emoji", ignoreCase = true)) return true
        if (alt.length in 1..4 && alt.codePoints().allMatch { Character.isSurrogate(Character.toChars(it)[0]) || it >= 0x1F000 }) {
            return true
        }
        return false
    }

    /** 解析 HTML 为块序列。段落内嵌图片会拆出独立 Image 块。 */
    fun parse(htmlInput: String): List<Block> {
        // 先剥离 HTML 注释（Vue 站残留 <!---->）
        val html = htmlInput.replace(Regex("""(?is)<!--[\s\S]*?-->"""), "")
        val blocks = ArrayList<Block>()
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotBlank()) {
                val spans = inline(buf.toString())
                if (spans.isNotEmpty()) blocks += Block.Paragraph(spans)
                buf.setLength(0)
            }
        }

        var pos = 0
        while (pos < html.length) {
            val m = OPEN_BLOCK.find(html, pos)
            if (m == null) { buf.append(html, pos, html.length); break }
            buf.append(html, pos, m.range.first)
            val tag = m.groupValues[1].lowercase()
            val tagStart = m.range.last + 1

            when (tag) {
                "img" -> {
                    flush()
                    val url = Regex("""(?i)src=["']([^"']+)["']""").find(m.value)?.groupValues?.get(1)
                    val alt = Regex("""(?i)alt=["']([^"']*)["']""").find(m.value)?.groupValues?.get(1).orEmpty()
                    if (!url.isNullOrBlank() && !url.startsWith("data:") && !looksLikeEmoji(url, alt)) {
                        blocks += Block.Image(url, alt)
                    }
                    pos = tagStart
                }

                "hr" -> { flush(); blocks += Block.Divider; pos = tagStart }

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush()
                    val endIdx = html.indexOf("</$tag>", tagStart, ignoreCase = true)
                    val content = if (endIdx >= 0) html.substring(tagStart, endIdx) else ""
                    blocks += Block.Heading(tag[1].digitToInt(), inline(content))
                    pos = if (endIdx >= 0) endIdx + tag.length + 3 else html.length
                }

                "pre" -> {
                    flush()
                    val endIdx = html.indexOf("</pre>", tagStart, ignoreCase = true)
                    val raw = if (endIdx >= 0) html.substring(tagStart, endIdx) else ""
                    blocks += Block.CodeBlock(decodeCode(raw), extractLang(m.value))
                    pos = if (endIdx >= 0) endIdx + "</pre>".length else html.length
                }

                "ul", "ol" -> {
                    flush()
                    val ordered = tag == "ol"
                    val endIdx = html.indexOf("</$tag>", tagStart, ignoreCase = true)
                    val segEnd = if (endIdx >= 0) endIdx else html.length
                    val seg = html.substring(tagStart, segEnd)
                    val items = Regex("""(?is)<li\b[^>]*>([\s\S]*?)</li\s*>""")
                        .findAll(seg).map { inline(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
                    if (items.isNotEmpty()) {
                        blocks += if (ordered) Block.OrderedList(items) else Block.UnorderedList(items)
                    }
                    pos = if (endIdx >= 0) endIdx + tag.length + 3 else segEnd
                }

                "blockquote" -> {
                    flush()
                    val endIdx = html.indexOf("</blockquote>", tagStart, ignoreCase = true)
                    val content = if (endIdx >= 0) html.substring(tagStart, endIdx) else ""
                    blocks += Block.Quote(inline(content))
                    pos = if (endIdx >= 0) endIdx + "</blockquote>".length else html.length
                }

                // 容器标签：仅作段落分隔边界（不吞内容），让正文分段更合理
                "div", "section", "article", "figure" -> {
                    flush()
                    pos = tagStart
                }

                "p" -> {
                    // 段落：一次性消费到 </p>，段内图片拆出 Image 块
                    flush()
                    val endIdx = html.indexOf("</p>", tagStart, ignoreCase = true)
                    val content = if (endIdx >= 0) html.substring(tagStart, endIdx) else html.substring(tagStart)
                    appendMixed(buf, content, blocks)
                    pos = if (endIdx >= 0) endIdx + 4 else html.length
                }
            }
        }
        flush()
        return blocks
    }

    /**
     * 把一段 HTML 按 <img> 拆开：文本部分进 [buf]，图片直接输出为 Image 块。
     * 解决「<p><img /></p>」段落内图片消失的问题。
     */
    private fun appendMixed(buf: StringBuilder, html: String, out: MutableList<Block>) {
        var i = 0
        while (i < html.length) {
            val open = html.indexOf("<img", i, ignoreCase = true)
            if (open < 0) { buf.append(html, i, html.length); break }
            buf.append(html, i, open)
            // 引号感知定位标签结束，杜绝属性值含 > 时截断残留
            val tagMatch = Regex("""(?is)<img\b(?:[^'">]|"[^"]*"|'[^']*')*>""").find(html, open)
            if (tagMatch == null) { buf.append(html, open, html.length); break }
            val tag = tagMatch.value
            val url = Regex("""(?i)src=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
            val alt = Regex("""(?i)alt=["']([^"']*)["']""").find(tag)?.groupValues?.get(1).orEmpty()
            if (!url.isNullOrBlank() && !url.startsWith("data:") && !looksLikeEmoji(url, alt)) {
                buf.append(' ')
                out += Block.Image(url, alt)
            }
            i = tagMatch.range.last + 1
        }
    }

    /** 行内样式解析：strong/b、em/i、code、a、br、文本。img 已在块级处理，此处忽略。 */
    fun inline(html: String): List<Span> {
        // 引号感知的标签匹配：跳过引号内的 `>`，Vue 的 data-v-xxx 属性等未知标签整体丢弃
        val pattern = Regex(
            """(?is)(<a\b(?:[^'">]|"[^"]*"|'[^']*')*>.*?</a\s*>|<strong>.*?</strong>|<b>.*?</b>|<em>.*?</em>|<i>.*?</i>|<code>.*?</code>|<br\s*/?>|<img\b(?:[^'">]|"[^"]*"|'[^']*')*>|<!-{2}[\s\S]*?-{2}>|<(?:[a-zA-Z/!][^'">]*|"[^"]*"|'[^']*')*>|[^<]+)"""
        )
        val spans = ArrayList<Span>()

        for (m in pattern.findAll(html)) {
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
                t.startsWith("<img", true) -> { /* 块级已处理，忽略 */ }
                !t.startsWith("<") -> {
                    val plain = HtmlText.toPlainText(t)
                    if (plain.isNotBlank()) spans += Span(plain)
                }
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
