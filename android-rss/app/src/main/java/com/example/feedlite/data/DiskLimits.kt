package com.example.feedlite.data

/** 磁盘缓存上限（v1.33）：按「总字节数 + 文件数」双重约束做 LRU 淘汰。 */
object DiskLimits {
    /** 全文离线缓存：默认 20MB / 400 个文件。 */
    const val MAX_FULLTEXT_BYTES = 20L * 1024 * 1024
    const val MAX_FULLTEXT_FILES = 400

    /** 译文缓存：默认 20MB / 500 个文件。 */
    const val MAX_TRANSLATION_BYTES = 20L * 1024 * 1024
    const val MAX_TRANSLATION_FILES = 500
}
