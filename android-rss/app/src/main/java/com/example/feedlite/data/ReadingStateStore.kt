package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 阅读状态持久化（v1.8）：已读标记 + 收藏 + 变更版本号（供 UI 刷新）。
 */
class ReadingStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reading_state", Context.MODE_PRIVATE)

    /** 收藏/已读变更时自增；首页、列表、收藏页 collect 它来刷新。 */
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
    fun isStarred(key: String): Boolean = starredMap().containsKey(key)

    fun toggleStar(item: RssItem): Boolean {
        val map = starredMap().toMutableMap()
        val starred = if (map.containsKey(item.key)) {
            map.remove(item.key); false
        } else {
            map[item.key] = item; true
        }
        prefs.edit().putString(KEY_STARRED, JSONObject(map).toString()).apply()
        bump() // ★ 通知收藏页/首页刷新
        return starred
    }

    fun starredItems(): List<RssItem> {
        val map = starredMap()
        return map.keys.mapNotNull { map[it] }
    }

    /** ★ 导出收藏为 JSON 文本。 */
    fun exportJson(): String {
        val items = starredItems()
        val arr = JSONArray()
        items.forEach { it.toJson().also { arr.put(it) } }
        return arr.toString(2)
    }

    private fun starredMap(): Map<String, RssItem> {
        val raw = prefs.getString(KEY_STARRED, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { k ->
                k to rssItemFromJson(obj.getJSONObject(k))
            }
        } catch (e: Exception) {
            emptyMap()
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
    }
}
