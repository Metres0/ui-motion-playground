package com.example.feedlite

import com.example.feedlite.data.HtmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HtmlText 纯函数单测：标签剥离 / 实体解码 / 噪音过滤 / 代理区防崩。 */
class HtmlTextTest {

    @Test
    fun `剥离标签并解码常见实体`() {
        assertEquals(
            "hello & world <ok>",
            HtmlText.toPlainText("<p>hello &amp; world &lt;ok&gt;</p>"),
        )
    }

    @Test
    fun `数字实体安全解码`() {
        // 中文字符 U+4F60（你）
        assertEquals("你", HtmlText.toPlainText("&#20320;"))
        assertEquals("好", HtmlText.toPlainText("&#x597D;"))
    }

    @Test
    fun `代理区实体不崩溃并返回空格`() {
        // U+D800 位于代理区，旧实现 Character.toChars 会抛 IllegalArgumentException
        // 该字符被安全替换为单个空格
        assertEquals("ab cd", HtmlText.toPlainText("ab&#55296;cd").replace("\n", " "))
    }

    @Test
    fun `超范围实体不崩溃`() {
        assertEquals("", HtmlText.toPlainText("&#1114112;")) // 0x110000，越界
    }

    @Test
    fun `段落换行保留`() {
        val out = HtmlText.toPlainText("<p>一</p><p>二</p>")
        assertEquals("一\n\n二", out)
    }

    @Test
    fun `纯链接噪音不算有内容`() {
        assertFalse(HtmlText.hasMeaningfulContent("点击查看原文"))
        assertFalse(HtmlText.hasMeaningfulContent("<p><a href='x'>阅读全文</a></p>"))
    }

    @Test
    fun `实质内容判定`() {
        assertTrue(HtmlText.hasMeaningfulContent("<p>这是一段足够长的实质性内容描述，超过了二十个字。</p>"))
    }

    @Test
    fun `摘要截断加省略号`() {
        val long = "字".repeat(100)
        assertEquals(91, HtmlText.excerpt(long).length)
        assertTrue(HtmlText.excerpt(long).endsWith("…"))
    }
}
