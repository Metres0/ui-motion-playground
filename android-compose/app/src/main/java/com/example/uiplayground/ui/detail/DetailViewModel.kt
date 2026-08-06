package com.example.uiplayground.ui.detail

import android.os.SystemClock
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
 * `prefetched` 的判定利用「预取命中 = 几乎零等待」的事实：
 * 路由预取把详情写进了 Repository 内存缓存，页面这里再请求时 < 80ms 即返回，
 * 说明数据是预取来的 —— 这是路由预取收益的直接可观测指标。
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
            val start = SystemClock.elapsedRealtime()
            try {
                val detail = repository.getArticleDetail(articleId)
                val elapsed = SystemClock.elapsedRealtime() - start
                _state.value = DetailUiState.Success(detail, prefetched = elapsed < 80)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error
            }
        }
    }
}
