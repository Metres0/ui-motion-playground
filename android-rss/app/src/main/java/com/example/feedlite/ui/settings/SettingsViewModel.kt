package com.example.feedlite.ui.settings

import androidx.lifecycle.ViewModel
import com.example.feedlite.data.TranslationConfig
import com.example.feedlite.data.TranslationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 ViewModel：翻译配置的读写。
 * 配置保存即时生效（Translator 每次从 store 读取最新值）。
 */
class SettingsViewModel(private val store: TranslationStore) : ViewModel() {

    private val _config = MutableStateFlow(store.current())
    val config: StateFlow<TranslationConfig> = _config.asStateFlow()

    /** 应用模板（provider 改变时填充建议 baseUrl/model）。 */
    fun applyTemplate(provider: String) {
        _config.value = store.template(provider)
    }

    fun update(transform: (TranslationConfig) -> TranslationConfig) {
        _config.value = transform(_config.value)
    }

    /** 校验并保存；返回错误信息，null 表示成功。 */
    fun save(): String? {
        val c = _config.value
        if (c.apiKey.isBlank()) return "请填写 API Key"
        if (!c.baseUrl.startsWith("http")) return "Base URL 必须以 http(s):// 开头"
        store.save(c)
        return null
    }
}
