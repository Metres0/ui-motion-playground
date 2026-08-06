package com.example.uiplayground.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uiplayground.data.ArticleDetail
import com.example.uiplayground.data.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val detail: ArticleDetail, val prefetched: Boolean) : DetailUiState
    data object Error : DetailUiState
}

/**
 * 详情页 ViewModel。
 *
 * `prefetched` 的判定基于 Repository 的真实缓存事实：
 * `wasPrefetched(id)` 要求「路由层确实发起过预取」**且**「详情已写入内存缓存」，
 * 不再用「耗时 < 80ms」这种脆弱启发式（慢网络 / 快机器都会误判）。
 */
class DetailViewModel(
    private val repository: ArticleRepository,
    private val articleId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val detail = repository.getArticleDetail(articleId)
                _state.value = DetailUiState.Success(detail, prefetched = repository.wasPrefetched(articleId))
            } catch (e: Exception) {
                _state.value = DetailUiState.Error
            }
        }
    }
}
