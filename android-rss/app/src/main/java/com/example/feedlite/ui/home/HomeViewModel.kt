package com.example.feedlite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.FeedSource
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

/** 聚合流条目：文章 + 来源。 */
data class FeedEntry(val item: RssItem, val source: FeedSource)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val entries: List<FeedEntry>,
        val loadedCount: Int,
        val enabledCount: Int,
        /** 本次更新中失败的源数量（用于真实显示「成功 X/Y」）。 */
        val failedCount: Int = 0,
        /** 本次是否有后台增量更新在跑 */
        val updating: Boolean = false,
    ) : HomeUiState
}

/**
 * 首页聚合流 ViewModel（v1.4：缓存秒开 + 增量更新；v1.32：IO 读缓存 + 失败计数）。
 *
 * - 初始化：后台协程读所有启用源的本地缓存 → 立即展示（秒开）；
 * - 后台协程：对「无缓存」或「超过更新间隔」的源做增量抓取；
 * - 手动刷新 [refresh]：force 强制全部增量抓取（绕过 5 分钟 TTL）；
 * - 缓存读取与网络都在 Dispatchers.IO，不阻塞主线程。
 */
class HomeViewModel(
    private val repository: RssRepository,
    private val store: SubscriptionStore,
    private val updateSettings: UpdateSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        val enabledIds = store.enabledIds()
        val sources = enabledIds.mapNotNull { id -> store.allSources().firstOrNull { it.id == id } }
        viewModelScope.launch {
            // 1. 立即从本地缓存展示（秒开）
            val entries = withContext(Dispatchers.IO) {
                sources.flatMap { src ->
                    repository.cachedItems(src.id).map { FeedEntry(it, src) }
                }
            }
            // 2. 后台增量更新：只处理无缓存或超过间隔的源
            val needUpdate = sources.filter { updateSettings.needsUpdate(repository, it.id) }
            if (entries.isNotEmpty()) {
                // 只有确实有更新在跑时才置 updating=true（避免下拉刷新指示器假转）
                _state.value = HomeUiState.Success(
                    entries = entries,
                    loadedCount = sources.size,
                    enabledCount = enabledIds.size,
                    updating = needUpdate.isNotEmpty(),
                )
            }

            if (needUpdate.isNotEmpty()) {
                val result = repository.updateSources(needUpdate)
                val newEntries = withContext(Dispatchers.IO) {
                    sources.flatMap { src ->
                        repository.cachedItems(src.id).map { FeedEntry(it, src) }
                    }
                }
                _state.value = HomeUiState.Success(
                    entries = newEntries,
                    loadedCount = sources.size,
                    enabledCount = enabledIds.size,
                    failedCount = result.failures.size,
                )
            } else if (_state.value !is HomeUiState.Success) {
                _state.value = HomeUiState.Success(
                    entries = entries,
                    loadedCount = sources.size,
                    enabledCount = enabledIds.size,
                )
            }
        }
    }

    /** 手动刷新：force 强制全部启用源增量抓取。 */
    fun refresh() {
        val enabledIds = store.enabledIds()
        val sources = enabledIds.mapNotNull { id -> store.allSources().firstOrNull { it.id == id } }
        viewModelScope.launch {
            _state.value = HomeUiState.Success(
                entries = _state.value.let { (it as? HomeUiState.Success)?.entries ?: emptyList() },
                loadedCount = sources.size,
                enabledCount = enabledIds.size,
                updating = true,
            )
            val result = repository.updateSources(sources, force = true)
            val newEntries = withContext(Dispatchers.IO) {
                sources.flatMap { src ->
                    repository.cachedItems(src.id).map { FeedEntry(it, src) }
                }
            }
            _state.value = HomeUiState.Success(
                entries = newEntries,
                loadedCount = sources.size,
                enabledCount = enabledIds.size,
                failedCount = result.failures.size,
            )
        }
    }
}
