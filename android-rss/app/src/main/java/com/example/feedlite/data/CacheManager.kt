package com.example.feedlite.data

import android.content.Context
import java.io.File

/**
 * 缓存管理（v1.25）：统计 / 清理离线缓存（全文 + 译文）。
 */
class CacheManager(context: Context) {

    private val dirs = listOf(
        File(context.filesDir, "fulltext"),
        File(context.filesDir, "translations"),
    )

    /** 缓存总字节数。 */
    fun sizeBytes(): Long = dirs.sumOf { dir ->
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** 人类可读大小。 */
    fun sizeText(): String {
        val b = sizeBytes()
        return when {
            b < 1024 -> "$b B"
            b < 1024 * 1024 -> "${b / 1024} KB"
            else -> String.format("%.1f MB", b / (1024.0 * 1024.0))
        }
    }

    /** 清理全部缓存目录。 */
    fun clear() {
        dirs.forEach { it.deleteRecursively() }
    }
}
