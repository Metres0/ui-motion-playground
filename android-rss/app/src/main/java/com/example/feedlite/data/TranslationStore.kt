package com.example.feedlite.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 翻译服务配置存储。
 *
 * - provider 预置模板：DeepSeek / MiMo / 自定义(OpenAI 兼容)
 * - 保存时校验 baseUrl 非空、以 http 开头
 * - 安全（v1.32）：API Key 改用 [SecurePrefs]（AndroidKeyStore AES/GCM）加密存储，
 *   不再明文落盘；首次启动自动迁移旧版明文 Key 并清除残留。
 */
class TranslationStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("translation", Context.MODE_PRIVATE)
    private val secure = SecurePrefs(context)

    init {
        // 一次性迁移：旧版明文 api_key → 加密存储，然后删除明文残留
        val legacy = prefs.getString(KEY_API_KEY, null)
        if (!legacy.isNullOrBlank() && secure.getString(KEY_API_KEY).isNullOrEmpty()) {
            secure.putString(KEY_API_KEY, legacy)
        }
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    /** 读取当前配置。 */
    fun current(): TranslationConfig = TranslationConfig(
        provider = prefs.getString(KEY_PROVIDER, "deepseek") ?: "deepseek",
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URLS["deepseek"]!!) ?: "",
        apiKey = secure.getString(KEY_API_KEY) ?: "",
        model = prefs.getString(KEY_MODEL, DEFAULT_MODELS["deepseek"]!!) ?: "",
        targetLang = prefs.getString(KEY_TARGET, "中文") ?: "中文",
    )

    fun save(config: TranslationConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider)
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_TARGET, config.targetLang)
            .apply()
        secure.putString(KEY_API_KEY, config.apiKey.trim())
    }

    fun isConfigured(): Boolean =
        secure.getString(KEY_API_KEY)?.isNotBlank() == true

    /** 应用 provider 模板（baseUrl + model 建议值），供设置页"选择模板"用。 */
    fun template(provider: String): TranslationConfig =
        current().copy(
            provider = provider,
            baseUrl = DEFAULT_BASE_URLS[provider] ?: current().baseUrl,
            model = DEFAULT_MODELS[provider] ?: current().model,
        )

    companion object {
        private const val KEY_PROVIDER = "provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TARGET = "target_lang"

        val PROVIDERS = listOf("deepseek", "mimo", "custom")

        val DEFAULT_BASE_URLS = mapOf(
            "deepseek" to "https://api.deepseek.com",
            "mimo" to "https://api.mimo.ai/v1",
            "custom" to "https://api.openai.com/v1",
        )

        val DEFAULT_MODELS = mapOf(
            "deepseek" to "deepseek-chat",
            "mimo" to "mimo-chat",
            "custom" to "",
        )
    }
}

/** 翻译服务配置。 */
data class TranslationConfig(
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val targetLang: String,
)
