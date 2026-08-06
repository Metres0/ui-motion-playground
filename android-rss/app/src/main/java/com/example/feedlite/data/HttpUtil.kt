package com.example.feedlite.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 网络工具：统一响应大小上限（防恶意/异常源 OOM）。 */
object HttpUtil {
    const val MAX_FEED_BYTES = 10 * 1024 * 1024        // RSS/Atom feed
    const val MAX_ARTICLE_BYTES = 5 * 1024 * 1024      // 文章全文 HTML
    const val MAX_TRANSLATION_BYTES = 2 * 1024 * 1024  // 翻译响应

    /** 读入最多 max 字节，超限抛 [ResponseTooLargeException]。 */
    fun InputStream.readBounded(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw ResponseTooLargeException(max)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

class ResponseTooLargeException(maxBytes: Int) :
    Exception("响应超过大小上限 $maxBytes 字节")

/**
 * URL 策略。
 *
 * 翻译端点仅允许 https；http 只对本地/内网地址放行（防止用户误配
 * http:// 导致 API Key 明文出网）。RSS 订阅源仍允许 http（老站点多）。
 */
object UrlPolicy {
    fun isAllowedTranslationBaseUrl(baseUrl: String): Boolean {
        if (baseUrl.startsWith("https://")) return true
        if (!baseUrl.startsWith("http://")) return false
        val host = runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: return false
        return host == "localhost" ||
            host == "10.0.2.2" || // 模拟器宿主机
            host.startsWith("127.") ||
            host.startsWith("192.168.") ||
            host.startsWith("10.")
    }
}
