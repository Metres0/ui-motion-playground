package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 文章持久化 + 增量合并（v1.4）。
 *
 * 替代「每次进应用全量刷新」：
 * - [load]  读本地缓存（毫秒级，秒开）；
 * - [merge] 增量合并：按 key 去重，只保留新文章（新在前），已存在的不重复插入；
 * - [lastUpdated] 记录每个源上次成功更新时间，供定时策略判断。
 *
 * 存储：SharedPreferences + JSON（单源几十条文章，体积极小，够用且零依赖）。
 */
class ArticleStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("articles_v2", Context.MODE_PRIVATE)

    /** 读取某源缓存的文章（可能为空）。 */
    fun load(sourceId: String): List<RssItem> {
        val raw = prefs.getString(KEY_ITEMS(sourceId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { rssItemFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 该源上次成功抓取时间（ms），从未抓取返回 0。 */
    fun lastUpdated(sourceId: String): Long = prefs.getLong(KEY_TIME(sourceId), 0L)

    /** 是否有缓存。 */
    fun hasCache(sourceId: String): Boolean = prefs.contains(KEY_ITEMS(sourceId))

    /**
     * 增量合并：新文章（按 key 去重）插到最前，返回合并后的完整列表并保存。
     * @return 新增的文章数量
     *
     * 线程安全：同源并发合并（首页后台批量更新 + 列表页单源刷新）会产生
     * 「读到同一旧快照 → 各自 save → 后写覆盖先写」的丢失更新，这里按
     * sourceId 加锁串行化读-改-写。
     */
    fun merge(sourceId: String, newItems: List<RssItem>): Int = synchronized(lockFor(sourceId)) {
        val existing = load(sourceId)
        val existingKeys = existing.map { it.key }.toHashSet()
        val fresh = newItems.filter { it.key !in existingKeys }
        val merged = (fresh + existing).take(MAX_ITEMS)
        save(sourceId, merged)
        fresh.size
    }

    /** 手动刷新后的完整替换（同样串行化）。 */
    fun replace(sourceId: String, items: List<RssItem>) = synchronized(lockFor(sourceId)) {
        save(sourceId, items.take(MAX_ITEMS))
    }

    private fun lockFor(sourceId: String): Any = locks.getOrPut(sourceId) { Any() }

    /** 直接保存（用于手动刷新后的完整替换）。 */
    fun save(sourceId: String, items: List<RssItem>) {
        prefs.edit()
            .putString(KEY_ITEMS(sourceId), JSONArray(items.map { it.toJson() }).toString())
            .putLong(KEY_TIME(sourceId), System.currentTimeMillis())
            .apply()
    }

    /** 删除某源缓存（删除订阅时调用）。 */
    fun clear(sourceId: String) {
        prefs.edit().remove(KEY_ITEMS(sourceId)).remove(KEY_TIME(sourceId)).apply()
    }

    private fun KEY_ITEMS(id: String) = "items_$id"
    private fun KEY_TIME(id: String) = "time_$id"

    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    companion object {
        private const val MAX_ITEMS = 200
    }
}

// ── RssItem JSON 序列化 ──────────────────────────────────────
private fun RssItem.toJson(): JSONObject = JSONObject().apply {
    put("title", title)
    put("link", link)
    put("descriptionHtml", descriptionHtml)
    put("pubDate", pubDate)
    put("author", author)
    put("imageUrl", imageUrl)
    put("feedHost", feedHost)
    put("key", key)
}

private fun rssItemFromJson(o: JSONObject): RssItem = RssItem(
    title = o.optString("title"),
    link = o.optString("link"),
    descriptionHtml = o.optString("descriptionHtml"),
    pubDate = o.optString("pubDate"),
    author = o.optString("author"),
    imageUrl = if (o.isNull("imageUrl")) null else o.optString("imageUrl"),
    feedHost = if (o.isNull("feedHost")) null else o.optString("feedHost"),
    key = o.optString("key"),
)
