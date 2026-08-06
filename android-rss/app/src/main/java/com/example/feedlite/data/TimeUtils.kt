package com.example.feedlite.data

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间显示工具：把 RFC822 / ISO8601 发布时间转为相对时间（如「3 小时前」）。
 * 解析失败返回原文截断。
 */
object TimeUtils {

    private val RFC = DateTimeFormatter.RFC_1123_DATE_TIME

    fun timeAgo(pubDate: String): String {
        if (pubDate.isBlank()) return ""
        val instant = parse(pubDate) ?: return pubDate.take(16)
        val now = Instant.now()
        val minutes = Duration.between(instant, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} 天前"
            else -> instant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
    }

    private fun parse(s: String): Instant? {
        // ISO8601
        try { return OffsetDateTime.parse(s.trim()).toInstant() } catch (_: Exception) {}
        // RFC1123（GMT 结尾）
        try { return Instant.from(RFC.parse(s.trim())) } catch (_: Exception) {}
        // 无时区常见格式 "2026-08-05 10:00:00" 视为本地时间
        try {
            val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            return java.time.LocalDateTime.parse(s.trim(), f)
                .atZone(ZoneId.systemDefault()).toInstant()
        } catch (_: Exception) {}
        return null
    }
}
