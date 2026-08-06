package com.example.uiplayground.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据仓库 —— 演示「请求合并 / Request Merging」。
 *
 * 核心思想（见研究报告 §1.2）：
 * 路由预取和页面自身会并发发出同一个 key 的请求，如果都打到网络层，
 * 预取反而增加了并发压力。这里用 `Mutex + Deferred` 做去重：
 * 同 id 的请求在途时，后续调用直接订阅同一个 Deferred，**只发一次网络请求**。
 */
class ArticleRepository(private val api: ArticleApi) {

    private val mutex = Mutex()

    /** 独立 IO 作用域承载在途请求，与调用方生命周期解耦。 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** id → 在途请求。请求结束后移除，允许下一次导航重新拉取。 */
    private val inFlightDetail = ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<ArticleDetail>>()

    /** 详情缓存：预取成功且未过期时，页面进入可以瞬间渲染（stale-while-revalidate）。 */
    private val detailCache = ConcurrentHashMap<Long, ArticleDetail>()

    /**
     * 获取详情（带请求合并）。
     * 同一 id 的并发调用只触发一次网络请求，其余等待同一个结果。
     */
    suspend fun getArticleDetail(id: Long): ArticleDetail {
        detailCache[id]?.let { return it }

        val deferred = mutex.withLock {
            inFlightDetail[id] ?: scope.async { api.getArticleDetail(id) }.also { inFlightDetail[id] = it }
        }
        return try {
            deferred.await().also { detailCache[id] = it }
        } finally {
            mutex.withLock { inFlightDetail.remove(id) }
        }
    }

    /** 分页列表（无合并需求，直接透传）。 */
    suspend fun getArticles(page: Int, pageSize: Int): List<Article> = api.getArticles(page, pageSize)

    /** 预取是否已命中缓存（供 UI 展示「预取命中」标记）。 */
    fun isDetailCached(id: Long): Boolean = detailCache.containsKey(id)
}
