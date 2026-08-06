package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 阅读状态持久化（v1.9）：已读 / 收藏 / 稍后再看 / 阅读进度。
 *
 * ⚠️ 关键修复：收藏与稍后再看的持久化必须把每个 RssItem 先 toJson() 成 JSONObject 再放入
 * JSONObject —— 直接 `JSONObject(Map<String,RssItem>)` 会把 RssItem 序列化成字符串（toString），
 * 读回时 getJSONObject 抛异常导致「收藏永远显示不出来」。同时换用新 prefs 文件
 * reading_state_v2 丢弃旧版本损坏数据。
 */
class ReadingStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reading_state_v2", Context.MODE_PRIVATE)

    /** 收藏/已读/稍后再看 变更时自增；列表页 collect 它来刷新。 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun bump() { _version.value += 1 }

    // ── 已读 ────────────────────────────────
    fun isRead(key: String): Boolean =
        prefs.getStringSet(KEY_READ, emptySet())?.contains(key) == true

    fun markRead(key: String) {
        val set = prefs.getStringSet(KEY_READ, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.add(key)) {
            prefs.edit().putStringSet(KEY_READ, set).apply()
            bump()
        }
    }

    // ── 收藏 ────────────────────────────────
    fun isStarred(key: String): Boolean = itemMap(KEY_STARRED).containsKey(key)

    fun toggleStar(item: RssItem): Boolean {
        val map = itemMap(KEY_STARRED).toMutableMap()
        val starred = if (map.containsKey(item.key)) {
            map.remove(item.key); false
        } else {
            map[item.key] = item; true
        }
        saveItemMap(KEY_STARRED, map)
        bump()
        return starred
    }

    fun starredItems(): List<RssItem> = itemMap(KEY_STARRED).values.toList()

    /** 导出收藏为 JSON 文本。 */
    fun exportJson(): String {
        val arr = JSONArray()
        itemMap(KEY_STARRED).values.forEach { arr.put(it.toJson()) }
        return arr.toString(2)
    }

    // ── 稍后再看 ────────────────────────────
    fun isReadLater(key: String): Boolean = itemMap(KEY_LATER).containsKey(key)

    fun toggleReadLater(item: RssItem): Boolean {
        val map = itemMap(KEY_LATER).toMutableMap()
        val later = if (map.containsKey(item.key)) {
            map.remove(item.key); false
        } else {
            map[item.key] = item; true
        }
        saveItemMap(KEY_LATER, map)
        bump()
        return later
    }

    fun readLaterItems(): List<RssItem> = itemMap(KEY_LATER).values.toList()

    // ── 阅读进度（滚动位置 px） ─────────────
    fun saveProgress(key: String, offsetPx: Float) {
        if (offsetPx < 10f) return // 顶部附近不记录
        val cur = progressMap()
        if (cur[key] == offsetPx) return
        cur[key] = offsetPx
        prefs.edit().putString(KEY_PROGRESS, JSONObject(cur.mapValues { it.value.toString() }).toString()).apply()
    }

    fun getProgress(key: String): Float = progressMap()[key] ?: 0f

    // ── 已读清理（v1.10） ───────────────────
    fun clearRead() {
        prefs.edit().remove(KEY_READ).apply()
        bump()
    }
    private fun itemMap(prefKey: String): Map<String, RssItem> {
        val raw = prefs.getString(prefKey, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { k -> k to rssItemFromJson(obj.getJSONObject(k)) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveItemMap(prefKey: String, map: Map<String, RssItem>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toJson()) }
        prefs.edit().putString(prefKey, obj.toString()).apply()
    }

    private fun progressMap(): MutableMap<String, Float> {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateTo(linkedMapOf()) { k ->
                k to (obj.optString(k).toFloatOrNull() ?: 0f)
            }
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

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

    private companion object {
        const val KEY_READ = "read_keys"
        const val KEY_STARRED = "starred_items"
        const val KEY_LATER = "later_items"
        const val KEY_PROGRESS = "progress_px"
    }
}
