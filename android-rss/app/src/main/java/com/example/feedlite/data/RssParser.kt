package com.example.feedlite.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/**
 * 极简 RSS / Atom 解析器（加固版）。
 *
 * 基于 Android 内置 XmlPullParser，兼容 RSS 2.0 与 Atom：
 * - 开启 namespace 处理后 `parser.name` 返回 **localName**（如 `content:encoded` → `encoded`、
 *   `dc:creator` → `creator`、`media:thumbnail` → `thumbnail`），按 localName 匹配即可；
 * - Atom `<content>` / 普通 `<description>` 内常为 CDATA 包裹的 HTML，XmlPullParser 会将其
 *   作为 TEXT 返回，逐段拼接即可；
 * - 图片 URL 支持协议相对（//host）与相对路径（/path）规范化补全；
 * - 解析异常时返回**已解析部分**（部分成功优于整体失败），避免个别坏 item 拖垮全列表。
 */
object RssParser {

    fun parse(stream: InputStream, source: FeedSource): RssFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, "UTF-8")

        val feedBase = source.url

        var feedTitle = source.title
        var feedLink = ""
        var feedDesc = ""
        val items = ArrayList<RssItem>()

        var inItem = false
        var current: MutableMap<String, String>? = null
        var currentTag = ""
        var itemCount = 0

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && itemCount < 300) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        currentTag = tag
                        when {
                            tag == "item" || tag == "entry" -> {
                                inItem = true
                                current = linkedMapOf()
                            }
                            inItem && current != null -> {
                                // Atom: <link href="..."> 属性形式（可能有多个 link，取 alternate）
                                if (tag == "link") {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (rel == null || rel == "alternate") {
                                        if (!href.isNullOrBlank() && current!!["link"].isNullOrEmpty()) {
                                            current!!["link"] = href.trim()
                                        }
                                    }
                                }
                                // 封面：enclosure / media:content / media:thumbnail / atom:logo
                                if (tag == "enclosure" || tag == "thumbnail" || tag == "content") {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank() && looksLikeImage(url)) {
                                        current!!["imageUrl"] = url
                                    }
                                }
                            }
                            tag == "title" && !inItem && feedTitle == source.title -> feedTitle = ""
                            tag == "link" && !inItem -> {
                                if (parser.getAttributeValue(null, "href") != null) {
                                    feedLink = parser.getAttributeValue(null, "href") ?: ""
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem && current != null) {
                            val text = parser.text?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> current!!["title"] = current!!["title"].orEmpty() + text
                                    "link" -> if (current!!["link"].isNullOrEmpty()) current!!["link"] = text.trim()
                                    // localName 映射：description / summary / content:encoded / content
                                    "description", "summary", "encoded", "content" ->
                                        current!!["description"] = current!!["description"].orEmpty() + text
                                    "pubDate", "published", "updated" ->
                                        if (current!!["pubDate"].isNullOrEmpty()) current!!["pubDate"] = text
                                    "author", "creator" ->
                                        if (current!!["author"].isNullOrEmpty()) current!!["author"] = text
                                }
                            }
                        } else if (!inItem) {
                            when (currentTag) {
                                "title" -> feedTitle += parser.text?.trim().orEmpty()
                                "description" -> feedDesc += parser.text?.trim().orEmpty()
                                "link" -> if (feedLink.isEmpty()) feedLink = parser.text?.trim().orEmpty()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "item", "entry" -> {
                                current?.let { m ->
                                    val rawImage = m["imageUrl"]
                                        ?: extractFirstImage(m["description"].orEmpty())
                                    items += RssItem(
                                        title = m["title"].orEmpty().ifBlank { "(无标题)" },
                                        link = m["link"].orEmpty().trim(),
                                        descriptionHtml = m["description"].orEmpty(),
                                        pubDate = m["pubDate"].orEmpty(),
                                        author = m["author"].orEmpty(),
                                        imageUrl = normalizeImageUrl(rawImage, feedBase),
                                        key = "${source.id}_${itemCount}_${m["link"].orEmpty().trim()}",
                                    )
                                    itemCount++
                                }
                                current = null
                                inItem = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: XmlPullParserException) {
            // 单个 feed 解析中途失败：保留已解析条目，不抛异常导致整页失败
            // （解析器在 END_DOCUMENT 前中断时，最后一条未闭合的 item 丢弃）
        } catch (e: Exception) {
            // 其他解析异常同样部分成功兜底
        }
        return RssFeed(
            source = source,
            title = feedTitle.ifBlank { source.title },
            link = feedLink,
            description = feedDesc,
            items = items,
        )
    }

    /** 提取 HTML 片段中的第一张图 URL。 */
    private fun extractFirstImage(html: String): String? {
        if (html.isEmpty()) return null
        val regex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)?.trim()
    }

    /** 判断 URL 是否像图片资源（避免把页面 URL 误当封面）。 */
    private fun looksLikeImage(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
            clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".avif") ||
            clean.contains("image")
    }

    /**
     * 图片 URL 规范化：
     * - `//host/...`（协议相对）→ 补 https:
     * - `/path/...`（根相对）  → 基于 feed 域名补全
     * - 其他非法/空 → null（界面显示占位色）
     */
    private fun normalizeImageUrl(raw: String?, feedUrl: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val host = feedUrl?.let { f ->
                    Regex("""^(https?://[^/]+)""").find(f)?.groupValues?.get(1)
                } ?: return null
                "$host$url"
            }
            else -> null
        }
    }
}
