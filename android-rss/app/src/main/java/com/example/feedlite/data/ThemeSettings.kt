package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题设置（v1.8）：浅色 / 深色 / 跟随系统。
 * 用单例 StateFlow，MainActivity 收集后全局生效。
 */
class ThemeSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(prefs.getString(KEY_MODE, MODE_SYSTEM) ?: MODE_SYSTEM)
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun setMode(m: String) {
        prefs.edit().putString(KEY_MODE, m).apply()
        _mode.value = m
    }

    fun load(): String = _mode.value

    companion object {
        private const val KEY_MODE = "mode"
        const val MODE_SYSTEM = "system"
        const val MODE_LIGHT = "light"
        const val MODE_DARK = "dark"
        val OPTIONS = listOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)
    }
}
