package com.example.feedlite

import com.example.feedlite.data.NotificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 通知文本生成纯函数单测（v1.34）。 */
class NotificationSummaryTest {

    private val titles = mapOf(
        "solidot" to "Solidot 奇客",
        "sspai" to "少数派",
        "v2ex" to "V2EX",
        "qbitai" to "量子位",
    )

    @Test
    fun `无新文章返回 null（不弹通知）`() {
        assertNull(NotificationSummary.from(emptyMap(), titles))
        assertNull(NotificationSummary.from(mapOf("solidot" to 0), titles))
    }

    @Test
    fun `单源返回源名加篇数`() {
        assertEquals("Solidot 奇客 5 篇", NotificationSummary.from(mapOf("solidot" to 5), titles))
    }

    @Test
    fun `多源按新增数排序取前 3`() {
        val added = mapOf(
            "solidot" to 2,
            "sspai" to 8,
            "v2ex" to 5,
            "qbitai" to 1,
        )
        // 前 3：sspai(8) > v2ex(5) > solidot(2)，且超过 3 个源 → 追加「等 N 个源」
        assertEquals("少数派 8 篇、V2EX 5 篇、Solidot 奇客 2 篇 等 4 个源", NotificationSummary.from(added, titles))
    }

    @Test
    fun `恰好 3 个源不追加等字句`() {
        val added = mapOf("solidot" to 1, "sspai" to 3, "v2ex" to 2)
        assertEquals("少数派 3 篇、V2EX 2 篇、Solidot 奇客 1 篇", NotificationSummary.from(added, titles))
    }

    @Test
    fun `未知源回退到 id`() {
        assertEquals("unknown_src 2 篇", NotificationSummary.from(mapOf("unknown_src" to 2), titles))
    }
}
