package com.example.feedlite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 聚合流条目：文章 + 来源。 */
data class FeedEntry(val item: RssItem, val source: FeedSource)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val entries: List<FeedEntry>,
        /** 抓取成功的源数 / 启用源总数，用于空态提示 */
        val loadedCount: Int,
        val enabledCount: Int,
    ) : HomeUiState
}

/**
 * 首页聚合流 ViewModel。
 *
 * - 并行抓取所有启用源，合并为按源顺序排列的文章流；
 * - 个别源失败不影响其他源（失败源跳过，[loadedCount] 反映成功数）；
 * - 同时承担侧边栏的源管理操作（toggle/add/remove），操作后刷新聚合。
 */
class HomeViewModel(
    private val repository: RssRepository,
    private val store: SubscriptionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _sources = MutableStateFlow<List<FeedSource>>(store.allSources())
    val sources: StateFlow<List<FeedSource>> = _sources.asStateFlow()

    private val _enabled = MutableStateFlow<Set<String>>(store.enabledIds())
    val enabled: StateFlow<Set<String>> = _enabled.asStateFlow()

    init { load() }

    fun load() {
        val enabledIds = store.enabledIds()
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            val feeds = enabledIds.mapNotNull { id ->
                store.allSources().firstOrNull { it.id == id }
            }.map { src ->
                async {
                    try { repository.fetchFeed(src) to src } catch (e: Exception) { null }
                }
            }
            val results = feeds.awaitAll().filterNotNull()
            val entries = results.flatMap { (feed, src) ->
                feed.items.map { FeedEntry(it, src) }
            }
            _state.value = HomeUiState.Success(
                entries = entries,
                loadedCount = results.size,
                enabledCount = enabledIds.size,
            )
        }
    }

    fun refresh() {
        store.allSources().forEach { repository.refresh(it) }
        load()
    }

    fun toggleSource(id: String, on: Boolean) {
        store.setEnabled(id, on)
        _enabled.value = store.enabledIds()
        load() // 聚合内容随开关变化
    }

    fun addCustom(title: String, url: String) {
        store.addCustom(title, url)
        _sources.value = store.allSources()
        _enabled.value = store.enabledIds()
        load()
    }

    fun removeCustom(id: String) {
        store.removeCustom(id)
        _sources.value = store.allSources()
        _enabled.value = store.enabledIds()
        load()
    }
}
