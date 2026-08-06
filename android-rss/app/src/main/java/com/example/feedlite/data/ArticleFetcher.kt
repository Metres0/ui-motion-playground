package com.example.feedlite.data

import com.example.feedlite.data.HttpUtil.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 全文抓取器（v1.8；v1.33：统一 OkHttp）：抓取文章 URL 的 HTML，readability-lite 提取正文，并离线缓存。
 *
 * 少数派等源的 feed 只给短摘要，正文要抓原始网页：
 * 1. 先读 [FullTextCache] 离线缓存（命中直接返回，离线可看）；
 * 2. 抓取 article URL（带 Referer + 完整 UA）；
 * 3. [extractMainContent] 按候选容器定位正文，div 配对提取并清理噪音；
 * 4. 结果写入缓存。
 */
class ArticleFetcher(
    private val cache: FullTextCache? = null,
    private val client: OkHttpClient = OkHttpClient(),
) {

    /** 抓取并提取正文 HTML（带缓存）；失败抛 [RssFetchException]。 */
    suspend fun fetchArticle(link: String): String = withContext(Dispatchers.IO) {
        // 1. 离线缓存优先
        cache?.get(link)?.let { return@withContext it }

        // 2. 抓取 + 提取
        val html = fetch(link)
        val main = extractMainContent(html) ?: throw RssFetchException("未能在页面中找到正文区域")
        val normalized = normalizeImageUrls(main, link)

        // 3. 写入缓存（供离线阅读）
        cache?.put(link, normalized)
        normalized
    }

    /** 仅读缓存（离线场景，不发网络）。 */
    fun cachedOrNull(link: String): String? = cache?.get(link)

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
        val builder = Request.Builder()
            .url(urlString)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 FeedLite/1.33")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        val host = runCatching { java.net.URI(urlString).host }.getOrNull()
        if (!host.isNullOrBlank()) builder.header("Referer", "https://$host/")
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw RssFetchException("HTTP ${resp.code}")
            return resp.body?.byteStream()?.use {
                it.readBounded(HttpUtil.MAX_ARTICLE_BYTES).toString(Charsets.UTF_8)
            } ?: ""
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
        // ★ v1.7：按关键词配对移除评论/相关推荐/表情/分享容器（少数派等站点常见噪音）
        return removeNoiseContainers(s)
    }

    /**
     * 配对移除噪音容器：`<div/section/aside class|id 含关键词>` 整块删除。
     * 关键词覆盖：comment(评论)、extend/related/recommend/suggest(相关推荐)、emoji(表情)、share(分享)。
     */
    private fun removeNoiseContainers(html: String): String {
        var s = html
        val noiseRe = Regex(
            """(?is)<(div|section|aside)\b(?:[^'">]|"[^"]*"|'[^']*')*(?:class|id)=["'][^"']*(?:comment|extend|recommend|related|suggest|emoji|share)[^"']*["'](?:[^'">]|"[^"]*"|'[^']*')*>"""
        )
        var guard = 0
        while (guard < 20) {
            val m = noiseRe.find(s) ?: break
            val tag = m.groupValues[1]
            val end = removeBalancedContainer(s, m.range.last + 1, tag)
            if (end == null) break
            s = s.removeRange(m.range.first, end)
            guard++
        }
        return s
    }

    /** 从 start 开始配对容器，返回结束标签后的位置；未闭合返回 null。 */
    private fun removeBalancedContainer(html: String, start: Int, tag: String): Int? {
        val openRe = Regex("""(?is)<$tag\b(?:[^'">]|"[^"]*"|'[^']*')*>""")
        val closeRe = Regex("""(?is)</$tag\s*>""")
        var depth = 0
        var i = start
        while (i < html.length) {
            val o = openRe.find(html, i)
            val c = closeRe.find(html, i)
            val op = o?.range?.first ?: Int.MAX_VALUE
            val cp = c?.range?.first ?: Int.MAX_VALUE
            if (op == Int.MAX_VALUE && cp == Int.MAX_VALUE) return null
            if (op <= cp) { depth++; i = o!!.range.last + 1 }
            else {
                depth--
                if (depth < 0) return c!!.range.last + 1
                i = c!!.range.last + 1
            }
        }
        return null
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
