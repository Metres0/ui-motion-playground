package com.example.feedlite.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * 极简 RSS / Atom 解析器。
 *
 * 基于 Android 平台内置 XmlPullParser（零额外依赖），
 * 兼容两种主流格式：
 * - RSS 2.0：<channel><item><title><link><description><pubDate><enclosure>
 * - Atom：  <feed><entry><title><link href><summary><published><author>
 *
 * 用「按 tag 栈式读取」的方式，避免依赖具体命名空间，对不规范 feed 有较好容错。
 */
object RssParser {

    fun parse(stream: InputStream, source: FeedSource): RssFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, "UTF-8")

        var feedTitle = source.title
        var feedLink = ""
        var feedDesc = ""
        val items = ArrayList<RssItem>()

        // 当前正在累积的 item 字段
        var inItem = false
        var current: MutableMap<String, String>? = null
        var currentTag = ""
        var itemCount = 0

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
                            // Atom 的 <link href="..."> 属性形式
                            if (tag == "link" && current!!["link"].isNullOrEmpty()) {
                                current!!["link"] = parser.getAttributeValue(null, "href") ?: ""
                            }
                            // enclosure / media:content 取封面图
                            if (tag == "enclosure" || tag == "content" || tag == "thumbnail") {
                                val url = parser.getAttributeValue(null, "url")
                                if (!url.isNullOrEmpty()) current!!["imageUrl"] = url
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
                                "link" -> if (current!!["link"].isNullOrEmpty()) current!!["link"] = text
                                "description", "summary", "content:encoded" ->
                                    current!!["description"] = current!!["description"].orEmpty() + text
                                "pubDate", "published", "updated" ->
                                    if (current!!["pubDate"].isNullOrEmpty()) current!!["pubDate"] = text
                                "author", "creator", "dc:creator" ->
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
                                val image = m["imageUrl"]
                                    ?: extractFirstImage(m["description"].orEmpty())
                                items += RssItem(
                                    title = m["title"].orEmpty().ifBlank { "(无标题)" },
                                    link = m["link"].orEmpty(),
                                    descriptionHtml = m["description"].orEmpty(),
                                    pubDate = m["pubDate"].orEmpty(),
                                    author = m["author"].orEmpty(),
                                    imageUrl = image,
                                    key = "${source.id}_${itemCount}_${m["link"].orEmpty()}",
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
        return RssFeed(
            source = source,
            title = feedTitle.ifBlank { source.title },
            link = feedLink,
            description = feedDesc,
            items = items,
        )
    }

    /** 从 HTML 描述中提取第一张图片 URL（渐进式封面兜底）。 */
    private fun extractFirstImage(html: String): String? {
        if (html.isEmpty()) return null
        val regex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val m = regex.find(html)
        val url = m?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }
        return url
    }
}
