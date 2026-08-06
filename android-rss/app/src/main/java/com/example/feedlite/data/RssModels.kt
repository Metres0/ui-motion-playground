package com.example.feedlite.data

/**
 * RSS 数据模型（同时兼容 RSS 2.0 与 Atom 的字段映射）。
 */
data class RssFeed(
    val source: FeedSource,
    val title: String,
    val link: String,
    val description: String = "",
    val items: List<RssItem> = emptyList(),
)

data class RssItem(
    val title: String,
    val link: String,
    /** 原始 HTML 摘要（RSS description / Atom summary） */
    val descriptionHtml: String = "",
    /** 发布日期原文，用于展示 */
    val pubDate: String = "",
    val author: String = "",
    /** 封面图 URL：优先 enclosure/media:content，其次正文首图 */
    val imageUrl: String? = null,
    /** 稳定 key，用于 Compose 共享元素转场 */
    val key: String,
)

/**
 * 订阅源。
 * @param url      feed 地址
 * @param defaultEnabled 内置源默认是否启用
 * @param seed     占位色种子
 */
data class FeedSource(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val defaultEnabled: Boolean = true,
    val seed: Int,
) {
    /** 列表页 favicon 占位首字母 */
    val initial: String get() = title.take(1)
}
