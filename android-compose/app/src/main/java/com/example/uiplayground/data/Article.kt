package com.example.uiplayground.data

/**
 * 列表项模型（分页来源）。
 */
data class Article(
    val id: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    /** 占位色种子，用于共享元素转场期间、图片尚未加载时的渐变占位 */
    val seed: Int,
)

/**
 * 详情模型（路由预取 + 请求合并的目标数据）。
 */
data class ArticleDetail(
    val article: Article,
    val body: String,
    val readTimeMin: Int,
)
