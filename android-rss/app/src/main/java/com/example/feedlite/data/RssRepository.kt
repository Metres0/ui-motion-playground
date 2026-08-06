package com.example.feedlite.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据仓库（v1.4：本地缓存优先 + 增量更新）。
 *
 * 核心变化：
 * - 文章持久化到 [ArticleStore]，**进入应用直接读缓存秒开**；
 * - 网络抓取只在「超过更新间隔」或「手动刷新」时触发；
 * - 增量合并：新文章插入，已存在的跳过，不重复请求内容。
 */
class RssRepository(context: Context) {

    private val store = ArticleStore(context)
    private val cache = ConcurrentHashMap<String, CachedFeed>()

    /** 读取某源本地缓存（无缓存时返回空列表）。 */
    fun cachedItems(sourceId: String): List<RssItem> = store.load(sourceId)

    /** 某源是否有本地缓存。 */
    fun hasCache(sourceId: String): Boolean = store.hasCache(sourceId)

    /** 某源上次更新时间。 */
    fun lastUpdated(sourceId: String): Long = store.lastUpdated(sourceId)

    /**
     * 抓取并增量合并一个源。返回「新增文章数」。
     * 网络失败时抛异常（由调用方决定是否降级为纯缓存展示）。
     */
    suspend fun updateSource(source: FeedSource): Int = withContext(Dispatchers.IO) {
        val feed = fetchFeed(source)
        store.merge(source.id, feed.items)
    }

    /**
     * 增量更新一批源；单个源失败不影响其他。
     * @return 每个源新增文章数映射
     */
    suspend fun updateSources(sources: List<FeedSource>): Map<String, Int> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Int>()
        for (src in sources) {
            result[src.id] = try {
                store.merge(src.id, fetchFeed(src).items)
            } catch (e: Exception) {
                0
            }
        }
        result
    }

    /** 删除某源缓存（取消订阅时调用）。 */
    fun clearCache(sourceId: String) {
        store.clear(sourceId)
        cache.remove(sourceId)
    }

    private suspend fun fetchFeed(source: FeedSource): RssFeed {
        val cached = cache[source.id]
        if (cached != null && System.currentTimeMillis() - cached.at < TTL_MS) {
            return cached.feed
        }
        val bytes = fetchBytes(source.url)
        val feed = bytes.inputStream().buffered().use { RssParser.parse(it, source) }
        cache[source.id] = CachedFeed(feed, System.currentTimeMillis())
        return feed
    }

    private fun fetchBytes(urlString: String): ByteArray {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "FeedLite/1.4 (Android; RSS Reader)")
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RssFetchException("HTTP $code for $urlString")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private data class CachedFeed(val feed: RssFeed, val at: Long)

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L
    }
}

class RssFetchException(message: String) : Exception(message)
