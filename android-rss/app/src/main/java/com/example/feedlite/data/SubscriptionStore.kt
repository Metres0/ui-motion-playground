package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 订阅状态持久化（SharedPreferences + JSON）。
 * 自定义源分类固定为 [FeedCategory.CUSTOM]。
 */
class SubscriptionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("subscriptions", Context.MODE_PRIVATE)

    fun enabledIds(): Set<String> {
        val saved = prefs.getStringSet(KEY_ENABLED, null) ?: return FeedCatalog.builtin
            .filter { it.defaultEnabled }.map { it.id }.toSet()
        return saved
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val current = enabledIds().toMutableSet()
        if (enabled) current.add(id) else current.remove(id)
        prefs.edit().putStringSet(KEY_ENABLED, current).apply()
    }

    fun allSources(): List<FeedSource> = FeedCatalog.builtin + customSources()

    fun customSources(): List<FeedSource> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FeedSource(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    description = o.optString("description", ""),
                    url = o.getString("url"),
                    category = o.optString("category", FeedCategory.CUSTOM),
                    seed = 100 + o.getInt("idHash"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustom(title: String, url: String) {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        val current = customSources().toMutableList()
        val existing = current.firstOrNull { it.url == normalized }
        if (existing != null) {
            current.remove(existing)
            current.add(existing.copy(title = title, description = existing.description))
        } else {
            val idHash = normalized.hashCode().and(0x7fffffff)
            current.add(
                FeedSource(
                    id = "custom_$idHash",
                    title = title,
                    description = "自定义订阅",
                    url = normalized,
                    category = FeedCategory.CUSTOM,
                    seed = 100 + idHash,
                )
            )
        }
        prefs.edit().putString(KEY_CUSTOM, JSONArray(current.map { it.toJson() }).toString()).apply()
    }

    fun removeCustom(id: String) {
        val current = customSources().filterNot { it.id == id }
        prefs.edit().putString(KEY_CUSTOM, JSONArray(current.map { it.toJson() }).toString()).apply()
    }

    private fun FeedSource.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("url", url)
        put("category", category)
        put("idHash", seed - 100)
    }

    private companion object {
        const val KEY_ENABLED = "enabled_ids"
        const val KEY_CUSTOM = "custom_sources"
    }
}
