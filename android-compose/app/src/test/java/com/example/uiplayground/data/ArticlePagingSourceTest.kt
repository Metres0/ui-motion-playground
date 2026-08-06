package com.example.uiplayground.data

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 offset 分页语义（研究报告 §3.1 的 Paging 修复）：
 *
 * 1. 首屏 Refresh(loadSize=40) 与后续 Append(loadSize=20) 的 item id 绝不重叠；
 *    —— 旧的「页码 + loadSize」实现会让 Append 取到 20~39，与首屏重叠，本测试会失败；
 * 2. 全部 60 条可达；
 * 3. 越过数据集末尾的 Append 返回空页且 nextKey = null。
 */
class ArticlePagingSourceTest {

    @Test
    fun offsetPagesDoNotOverlapAndReachTheEnd() = runTest {
        val repository = ArticleRepository(NoDelayFakeApi())
        val source = ArticlePagingSource(repository)

        // 首屏：initialLoadSize = 40
        val refresh = loadRefresh(source, loadSize = 40)
        val refreshIds = refresh.data.map { it.id }
        assertEquals(40, refresh.data.size)
        assertEquals(1L, refreshIds.first())
        assertEquals(40L, refreshIds.last())

        // 追加页：从上一页的 nextKey（数据偏移量）继续，不得与首屏重叠
        val append = loadAppend(source, key = requireNotNull(refresh.nextKey), loadSize = 20)
        val appendIds = append.data.map { it.id }
        assertTrue("追加页与首屏存在重叠 id（offset 修复失败）", refreshIds.intersect(appendIds.toSet()).isEmpty())
        assertEquals(20, append.data.size)
        assertEquals(41L, appendIds.first())
        assertEquals(60L, appendIds.last())

        // 全量 60 条可达
        val all = refreshIds + appendIds
        assertEquals(60, all.size)
        assertEquals(60L, all.maxOrNull())

        // 越过末尾的追加：空页 + nextKey = null
        val tail = loadAppend(source, key = requireNotNull(append.nextKey), loadSize = 20)
        assertTrue(tail.data.isEmpty())
        assertEquals(null, tail.nextKey)
    }

    private suspend fun loadRefresh(source: ArticlePagingSource, loadSize: Int): PagingSource.LoadResult.Page<Int, Article> =
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Article>

    private suspend fun loadAppend(source: ArticlePagingSource, key: Int, loadSize: Int): PagingSource.LoadResult.Page<Int, Article> =
        source.load(
            PagingSource.LoadParams.Append<Int>(key = key, loadSize = loadSize, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Article>
}

/** 无网络延迟的假 API（分页测试专用，语义与 FakeArticleApi 一致）。 */
private class NoDelayFakeApi : ArticleApi {
    private val total = 60

    override suspend fun getArticles(start: Int, count: Int): List<Article> {
        if (start >= total) return emptyList()
        return (start until minOf(start + count, total)).map { index ->
            Article(
                id = index + 1L,
                title = "article #${index + 1}",
                subtitle = "sub",
                coverUrl = "https://example.com/${index + 1}.jpg",
                seed = index + 1,
            )
        }
    }

    override suspend fun getArticleDetail(id: Long): ArticleDetail = error("not used in this test")
}
