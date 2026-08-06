package com.example.feedlite.ui

import com.example.feedlite.data.RssItem
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内文章/译文缓存：
 * - 列表页点击时写入文章对象，详情页按 key 读取（避免把对象塞进导航参数）；
 * - 翻译结果按文章 key 缓存，重复进入不重复请求。
 */
object ArticleCache {
    private val map = ConcurrentHashMap<String, RssItem>()
    val translations = ConcurrentHashMap<String, String>()

    fun put(key: String, item: RssItem) { map[key] = item }
    fun get(key: String): RssItem? = map[key]
}
