package com.example.feedlite.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.ArticleFetcher
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

/** 全文抓取状态。 */
sealed interface FullTextUiState {
    data object Idle : FullTextUiState
    data object Loading : FullTextUiState
    data class Done(val html: String) : FullTextUiState
    data object Fail : FullTextUiState
}

/**
 * 详情页 ViewModel。
 *
 * v1.5：feed 只有摘要时，进入页面自动抓取原始网页正文（readability-lite）。
 * - [FullTextUiState] 管理全文抓取状态；正文过短才触发；
 * - v1.4 的翻译替换原文/切换逻辑保留。
 */
class ArticleDetailViewModel(
    private val articleKey: String,
    private val translator: Translator,
    private val store: TranslationStore,
    private val fetcher: ArticleFetcher,
) : ViewModel() {

    private val _fullText = MutableStateFlow<FullTextUiState>(FullTextUiState.Idle)
    val fullText: StateFlow<FullTextUiState> = _fullText.asStateFlow()

    init {
        // 正文过短（只有摘要）时自动抓全文
        val item = ArticleCache.get(articleKey)
        val html = item?.descriptionHtml.orEmpty()
        val tooShort = !HtmlText.hasMeaningfulContent(html) || HtmlText.toPlainText(html).length < 300
        val link = item?.link.orEmpty()
        if (tooShort && link.isNotBlank()) {
            fetchFullText(link)
        }
    }

    /** 抓取全文（可手动重试）。 */
    fun fetchFullText(link: String) {
        if (_fullText.value is FullTextUiState.Loading) return
        _fullText.value = FullTextUiState.Loading
        viewModelScope.launch {
            _fullText.value = try {
                FullTextUiState.Done(fetcher.fetchArticle(link))
            } catch (e: Exception) {
                FullTextUiState.Fail
            }
        }
    }

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
                // ★ 全文已抓到时优先翻译全文，否则翻译 feed 摘要
                val sourceHtml = (fullText.value as? FullTextUiState.Done)?.html ?: item.descriptionHtml
                val extracted = CodeBlockExtractor.extract(sourceHtml)
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
