package com.example.feedlite.ui.settings

import androidx.lifecycle.ViewModel
import com.example.feedlite.data.ReadingSettings
import com.example.feedlite.data.TranslationConfig
import com.example.feedlite.data.TranslationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 ViewModel：翻译配置 + 阅读设置。
 */
class SettingsViewModel(
    private val store: TranslationStore,
    private val reading: ReadingSettings,
) : ViewModel() {

    // ── 翻译配置 ────────────────────────────
    private val _config = MutableStateFlow(store.current())
    val config: StateFlow<TranslationConfig> = _config.asStateFlow()

    fun applyTemplate(provider: String) {
        _config.value = store.template(provider)
    }

    fun update(transform: (TranslationConfig) -> TranslationConfig) {
        _config.value = transform(_config.value)
    }

    fun saveTranslation(): String? {
        val c = _config.value
        if (c.apiKey.isBlank()) return "请填写 API Key"
        if (!c.baseUrl.startsWith("http")) return "Base URL 必须以 http(s):// 开头"
        store.save(c)
        return null
    }

    // ── 阅读设置 ────────────────────────────
    private val _reading = MutableStateFlow(reading.load())
    val readingConfig: StateFlow<ReadingSettings.ReadingConfig> = _reading.asStateFlow()

    fun updateReading(transform: (ReadingSettings.ReadingConfig) -> ReadingSettings.ReadingConfig) {
        _reading.value = transform(_reading.value)
    }

    fun saveReading() {
        reading.save(_reading.value)
    }

    /** 保存全部设置；返回错误信息，null 表示成功。 */
    fun saveAll(): String? {
        val err = saveTranslation()
        if (err != null) return err
        saveReading()
        return null
    }
}
