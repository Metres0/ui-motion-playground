package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 更新策略设置：自动更新间隔（手动 / 6h / 12h / 24h / 48h）+ 新文章通知开关。
 */
class UpdateSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("update", Context.MODE_PRIVATE)

    data class UpdateConfig(
        val intervalHours: Int, // 0 = 手动
        val notifyEnabled: Boolean = true, // 后台同步发现新文章时弹通知
    )

    fun load(): UpdateConfig = UpdateConfig(
        intervalHours = prefs.getInt(KEY_INTERVAL, 24).coerceAtLeast(0),
        notifyEnabled = prefs.getBoolean(KEY_NOTIFY, true),
    )

    fun save(config: UpdateConfig) {
        prefs.edit()
            .putInt(KEY_INTERVAL, config.intervalHours)
            .putBoolean(KEY_NOTIFY, config.notifyEnabled)
            .apply()
    }

    /** 是否需要刷新某源：缓存为空 或 距上次更新已超过间隔。 */
    fun needsUpdate(repository: RssRepository, sourceId: String): Boolean {
        val interval = load().intervalHours
        if (interval <= 0) return false // 手动模式
        if (!repository.hasCache(sourceId)) return true
        val elapsed = System.currentTimeMillis() - repository.lastUpdated(sourceId)
        return elapsed >= interval * 3600_000L
    }

    companion object {
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_NOTIFY = "notify_enabled"
        val OPTIONS = listOf(0, 6, 12, 24, 48)
    }
}
