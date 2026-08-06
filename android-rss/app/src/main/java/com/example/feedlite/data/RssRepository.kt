package com.example.feedlite.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.example.feedlite.data.HttpUtil.readBounded
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据仓库（v1.4：本地缓存优先 + 增量更新；v1.32：强制刷新/上限/重试/失败追踪）。
 *
 * 核心变化：
 * - 文章持久化到 [ArticleStore]，**进入应用直接读缓存秒开**；
 * - 网络抓取只在「超过更新间隔」或「手动刷新（force）」时触发；
 * - 增量合并：新文章插入，已存在的跳过，不重复请求内容；
 * - 响应大小上限 10MB，防止恶意/异常源 OOM；
 * - 网络瞬时故障自动重试（1s / 2s 退避），HTTP 错误不重试；
 * - [updateSources] 单独返回失败源集合，首页「成功 X/Y」不再虚报。
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
     * @param force true 时绕过 5 分钟内存 TTL（手动刷新必须强制，否则看起来「点了没反应」）。
     * 网络失败时抛异常（由调用方决定是否降级为纯缓存展示）。
     */
    suspend fun updateSource(source: FeedSource, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val feed = fetchFeed(source, force)
        store.merge(source.id, feed.items)
    }

    /**
     * 增量更新一批源；单个源失败不影响其他。
     * @return 每个源的新增数 + 失败源 id 集合（用于真实统计「成功 X/Y」）
     */
    suspend fun updateSources(sources: List<FeedSource>, force: Boolean = false): UpdateResult =
        withContext(Dispatchers.IO) {
            val added = LinkedHashMap<String, Int>()
            val failures = linkedSetOf<String>()
            for (src in sources) {
                try {
                    added[src.id] = store.merge(src.id, fetchFeed(src, force).items)
                } catch (e: Exception) {
                    failures += src.id
                }
            }
            UpdateResult(added, failures)
        }

    /** 删除某源缓存（取消订阅时调用）。 */
    fun clearCache(sourceId: String) {
        store.clear(sourceId)
        cache.remove(sourceId)
    }

    private suspend fun fetchFeed(source: FeedSource, force: Boolean = false): RssFeed {
        if (!force) {
            val cached = cache[source.id]
            if (cached != null && System.currentTimeMillis() - cached.at < TTL_MS) {
                return cached.feed
            }
        }
        val bytes = fetchBytes(source.url)
        val feed = bytes.inputStream().buffered().use { RssParser.parse(it, source) }
        cache[source.id] = CachedFeed(feed, System.currentTimeMillis())
        return feed
    }

    /** 带重试退避 + 大小上限的抓取。HTTP 错误不重试，瞬时 IO 错误重试 2 次。 */
    private suspend fun fetchBytes(urlString: String): ByteArray {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return fetchBytesOnce(urlString)
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                delay(RETRY_BASE_MS * attempt) // 1s / 2s
            }
        }
    }

    private fun fetchBytesOnce(urlString: String): ByteArray {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "FeedLite/1.32 (Android; RSS Reader)")
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RssFetchException("HTTP $code for $urlString")
            }
            val declared = conn.contentLength
            if (declared > HttpUtil.MAX_FEED_BYTES) {
                throw RssFetchException("响应过大（$declared B）for $urlString")
            }
            return conn.inputStream.use { it.readBounded(HttpUtil.MAX_FEED_BYTES) }
        } finally {
            conn.disconnect()
        }
    }

    private data class CachedFeed(val feed: RssFeed, val at: Long)

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L
        private const val MAX_ATTEMPTS = 2 // 首次 + 2 次重试
        private const val RETRY_BASE_MS = 1_000L
    }
}

/** 批量更新的结果：新增数映射 + 失败源集合。 */
data class UpdateResult(val added: Map<String, Int>, val failures: Set<String>)

class RssFetchException(message: String) : Exception(message)
