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
 *
 * 生命周期：由 [PlaygroundApplication] 的 AppContainer 单例持有并构造一次，
 * 内部 scope 与进程同寿 —— 旋转屏幕 / 配置变更时 Activity 重建但预取缓存不丢。
 */
class ArticleRepository(private val api: ArticleApi) {

    private val mutex = Mutex()

    /** 进程级 IO 作用域承载在途请求，与调用方生命周期解耦。 */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** id → 在途请求。请求结束后移除，允许下一次导航重新拉取。 */
    private val inFlightDetail = ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<ArticleDetail>>()

    /**
     * 详情缓存：有界 LRU（accessOrder=true，put/get 都刷新访问序），
     * 超过 [DETAIL_CACHE_MAX] 条时自动逐出最久未访问项。
     */
    private val detailCache = object : LinkedHashMap<Long, ArticleDetail>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ArticleDetail>): Boolean =
            size > DETAIL_CACHE_MAX
    }
    private val detailCacheLock = Any()

    /** 发起过预取的文章 id 集合（配合 detailCache 判定「预取命中」徽章）。 */
    private val prefetchAttempted = ConcurrentHashMap.newKeySet<Long>()

    private companion object {
        const val DETAIL_CACHE_MAX = 60
    }

    /**
     * 获取详情（带请求合并）。
     * 同一 id 的并发调用只触发一次网络请求，其余等待同一个结果。
     */
    suspend fun getArticleDetail(id: Long): ArticleDetail {
        cachedDetail(id)?.let { return it }

        val deferred = mutex.withLock {
            inFlightDetail[id] ?: scope.async { api.getArticleDetail(id) }.also { inFlightDetail[id] = it }
        }
        return try {
            deferred.await().also { cacheDetail(id, it) }
        } finally {
            mutex.withLock { inFlightDetail.remove(id) }
        }
    }

    /** 分页列表（无合并需求，直接透传）。 */
    suspend fun getArticles(start: Int, count: Int): List<Article> = api.getArticles(start, count)

    /** 记录一次预取尝试（路由层发起预取时调用）。 */
    fun markPrefetched(id: Long) {
        prefetchAttempted.add(id)
    }

    /**
     * 预取是否「真实命中」：发起过预取 **且** 详情已写入缓存。
     * 相比「耗时 < Xms」的启发式，这是基于缓存事实的判定，不会误报。
     */
    fun wasPrefetched(id: Long): Boolean =
        prefetchAttempted.contains(id) && cachedDetail(id) != null

    /** 详情是否已在缓存中（供 UI 展示「预取命中」标记）。 */
    fun isDetailCached(id: Long): Boolean = cachedDetail(id) != null

    private fun cacheDetail(id: Long, detail: ArticleDetail) {
        synchronized(detailCacheLock) { detailCache[id] = detail }
    }

    private fun cachedDetail(id: Long): ArticleDetail? = synchronized(detailCacheLock) { detailCache[id] }
}
