package com.example.feedlite

import com.example.feedlite.data.CodeBlockExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 代码块提取/还原单测：翻译前后代码不被改写。 */
class CodeBlockExtractorTest {

    @Test
    fun `提取代码块并替换为占位符`() {
        val extracted = CodeBlockExtractor.extract("<p>正文</p><pre><code>val x = 1</code></pre>")
        assertTrue(extracted.codes.contains("val x = 1"))
        assertTrue(extracted.placeholderText.contains("@@C0@@"))
        assertTrue(!extracted.placeholderText.contains("val x"))
    }

    @Test
    fun `还原占位符为围栏代码`() {
        val extracted = CodeBlockExtractor.extract("<pre>fun main() {}</pre>")
        val restored = CodeBlockExtractor.restore("翻译后的文本\n@@C0@@", extracted.codes)
        assertEquals("翻译后的文本\n\n```\nfun main() {}\n```", restored)
    }

    @Test
    fun `被改写的占位符回退追加到末尾`() {
        val extracted = CodeBlockExtractor.extract("<pre>a = 1</pre><pre>b = 2</pre>")
        val restored = CodeBlockExtractor.restore("译文没保留占位符", extracted.codes)
        assertTrue(restored.contains("a = 1"))
        assertTrue(restored.contains("b = 2"))
        assertTrue(restored.contains("[代码块]"))
    }

    @Test
    fun `HTML 实体在代码中正确还原`() {
        val extracted = CodeBlockExtractor.extract("<pre>a &lt; b &amp;&amp; c</pre>")
        assertEquals("a < b && c", extracted.codes[0])
    }
}
