package com.example.feedlite.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.ReadingSettings
import com.example.feedlite.data.TranslationConfig
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.UpdateSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel：翻译配置 + 阅读设置 + 更新策略 + 测试连接。
 */
class SettingsViewModel(
    private val store: TranslationStore,
    private val reading: ReadingSettings,
    private val translator: Translator,
    private val updateSettings: UpdateSettings,
) : ViewModel() {

    // ── 测试连接 ────────────────────────────
    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Success(val reply: String) : TestState
        data class Fail(val message: String) : TestState
    }

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _test.asStateFlow()

    /** 先保存当前翻译配置，再用它发一条翻译请求验证。 */
    fun testConnection(onNotConfigured: () -> Unit) {
        val err = saveTranslation()
        if (err != null) {
            _test.value = TestState.Fail(err)
            return
        }
        if (_test.value is TestState.Testing) return
        _test.value = TestState.Testing
        viewModelScope.launch {
            try {
                val reply = translator.translate("测试连接：Hello world")
                _test.value = TestState.Success(reply)
            } catch (e: Exception) {
                _test.value = TestState.Fail(e.message ?: "连接失败")
            }
        }
    }

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
        updateSettings.save(_updateConfig.value)
        return null
    }

    // ── 更新策略 ────────────────────────────
    private val _updateConfig = MutableStateFlow(updateSettings.load())
    val updateConfig: StateFlow<UpdateSettings.UpdateConfig> = _updateConfig.asStateFlow()

    fun setInterval(hours: Int) {
        _updateConfig.value = UpdateSettings.UpdateConfig(hours)
    }
}
