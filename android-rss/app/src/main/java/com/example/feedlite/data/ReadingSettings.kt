package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 阅读设置持久化：字号缩放 / 行高 / 字体。
 * 详情页正文按此渲染。
 */
class ReadingSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    data class ReadingConfig(
        val fontSizeScale: Float,   // 0.85 ~ 1.40
        val lineHeightScale: Float, // 1.2 ~ 2.0
        val fontFamily: String,     // sans / serif / mono
    )

    fun load(): ReadingConfig = ReadingConfig(
        fontSizeScale = prefs.getFloat(KEY_SIZE, 1f).coerceIn(0.85f, 1.4f),
        lineHeightScale = prefs.getFloat(KEY_LINE, 1.4f).coerceIn(1.2f, 2.0f),
        fontFamily = prefs.getString(KEY_FONT, "sans") ?: "sans",
    )

    fun save(config: ReadingConfig) {
        prefs.edit()
            .putFloat(KEY_SIZE, config.fontSizeScale)
            .putFloat(KEY_LINE, config.lineHeightScale)
            .putString(KEY_FONT, config.fontFamily)
            .apply()
    }

    companion object {
        private const val KEY_SIZE = "font_size_scale"
        private const val KEY_LINE = "line_height_scale"
        private const val KEY_FONT = "font_family"
        const val FONT_SANS = "sans"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        val FONTS = listOf(FONT_SANS, FONT_SERIF, FONT_MONO)
    }
}
