package com.example.feedlite.ui

import com.example.feedlite.data.RssItem
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内文章缓存：列表页点击时写入，详情页按 key 读取。
 * 避免把对象塞进 Navigation 参数（保持路由参数为纯字符串）。
 */
object ArticleCache {
    private val map = ConcurrentHashMap<String, RssItem>()

    fun put(key: String, item: RssItem) { map[key] = item }
    fun get(key: String): RssItem? = map[key]
}
