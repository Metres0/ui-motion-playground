package com.example.feedlite.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据仓库：抓取 + 解析 + 内存缓存。
 *
 * - `HttpURLConnection`：平台内置，无额外网络库依赖；带超时与 UA；
 * - 内存缓存：同源 5 分钟内不重复抓取（stale-while-revalidate 简化版），
 *   下拉刷新通过 [refresh] 强制更新；
 * - 抓取与解析都在 IO 线程。
 */
class RssRepository(private val context: Context) {

    private val cache = ConcurrentHashMap<String, CachedFeed>()

    /** 抓取并解析一个订阅源。force=true 强制刷新（绕过缓存）。 */
    suspend fun fetchFeed(source: FeedSource, force: Boolean = false): RssFeed = withContext(Dispatchers.IO) {
        if (!force) {
            cache[source.id]?.let { cached ->
                if (System.currentTimeMillis() - cached.at < TTL_MS) {
                    return@withContext cached.feed
                }
            }
        }
        val bytes = fetchBytes(source.url)
        val feed = bytes.inputStream().buffered().use { RssParser.parse(it, source) }
        cache[source.id] = CachedFeed(feed, System.currentTimeMillis())
        feed
    }

    fun refresh(source: FeedSource) {
        cache.remove(source.id)
    }

    private fun fetchBytes(urlString: String): ByteArray {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "FeedLite/1.0 (Android; RSS Reader)")
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
