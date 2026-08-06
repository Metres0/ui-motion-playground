package com.example.feedlite.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 全文离线缓存（v1.8）：抓取到的文章全文 HTML 落盘，离线时可立即展示。
 * - 内存 Map 快照 + files/fulltext/{md5}.html 文件
 * - [get] 命中返回 HTML；[put] 保存
 */
class FullTextCache(context: Context) {

    private val dir: File = File(context.filesDir, "fulltext").apply { mkdirs() }
    private val memory = HashMap<String, String>()

    fun get(link: String): String? {
        memory[link]?.let { return it }
        val f = fileOf(link)
        if (!f.exists()) return null
        return try {
            val html = f.readText(Charsets.UTF_8)
            memory[link] = html
            html
        } catch (e: Exception) {
            null
        }
    }

    fun put(link: String, html: String) {
        memory[link] = html
        try {
            fileOf(link).writeText(html, Charsets.UTF_8)
        } catch (e: Exception) {
            // 写入失败忽略（磁盘满等）
        }
    }

    private fun fileOf(link: String): File {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(link.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$md5.html")
    }
}
