package com.example.feedlite.data

/**
 * 订阅源分类（常量）。顺序即首页/侧边栏分组展示顺序。
 */
object FeedCategory {
    const val TECH = "技术"
    const val AI = "AI"
    const val GO = "Go"
    const val BUSINESS = "商业"
    const val WORLD = "国际"
    const val CUSTOM = "自定义"

    /** 分类展示顺序 */
    val ORDER = listOf(TECH, AI, GO, BUSINESS, WORLD, CUSTOM)
}

/**
 * RSS 数据模型（同时兼容 RSS 2.0 与 Atom）。
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
    /** 原始 HTML 摘要（RSS description / Atom summary / content:encoded） */
    val descriptionHtml: String = "",
    val pubDate: String = "",
    val author: String = "",
    /** 封面图 URL（enclosure/media:thumbnail/正文首图，URL 已规范化） */
    val imageUrl: String? = null,
    /** feed 域名，用于图片请求防盗链 Referer */
    val feedHost: String? = null,
    /** 稳定 key，用于共享元素转场 */
    val key: String,
)

/**
 * 订阅源。
 * @param category 分类（见 [FeedCategory]）
 */
data class FeedSource(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val category: String = FeedCategory.TECH,
    val defaultEnabled: Boolean = true,
    val seed: Int,
) {
    val initial: String get() = title.take(1)
}
