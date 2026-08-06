package com.example.feedlite.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 详情页 ViewModel：管理翻译状态。
 * - 译文按文章 key 缓存在 [ArticleCache.translations]，重复进入秒开；
 * - 未配置 API Key 时给出引导提示。
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

    fun translate(originalText: String) {
        if (_translation.value is TranslationUiState.Done || _translation.value is TranslationUiState.Translating) return

        val cached = ArticleCache.translations[articleKey]
        if (cached != null) {
            _translation.value = TranslationUiState.Done(cached)
            return
        }
        if (!store.isConfigured()) {
            _translation.value = TranslationUiState.Error("未配置翻译 API Key，请到「设置」填写")
            return
        }

        viewModelScope.launch {
            _translation.value = TranslationUiState.Translating
            try {
                val result = translator.translate(originalText)
                ArticleCache.translations[articleKey] = result
                _translation.value = TranslationUiState.Done(result)
            } catch (e: Exception) {
                _translation.value = TranslationUiState.Error(e.message ?: "翻译失败，请重试")
            }
        }
    }

    fun reset() {
        _translation.value = TranslationUiState.Idle
    }
}
