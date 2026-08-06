package com.example.feedlite.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.CodeBlockExtractor
import com.example.feedlite.data.HtmlText
import com.example.feedlite.data.Translator
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.ui.ArticleCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Translating : TranslationUiState
    data class Done(val text: String) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

/**
 * 详情页 ViewModel。
 *
 * v1.4：翻译结果「替换原文显示」，可一键切换原文/译文。
 * - [showTranslation] 为 true 时，正文区域渲染译文而非原文块；
 * - 翻译成功后自动切到译文，顶栏「原文/译文」切换按钮随时可换。
 */
class ArticleDetailViewModel(
    private val articleKey: String,
    private val translator: Translator,
    private val store: TranslationStore,
) : ViewModel() {

    private val _translation = MutableStateFlow<TranslationUiState>(
        ArticleCache.translations[articleKey]?.let { TranslationUiState.Done(it) }
            ?: TranslationUiState.Idle
    )
    val translation: StateFlow<TranslationUiState> = _translation.asStateFlow()

    private val _showTranslation = MutableStateFlow(
        ArticleCache.translations[articleKey] != null
    )
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    fun translate() {
        if (_translation.value is TranslationUiState.Translating) return

        val cached = ArticleCache.translations[articleKey]
        if (cached != null) {
            _translation.value = TranslationUiState.Done(cached)
            _showTranslation.value = true
            return
        }
        val item = ArticleCache.get(articleKey)
        if (item == null) {
            _translation.value = TranslationUiState.Error("文章不存在")
            return
        }
        if (!store.isConfigured()) {
            _translation.value = TranslationUiState.Error("未配置翻译 API Key，请到「设置」填写")
            return
        }

        viewModelScope.launch {
            _translation.value = TranslationUiState.Translating
            try {
                val extracted = CodeBlockExtractor.extract(item.descriptionHtml)
                val textForTranslate = HtmlText.toPlainText(extracted.placeholderText)
                if (textForTranslate.isBlank()) {
                    _translation.value = TranslationUiState.Error("没有可翻译的内容")
                    return@launch
                }
                val rawResult = translator.translate(textForTranslate)
                val restored = CodeBlockExtractor.restore(rawResult, extracted.codes)
                ArticleCache.translations[articleKey] = restored
                _translation.value = TranslationUiState.Done(restored)
                _showTranslation.value = true // ★ 翻译完成后替换原文显示
            } catch (e: Exception) {
                _translation.value = TranslationUiState.Error(e.message ?: "翻译失败，请重试")
            }
        }
    }

    /** 一键切换原文/译文。 */
    fun toggleTranslation() {
        _showTranslation.value = !_showTranslation.value
    }

    /** 是否有可用的译文。 */
    fun hasTranslation(): Boolean = _translation.value is TranslationUiState.Done
}
