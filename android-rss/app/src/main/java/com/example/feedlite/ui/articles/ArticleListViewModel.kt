package com.example.feedlite.ui.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.RssFeed
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(val feed: RssFeed, val visibleCount: Int) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

/**
 * 文章列表 ViewModel —— 「先加载 5 篇，其余点击加载更多」的状态机。
 *
 * - 抓取一次 RSS feed（一次网络请求），UI 层先展示 5 篇；
 * - [loadMore] 每次追加 5 篇；底部按钮仅在还有未展示条目时出现。
 */
class ArticleListViewModel(
    private val repository: RssRepository,
    private val store: SubscriptionStore,
    private val sourceId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<ArticleListUiState>(ArticleListUiState.Loading)
    val state: StateFlow<ArticleListUiState> = _state.asStateFlow()

    val source get() = store.allSources().firstOrNull { it.id == sourceId }

    init { load() }

    fun load() {
        val s = source
        if (s == null) {
            _state.value = ArticleListUiState.Error("订阅源不存在，请返回重试")
            return
        }
        viewModelScope.launch {
            _state.value = ArticleListUiState.Loading
            try {
                val feed = repository.fetchFeed(s)
                // ★ 修复闪退：文章数不足 5 时按实际数量展示，防止 items[index] 越界
                _state.value = ArticleListUiState.Success(feed, visibleCount = minOf(5, feed.items.size))
            } catch (e: Exception) {
                _state.value = ArticleListUiState.Error(e.message ?: "加载失败，请检查网络")
            }
        }
    }

    /** 强制重新抓取（下拉刷新语义）。 */
    fun refresh() {
        source?.let { repository.refresh(it) }
        load()
    }

    /** 点击「加载更多」：追加 5 篇。 */
    fun loadMore() {
        val cur = _state.value as? ArticleListUiState.Success ?: return
        val next = (cur.visibleCount + 5).coerceAtMost(cur.feed.items.size)
        _state.value = cur.copy(visibleCount = next)
    }
}
