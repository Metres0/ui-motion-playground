package com.example.feedlite.data

/**
 * 全局图片请求上下文。
 *
 * 部分站点 CDN（如 sspai 的 cdnfile.sspai.com）**无 Referer 返回 403**，
 * 且严格场景下 Referer 应为文章页域名而非图片域。
 * 进入详情页时记录文章域名，Coil 拦截器用它作为图片请求 Referer；
 * 离开详情页时清空，恢复「图片域」兜底，避免影响列表缩略图。
 */
object ImageContext {
    @Volatile
    var articleRefererHost: String? = null
}
