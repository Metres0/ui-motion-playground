package com.example.feedlite.data

/**
 * 内置 RSS 源目录（v1.2：加入分类 + AI/Go 源）。
 *
 * 可达性评估（2026-08 实测）：
 * - 默认启用的源以「国内可达 + 更新活跃」为主；
 * - Hugging Face / OpenAI / Google AI 实测 feed 有效，但国内网络可能不可达，默认关闭；
 * - 机器之心 jiqizhixin.com/rss 已返回 HTML 页面（RSS 路由废弃），未收录。
 */
object FeedCatalog {

    val builtin: List<FeedSource> = listOf(
        // ── 技术 ──────────────────────────────
        FeedSource(
            id = "ruanyifeng",
            title = "阮一峰的网络日志",
            description = "科技爱好者周刊、软件与创业",
            url = "https://www.ruanyifeng.com/blog/atom.xml",
            category = FeedCategory.TECH,
            seed = 1,
        ),
        FeedSource(
            id = "sspai",
            title = "少数派 sspai",
            description = "高效工作、品质生活、数字生活方式媒体",
            url = "https://sspai.com/feed",
            category = FeedCategory.TECH,
            seed = 2,
        ),
        FeedSource(
            id = "v2ex",
            title = "V2EX",
            description = "创意工作者们的社区，Tech / Geek 讨论",
            url = "https://www.v2ex.com/index.xml",
            category = FeedCategory.TECH,
            seed = 3,
        ),
        FeedSource(
            id = "infoq",
            title = "InfoQ 中文",
            description = "软件开发与架构领域技术媒体",
            url = "https://www.infoq.cn/feed",
            category = FeedCategory.TECH,
            seed = 4,
        ),

        // ── AI ────────────────────────────────
        FeedSource(
            id = "qbitai",
            title = "量子位",
            description = "追踪人工智能新趋势，报道科技行业新突破",
            url = "https://www.qbitai.com/feed",
            category = FeedCategory.AI,
            seed = 11,
        ),
        FeedSource(
            id = "huggingface",
            title = "Hugging Face Blog",
            description = "ML 社区官方博客：模型、库、研究",
            url = "https://huggingface.co/blog/feed.xml",
            category = FeedCategory.AI,
            defaultEnabled = false,
            seed = 12,
        ),
        FeedSource(
            id = "openai",
            title = "OpenAI News",
            description = "OpenAI 官方新闻",
            url = "https://openai.com/news/rss.xml",
            category = FeedCategory.AI,
            defaultEnabled = false,
            seed = 13,
        ),
        FeedSource(
            id = "googleai",
            title = "Google AI Blog",
            description = "Google 的 AI 研究与应用博客",
            url = "https://blog.google/technology/ai/rss/",
            category = FeedCategory.AI,
            defaultEnabled = false,
            seed = 14,
        ),

        // ── Go ────────────────────────────────
        FeedSource(
            id = "goblog",
            title = "Go 官方博客",
            description = "The Go Programming Language Blog",
            url = "https://go.dev/blog/feed.atom",
            category = FeedCategory.GO,
            seed = 21,
        ),

        // ── 商业 ──────────────────────────────
        FeedSource(
            id = "36kr",
            title = "36氪",
            description = "创业投资与科技商业资讯",
            url = "https://36kr.com/feed",
            category = FeedCategory.BUSINESS,
            defaultEnabled = false,
            seed = 5,
        ),

        // ── 国际 ──────────────────────────────
        FeedSource(
            id = "verge",
            title = "The Verge",
            description = "Technology, science, art and culture",
            url = "https://www.theverge.com/rss/index.xml",
            category = FeedCategory.WORLD,
            defaultEnabled = false,
            seed = 6,
        ),
        FeedSource(
            id = "bbc",
            title = "BBC News",
            description = "Top stories from the BBC",
            url = "https://feeds.bbci.co.uk/news/rss.xml",
            category = FeedCategory.WORLD,
            defaultEnabled = false,
            seed = 7,
        ),
        FeedSource(
            id = "nasa",
            title = "NASA Breaking News",
            description = "A RSS news feed from NASA",
            url = "https://www.nasa.gov/rss/dyn/breaking_news.rss",
            category = FeedCategory.WORLD,
            defaultEnabled = false,
            seed = 8,
        ),
    )
}
