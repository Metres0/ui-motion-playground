package com.example.feedlite.data

/**
 * 内置 RSS 源目录。
 *
 * 选源原则（2026-08 当前可达性评估）：
 * - 默认启用的 4 个源以「国内可达 + 更新活跃」为主（阮一峰 / 少数派 / V2EX / InfoQ）；
 * - 其余国际源默认关闭，用户按需启用。
 * 网络环境不同可达性会有差异，抓取失败会在 UI 显示重试，不影响其他源。
 */
object FeedCatalog {

    val builtin: List<FeedSource> = listOf(
        FeedSource(
            id = "ruanyifeng",
            title = "阮一峰的网络日志",
            description = "科技爱好者周刊、ES6 教程、软件与创业",
            url = "https://www.ruanyifeng.com/blog/atom.xml",
            seed = 1,
        ),
        FeedSource(
            id = "sspai",
            title = "少数派 sspai",
            description = "高效工作、品质生活、数字生活方式媒体",
            url = "https://sspai.com/feed",
            seed = 2,
        ),
        FeedSource(
            id = "v2ex",
            title = "V2EX",
            description = "创意工作者们的社区，Tech / Geek 讨论",
            url = "https://www.v2ex.com/index.xml",
            seed = 3,
        ),
        FeedSource(
            id = "infoq",
            title = "InfoQ 中文",
            description = "软件开发与架构领域技术媒体",
            url = "https://www.infoq.cn/feed",
            seed = 4,
        ),
        FeedSource(
            id = "36kr",
            title = "36氪",
            description = "创业投资与科技商业资讯",
            url = "https://36kr.com/feed",
            defaultEnabled = false,
            seed = 5,
        ),
        FeedSource(
            id = "verge",
            title = "The Verge",
            description = "Technology, science, art and culture",
            url = "https://www.theverge.com/rss/index.xml",
            defaultEnabled = false,
            seed = 6,
        ),
        FeedSource(
            id = "bbc",
            title = "BBC News",
            description = "Top stories from the BBC",
            url = "https://feeds.bbci.co.uk/news/rss.xml",
            defaultEnabled = false,
            seed = 7,
        ),
        FeedSource(
            id = "nasa",
            title = "NASA Breaking News",
            description = "A RSS news feed from NASA",
            url = "https://www.nasa.gov/rss/dyn/breaking_news.rss",
            defaultEnabled = false,
            seed = 8,
        ),
    )
}
