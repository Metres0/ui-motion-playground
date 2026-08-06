package com.example.feedlite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 全文抓取器（v1.5）：抓取文章 URL 的 HTML，用 readability-lite 提取正文容器。
 *
 * 少数派等源的 feed 只给短摘要，正文要抓原始网页：
 * 1. 抓取 article URL（带 Referer + 完整 UA）；
 * 2. [extractMainContent] 按候选容器（id/class/标签）定位正文区域，
 *    用 <div> 配对计数提取内嵌 HTML，清理 script/style/nav/header/footer/aside；
 * 3. 失败抛异常，由调用方降级为「查看全文」跳浏览器。
 */
class ArticleFetcher {

    /** 抓取并提取正文 HTML；失败抛 [RssFetchException]。 */
    suspend fun fetchArticle(link: String): String = withContext(Dispatchers.IO) {
        val html = fetch(link)
        val main = extractMainContent(html) ?: throw RssFetchException("未能在页面中找到正文区域")
        normalizeImageUrls(main, link)
    }

    /**
     * 补全正文中的相对图片 URL：
     * - `//host/x.jpg` → `https://host/x.jpg`
     * - `/path/x.jpg`  → `https://{link 域名}/path/x.jpg`
     * - `../x.jpg`     → 基于 link 路径解析
     */
    private fun normalizeImageUrls(html: String, link: String): String {
        val base = runCatching { java.net.URI(link) }.getOrNull() ?: return html
        val scheme = base.scheme ?: "https"
        val host = base.host
        if (host.isNullOrBlank()) return html
        return html.replace(
            Regex("""(<img\b[^>]*\ssrc=["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
        ) { m ->
            val url = m.groupValues[2].trim()
            val resolved = when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "$scheme:$url"
                url.startsWith("/") -> "$scheme://$host$url"
                else -> {
                    val dir = base.path.substringBeforeLast('/')
                    "$scheme://$host$dir/$url"
                }
            }
            m.groupValues[1] + resolved + m.groupValues[3]
        }
    }

    private fun fetch(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 FeedLite/1.5")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            val host = runCatching { java.net.URI(urlString).host }.getOrNull()
            if (!host.isNullOrBlank()) conn.setRequestProperty("Referer", "https://$host/")
            val code = conn.responseCode
            if (code !in 200..299) throw RssFetchException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * readability-lite 正文提取。
     * 候选容器（按优先级）：
     * 1. id/class 关键词（article-body / article-content / post-content / entry-content / markdown-body / rich_media_content / topic_content …）
     * 2. <article> 标签
     * 3. <main> 或 [itemprop=articleBody]
     * 找到后用 <div> 配对计数截取容器内 HTML，清理噪音标签。
     */
    fun extractMainContent(html: String): String? {
        // 候选容器 id/class 定位
        val containerPatterns = listOf(
            Regex("""<div\b[^>]*class=["'][^"']*(?:article-body|article-content|article_content|post-content|post_content|entry-content|markdown-body|rich_media_content|topic_content|article_detail|articleDetail)[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div\b[^>]*id=["'][^"']*(?:article|content|post|main-content|js_content)[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<article\b[^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<main\b[^>]*>""", RegexOption.IGNORE_CASE),
        )

        for (pattern in containerPatterns) {
            val m = pattern.find(html) ?: continue
            val inner = extractBalancedDiv(html, m.range.last + 1)
            if (inner != null) {
                val cleaned = cleanNoise(inner)
                if (countParagraphText(cleaned) >= 200) {
                    return cleaned
                }
            }
        }
        return null
    }

    /** 从 start 位置用 <div> 配对计数截取容器内 HTML（兼容嵌套 div）。 */
    private fun extractBalancedDiv(html: String, start: Int): String? {
        var depth = 0
        var i = start
        val openRe = Regex("""<div\b""", RegexOption.IGNORE_CASE)
        val closeRe = Regex("""</div\s*>""", RegexOption.IGNORE_CASE)

        var segStart = -1
        while (i < html.length) {
            val nextOpen = openRe.find(html, i)
            val nextClose = closeRe.find(html, i)
            val nextPos = when {
                nextOpen != null && nextClose != null -> minOf(nextOpen.range.first, nextClose.range.first)
                nextOpen != null -> nextOpen.range.first
                nextClose != null -> nextClose.range.first
                else -> return null
            }
            val isOpen = nextOpen != null && nextOpen.range.first == nextPos
            if (isOpen) {
                depth++
                if (segStart < 0) segStart = nextOpen.range.last + 1
                i = nextOpen.range.last + 1
            } else {
                depth--
                if (depth == 0 && segStart >= 0) {
                    return html.substring(segStart, nextClose!!.range.first)
                }
                i = nextClose!!.range.last + 1
            }
        }
        return null
    }

    /** 清理 script/style/nav/header/footer/aside/iframe/表单。 */
    private fun cleanNoise(html: String): String {
        var s = html
        for (tag in listOf("script", "style", "nav", "header", "footer", "aside", "iframe", "form", "svg", "noscript")) {
            s = s.replace(
                Regex("""(?is)<$tag\b[^>]*>[\s\S]*?</$tag\s*>"""),
                " ",
            )
        }
        return s
    }

    /** 粗略估计正文文本量（提取的 <p> 内容长度和）。 */
    private fun countParagraphText(html: String): Int {
        var total = 0
        for (m in Regex("""(?is)<p\b[^>]*>([\s\S]*?)</p\s*>""").findAll(html)) {
            total += HtmlText.toPlainText(m.groupValues[1]).length
        }
        return total
    }
}
