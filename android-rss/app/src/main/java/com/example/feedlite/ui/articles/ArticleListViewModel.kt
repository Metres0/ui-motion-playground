package com.example.feedlite.ui.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.UpdateSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val items: List<RssItem>,
        val visibleCount: Int,
        val sourceTitle: String,
        /** 是否有后台增量更新在跑 */
        val updating: Boolean = false,
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

/**
 * 文章列表 ViewModel（v1.4：缓存优先 + 增量更新）。
 *
 * - 进入：先读本地缓存立即展示，若超过更新间隔则后台增量抓取；
 * - 「先加载 5 篇，其余点击加载更多」保留。
 */
class ArticleListViewModel(
    private val repository: RssRepository,
    private val store: SubscriptionStore,
    private val updateSettings: UpdateSettings,
    private val sourceId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<ArticleListUiState>(ArticleListUiState.Loading)
    val state: StateFlow<ArticleListUiState> = _state.asStateFlow()

    private val source get() = store.allSources().firstOrNull { it.id == sourceId }

    init { load() }

    fun load() {
        val s = source
        if (s == null) {
            _state.value = ArticleListUiState.Error("订阅源不存在，请返回重试")
            return
        }
        val title = s.title

        // 1. 立即读缓存（秒开）
        val cached = repository.cachedItems(sourceId)
        if (cached.isNotEmpty()) {
            _state.value = ArticleListUiState.Success(
                items = cached,
                visibleCount = minOf(5, cached.size),
                sourceTitle = title,
            )
        } else {
            _state.value = ArticleListUiState.Loading
        }

        // 2. 超过间隔则后台增量更新
        if (updateSettings.needsUpdate(repository, sourceId)) {
            viewModelScope.launch {
                try {
                    repository.updateSource(s)
                } catch (e: Exception) {
                    // 网络失败：有缓存则继续展示缓存
                }
                val items = repository.cachedItems(sourceId)
                _state.value = ArticleListUiState.Success(
                    items = items,
                    visibleCount = minOf(5, items.size),
                    sourceTitle = title,
                )
            }
        }
    }

    /** 手动刷新：强制增量抓取。 */
    fun refresh() {
        val s = source ?: return
        viewModelScope.launch {
            _state.value = (state.value as? ArticleListUiState.Success)?.copy(updating = true)
                ?: ArticleListUiState.Loading
            try {
                repository.updateSource(s)
            } catch (e: Exception) {
                _state.value = ArticleListUiState.Error(e.message ?: "加载失败，请检查网络")
            }
            val items = repository.cachedItems(sourceId)
            _state.value = ArticleListUiState.Success(
                items = items,
                visibleCount = minOf(5, items.size),
                sourceTitle = s.title,
            )
        }
    }

    /** 点击「加载更多」：追加 5 篇。 */
    fun loadMore() {
        val cur = _state.value as? ArticleListUiState.Success ?: return
        val next = (cur.visibleCount + 5).coerceAtMost(cur.items.size)
        _state.value = cur.copy(visibleCount = next)
    }
}
