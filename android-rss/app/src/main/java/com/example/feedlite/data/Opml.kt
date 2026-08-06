package com.example.feedlite.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * OPML 导入导出（v1.10）。
 * - 导出：内置源 + 自定义源 → OPML XML 文本
 * - 导入：解析 OPML 的 <outline type="rss" xmlUrl="...">，返回需要添加的源
 */
object Opml {

    fun export(sources: List<FeedSource>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n  <head><title>FeedLite Subscriptions</title></head>\n  <body>\n")
        sources.forEach { s ->
            sb.append("    <outline text=\"")
                .append(esc(s.title))
                .append("\" title=\"")
                .append(esc(s.title))
                .append("\" type=\"rss\" xmlUrl=\"")
                .append(esc(s.url))
                .append("\"/>\n")
        }
        sb.append("  </body>\n</opml>\n")
        return sb.toString()
    }

    /** 解析 OPML，返回 (标题, URL) 列表；URL 已去重。 */
    fun parse(content: String): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(content.reader())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        ?: parser.getAttributeValue(null, "xmlurl")
                    if (!xmlUrl.isNullOrBlank() && xmlUrl.startsWith("http")) {
                        val title = parser.getAttributeValue(null, "text")
                            ?: parser.getAttributeValue(null, "title")
                            ?: xmlUrl
                        if (result.none { it.second == xmlUrl }) {
                            result.add(title to xmlUrl)
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // 解析失败返回已收集部分
        }
        return result
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
