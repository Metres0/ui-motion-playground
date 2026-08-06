package com.example.feedlite.ui

import com.example.feedlite.data.RssItem
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 进程内文章/译文缓存（v1.32：LRU 上限 100 条，防止长会话内存/对象无限增长）：
 * - 列表页点击时写入文章对象，详情页按 key 读取（避免把对象塞进导航参数）；
 * - 翻译结果按文章 key 缓存，重复进入不重复请求。
 *
 * 线程安全：LinkedHashMap(accessOrder) + removeEldestEntry 实现 LRU，
 * 包一层 Collections.synchronizedMap 保证多线程访问安全。
 */
object ArticleCache {
    private const val MAX_ENTRIES = 100

    private val map: MutableMap<String, RssItem> = lru()
    val translations: MutableMap<String, String> = lru()

    fun put(key: String, item: RssItem) { map[key] = item }
    fun get(key: String): RssItem? = map[key]

    private fun <K, V> lru(): MutableMap<K, V> = Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > MAX_ENTRIES
        }
    )
}
