package com.example.feedlite.ui.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.UpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val items: List<RssItem>,
        val visibleCount: Int,
        val sourceTitle: String,
        /** 是否有后台增量更新在跑 */
        val updating: Boolean = false,
        /** 最近一次后台/手动刷新失败的信息（有缓存时保留列表并显示横幅）。 */
        val updateError: String? = null,
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

/**
 * 文章列表 ViewModel（v1.4：缓存优先 + 增量更新；v1.32：错误不再被吞、force 刷新）。
 *
 * - 进入：先读本地缓存立即展示，若超过更新间隔则后台增量抓取；
 * - 手动刷新 = 强制重新抓取（绕过内存 TTL）；
 * - 刷新失败：有缓存 → 保留列表 + 顶部错误横幅；无缓存 → Error 态（不再被 Success 覆盖）。
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

        viewModelScope.launch {
            // 1. 立即读缓存（秒开）
            val cached = withContext(Dispatchers.IO) { repository.cachedItems(sourceId) }
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
                try {
                    repository.updateSource(s)
                    val items = withContext(Dispatchers.IO) { repository.cachedItems(sourceId) }
                    _state.value = ArticleListUiState.Success(
                        items = items,
                        visibleCount = minOf(5, items.size),
                        sourceTitle = title,
                    )
                } catch (e: Exception) {
                    // 网络失败：有缓存则保留缓存并显示横幅，无缓存则 Error 态
                    val cur = _state.value
                    if (cur is ArticleListUiState.Success) {
                        _state.value = cur.copy(updateError = e.message ?: "更新失败，请检查网络")
                    } else {
                        _state.value = ArticleListUiState.Error("加载失败，请检查网络：${e.message}")
                    }
                }
            }
        }
    }

    /** 手动刷新：强制增量抓取（绕过内存 TTL）。 */
    fun refresh() {
        val s = source ?: return
        viewModelScope.launch {
            _state.value = (state.value as? ArticleListUiState.Success)
                ?.copy(updating = true, updateError = null)
                ?: ArticleListUiState.Loading
            try {
                repository.updateSource(s, force = true)
                val items = withContext(Dispatchers.IO) { repository.cachedItems(sourceId) }
                _state.value = ArticleListUiState.Success(
                    items = items,
                    visibleCount = minOf(5, items.size),
                    sourceTitle = s.title,
                )
            } catch (e: Exception) {
                val cur = _state.value
                if (cur is ArticleListUiState.Success) {
                    _state.value = cur.copy(
                        updating = false,
                        updateError = e.message ?: "刷新失败，已展示缓存内容",
                    )
                } else {
                    _state.value = ArticleListUiState.Error(e.message ?: "加载失败，请检查网络")
                }
            }
        }
    }

    /** 点击「加载更多」：追加 5 篇。 */
    fun loadMore() {
        val cur = _state.value as? ArticleListUiState.Success ?: return
        val next = (cur.visibleCount + 5).coerceAtMost(cur.items.size)
        _state.value = cur.copy(visibleCount = next)
    }
}
