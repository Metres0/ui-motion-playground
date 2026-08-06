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
 * 翻译流程（v1.2）：
 * 1. 从原文 HTML 提取 <pre> 代码块 → 占位符（**代码块不参与翻译**）；
 * 2. 占位文本 → 纯文本 → 调用翻译接口；
 * 3. 译文还原占位符 → 代码块原文；
 * 4. 结果缓存到 [ArticleCache.translations]。
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

    fun translate() {
        if (_translation.value is TranslationUiState.Done || _translation.value is TranslationUiState.Translating) return

        val cached = ArticleCache.translations[articleKey]
        if (cached != null) {
            _translation.value = TranslationUiState.Done(cached)
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
            } catch (e: Exception) {
                _translation.value = TranslationUiState.Error(e.message ?: "翻译失败，请重试")
            }
        }
    }
}
