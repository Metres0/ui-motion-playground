package com.example.feedlite

import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * RssParser JVM 单测：用 kxml2 的真实解析器验证提取逻辑。
 */
class RssParserTest {

    private val source = FeedSource(
        id = "test",
        title = "测试源",
        description = "desc",
        url = "https://example.com/rss",
        seed = 1,
    )

    private fun parse(xml: String, charset: String = "UTF-8") = RssParser.parseWith(
        KXmlParser(),
        ByteArrayInputStream(xml.toByteArray(Charset.forName(charset))),
        source,
    )

    private fun rss(items: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
            <title>测试源</title><link>https://example.com</link><description>d</description>
            $items
        </channel></rss>"""

    @Test
    fun `解析基本 RSS 字段`() {
        val feed = parse(
            rss(
                """<item><title>标题一</title><link>https://example.com/1</link>
                   <description>&lt;p&gt;摘要&lt;/p&gt;</description>
                   <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
                   <author>作者甲</author><guid>guid-1</guid></item>"""
            )
        )
        assertEquals(1, feed.items.size)
        val it = feed.items[0]
        assertEquals("标题一", it.title)
        assertEquals("https://example.com/1", it.link)
        assertEquals("<p>摘要</p>", it.descriptionHtml)
        assertEquals("guid-1", it.key.removePrefix("${source.id}_guid_"))
        assertEquals("作者甲", it.author)
    }

    @Test
    fun `key 优先用 guid 且不随顺序变化`() {
        val feed1 = parse(rss(
            """<item><title>A</title><guid>g1</guid></item>
               <item><title>B</title><guid>g2</guid></item>"""
        ))
        val feed2 = parse(rss(
            """<item><title>B</title><guid>g2</guid></item>
               <item><title>A</title><guid>g1</guid></item>"""
        ))
        // 顺序颠倒后同一文章 key 不变（v1.32 修复：不再是位置索引）
        assertEquals(feed1.items[1].key, feed2.items[0].key)
        assertEquals(feed1.items[0].key, feed2.items[1].key)
        assertTrue(feed1.items[0].key != feed1.items[1].key)
    }

    @Test
    fun `无 guid 时 key 用 link`() {
        val feed = parse(rss(
            """<item><title>A</title><link>https://example.com/a</link></item>"""
        ))
        assertEquals("${source.id}_https://example.com/a", feed.items[0].key)
    }

    @Test
    fun `封面图优先级 enclosure 优先`() {
        val feed = parse(rss(
            """<item><title>A</title>
               <enclosure type="image/jpeg" url="https://cdn.example.com/pic.jpg" />
               <description>&lt;img src="https://example.com/inline.jpg"/&gt;</description></item>"""
        ))
        assertEquals("https://cdn.example.com/pic.jpg", feed.items[0].imageUrl)
    }

    @Test
    fun `协议相对图补全为 https`() {
        val feed = parse(rss(
            """<item><title>A</title>
               <description>&lt;img src="//cdn.example.com/x.webp"/&gt;</description></item>"""
        ))
        assertEquals("https://cdn.example.com/x.webp", feed.items[0].imageUrl)
    }

    @Test
    fun `GBK 编码中文源不乱码`() {
        val xml = """<?xml version="1.0" encoding="GBK"?>
        <rss version="2.0"><channel>
            <title>中文标题</title><link>https://example.com</link>
            <item><title>中文文章</title><guid>g1</guid></item>
        </channel></rss>"""
        val feed = parse(xml, "GBK")
        assertEquals("中文标题", feed.title)
        assertEquals("中文文章", feed.items[0].title)
    }

    @Test
    fun `item 数量有上限 300 不无限膨胀`() {
        val sb = StringBuilder()
        repeat(400) { i ->
            sb.append("""<item><title>t$i</title><guid>g$i</guid></item>""")
        }
        val feed = parse(rss(sb.toString()))
        assertTrue(feed.items.size <= 300)
    }
}
