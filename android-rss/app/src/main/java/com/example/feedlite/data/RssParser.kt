package com.example.feedlite.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream

/**
 * 极简 RSS / Atom 解析器（v1.2 加固版）。
 *
 * - localName 匹配（content:encoded → encoded、dc:creator → creator、media:thumbnail → thumbnail）；
 * - 封面图来源优先级：enclosure(image) > media:thumbnail > media:content > 正文首图(srcset 优先)；
 * - data URI（36kr 等 base64 内联图）直接忽略 → 显示占位色；
 * - 图片 URL 协议相对（//host）/ 根相对（/path）自动补全；
 * - 解析异常部分成功兜底。
 */
object RssParser {

    fun parse(stream: InputStream, source: FeedSource): RssFeed =
        parseWith(Xml.newPullParser(), stream, source)

    /** 可注入解析器，便于 JVM 单测（生产用 [Xml.newPullParser]，测试用 kxml2）。 */
    fun parseWith(parser: XmlPullParser, stream: InputStream, source: FeedSource): RssFeed {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // encoding 传 null：让解析器按 XML 声明自动识别字符集（GBK/GB2312 等中文源不再乱码）
        parser.setInput(stream, null)

        val feedBase = source.url
        val feedHost = runCatching { java.net.URI(source.url).host }.getOrNull()

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
                                if (tag == "link") {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (rel == null || rel == "alternate") {
                                        if (!href.isNullOrBlank() && current!!["link"].isNullOrEmpty()) {
                                            current!!["link"] = href.trim()
                                        }
                                    }
                                }
                                // 封面图：enclosure(type=image) / media:thumbnail / media:content / atom:logo
                                if (tag == "enclosure") {
                                    val type = parser.getAttributeValue(null, "type") ?: ""
                                    if (type.startsWith("image")) {
                                        current!!["imageUrl"] = parser.getAttributeValue(null, "url").orEmpty()
                                    }
                                } else if (tag == "thumbnail") {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank()) current!!["imageUrl"] = url
                                } else if (tag == "content") {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank() && looksLikeImageUrl(url)) {
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
                                    "description", "summary", "encoded", "content" ->
                                        current!!["description"] = current!!["description"].orEmpty() + text
                                    "pubDate", "published", "updated" ->
                                        if (current!!["pubDate"].isNullOrEmpty()) current!!["pubDate"] = text
                                    "author", "creator" ->
                                        if (current!!["author"].isNullOrEmpty()) current!!["author"] = text
                                    "guid", "id" ->
                                        if (current!!["guid"].isNullOrEmpty()) current!!["guid"] = text
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
                                        feedHost = feedHost,
                                        key = stableKey(source.id, m),
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
            // 部分成功兜底
        } catch (e: Exception) {
            // 其他解析异常同样部分成功
        }
        return RssFeed(
            source = source,
            title = feedTitle.ifBlank { source.title },
            link = feedLink,
            description = feedDesc,
            items = items,
        )
    }

    /**
     * 生成稳定的文章 key（v1.32 修复）：优先 feed 提供的 guid/id，其次 link，
     * 兜底用标题+时间的哈希。之前用「itemCount 位置索引」做 key，源重排/截断会
     * 让同一篇文章换 key，导致重复入库、已读/收藏状态失联。
     */
    private fun stableKey(sourceId: String, m: Map<String, String>): String {
        val guid = m["guid"].orEmpty().trim()
        if (guid.isNotEmpty()) return "${sourceId}_guid_$guid"
        val link = m["link"].orEmpty().trim()
        if (link.isNotEmpty()) return "${sourceId}_$link"
        return "${sourceId}_hash_${(m["title"].orEmpty() + "|" + m["pubDate"].orEmpty()).hashCode()}"
    }

    /**
     * 提取 HTML 片段中的第一张图 URL。
     * 优先 srcset 第一项；跳过 data: URI。
     */
    private fun extractFirstImage(html: String): String? {
        if (html.isEmpty()) return null
        val srcsetRegex = Regex("""<img[^>]+srcset=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val srcset = srcsetRegex.find(html)?.groupValues?.get(1)
        if (!srcset.isNullOrBlank()) {
            val first = srcset.split(',').firstOrNull()?.trim()?.substringBefore(' ')
            if (!first.isNullOrBlank() && !first.startsWith("data:")) return first
        }
        val regex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val src = regex.find(html)?.groupValues?.get(1)?.trim()
        return src?.takeIf { !it.startsWith("data:") }
    }

    private fun looksLikeImageUrl(url: String): Boolean {
        if (url.startsWith("data:")) return false
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
            clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".avif") ||
            clean.contains("image")
    }

    /**
     * 图片 URL 规范化：http(s)/协议相对/根相对；data URI 与非法值返回 null（显示占位色）。
     */
    private fun normalizeImageUrl(raw: String?, feedUrl: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty() || url.startsWith("data:")) return null
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
