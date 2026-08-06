package com.example.uiplayground.data

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * 仓库层的请求合并 / 缓存语义（JVM 单测，无 Robolectric）。
 */
class ArticleRepositoryTest {

    @Test
    fun concurrentDetailRequestsCoalesceToOneNetworkCall() = runTest {
        val api = CountingApi()
        val repository = ArticleRepository(api)

        val results = listOf(
            async { repository.getArticleDetail(1L) },
            async { repository.getArticleDetail(1L) },
            async { repository.getArticleDetail(1L) },
        ).map { it.await() }

        assertEquals("同一 id 的并发详情请求只允许触发一次网络调用", 1, api.detailCalls[1L] ?: 0)
        assertEquals(3, results.size)
        assertEquals("body-1", results.first().body)
    }

    @Test
    fun failedAttemptIsCleanedUpSoRetryRefiresAndSucceeds() = runTest {
        val api = FlakyApi() // 每个 id 首次调用抛错，之后成功
        val repository = ArticleRepository(api)

        // 首次调用失败：在途项应被清理，且不写缓存
        val failure = try {
            repository.getArticleDetail(1L)
            null
        } catch (e: RuntimeException) {
            e
        }
        assertNotNull("首次请求应当抛错", failure)
        assertFalse("失败请求不应写入缓存", repository.isDetailCached(1L))

        // 重试：重新发起网络请求并成功（若在途项没被清理，会复用一个已失败的 Deferred）
        val detail = repository.getArticleDetail(1L)
        assertEquals("body-1", detail.body)
        assertTrue("成功后应写入缓存", repository.isDetailCached(1L))
        assertEquals("重试必须重新发起网络请求", 2, api.detailCalls[1L] ?: 0)
    }

    @Test
    fun successfulFetchIsReportedAsCachedAndReadsDoNotReCallNetwork() = runTest {
        val api = CountingApi()
        val repository = ArticleRepository(api)

        repository.getArticleDetail(2L)
        assertTrue(repository.isDetailCached(2L))

        // 缓存命中：再次读取不产生第二次网络调用
        assertEquals("body-2", repository.getArticleDetail(2L).body)
        assertEquals(1, api.detailCalls[2L] ?: 0)
    }
}

/** 计数假 API：记录每个 id 的详情网络调用次数。 */
private class CountingApi : ArticleApi {
    val detailCalls = ConcurrentHashMap<Long, Int>()

    override suspend fun getArticles(start: Int, count: Int): List<Article> = emptyList()

    override suspend fun getArticleDetail(id: Long): ArticleDetail {
        detailCalls.merge(id, 1, Int::plus)
        return detail(id)
    }
}

/** 首个请求对每个 id 抛错，之后成功；同时计数。 */
private class FlakyApi : ArticleApi {
    val detailCalls = ConcurrentHashMap<Long, Int>()
    private val failedOnce = ConcurrentHashMap.newKeySet<Long>()

    override suspend fun getArticles(start: Int, count: Int): List<Article> = emptyList()

    override suspend fun getArticleDetail(id: Long): ArticleDetail {
        detailCalls.merge(id, 1, Int::plus)
        if (failedOnce.add(id)) throw RuntimeException("network down for $id")
        return detail(id)
    }
}

private fun detail(id: Long) = ArticleDetail(
    article = Article(
        id = id,
        title = "title-$id",
        subtitle = "sub",
        coverUrl = "https://example.com/$id.jpg",
        seed = id.toInt(),
    ),
    body = "body-$id",
    readTimeMin = 1,
)
