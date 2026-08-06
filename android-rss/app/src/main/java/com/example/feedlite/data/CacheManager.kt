package com.example.feedlite.data

import android.content.Context
import java.io.File

/**
 * 缓存管理（v1.25）：统计 / 清理离线缓存（全文 + 译文）。
 *
 * 注意：[clear] 删除目录后必须重建，否则 [FullTextCache]/[Translator]
 * 依赖目录存在才能继续落盘；[sizeBytes] 可能遍历大量文件，调用方应在
 * Dispatchers.IO 下执行（不要在 composition 主线程调用）。
 */
class CacheManager(context: Context) {

    private val dirs = listOf(
        File(context.filesDir, "fulltext"),
        File(context.filesDir, "translations"),
    )

    /** 缓存总字节数（IO 密集，勿在主线程调用）。 */
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

    /** 清理全部缓存目录，并立即重建空目录（保证后续缓存写入不失效）。 */
    fun clear() {
        dirs.forEach {
            it.deleteRecursively()
            it.mkdirs()
        }
    }
}
