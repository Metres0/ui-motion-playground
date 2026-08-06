package com.example.feedlite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.UpdateSettings
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
        val loadedCount: Int,
        val enabledCount: Int,
        /** 本次是否有后台增量更新在跑 */
        val updating: Boolean = false,
    ) : HomeUiState
}

/**
 * 首页聚合流 ViewModel（v1.4：缓存秒开 + 增量更新）。
 *
 * - 初始化：直接读所有启用源的本地缓存 → 立即展示（秒开）；
 * - 后台协程：对「无缓存」或「超过更新间隔」的源做增量抓取；
 * - 手动刷新 [refresh]：强制全部增量抓取（只加新文章）。
 */
class HomeViewModel(
    private val repository: RssRepository,
    private val store: SubscriptionStore,
    private val updateSettings: UpdateSettings,
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
        val sources = enabledIds.mapNotNull { id -> store.allSources().firstOrNull { it.id == id } }

        // 1. 立即从本地缓存展示（秒开；有缓存才展示，无缓存保持 Loading 等后台）
        val entries = sources.flatMap { src ->
            repository.cachedItems(src.id).map { FeedEntry(it, src) }
        }
        if (entries.isNotEmpty()) {
            _state.value = HomeUiState.Success(
                entries = entries,
                loadedCount = sources.size,
                enabledCount = enabledIds.size,
                updating = true,
            )
        }

        // 2. 后台增量更新：只处理无缓存或超过间隔的源
        val needUpdate = sources.filter { updateSettings.needsUpdate(repository, it.id) }
        if (needUpdate.isNotEmpty()) {
            viewModelScope.launch {
                repository.updateSources(needUpdate)
                val newEntries = sources.flatMap { src ->
                    repository.cachedItems(src.id).map { FeedEntry(it, src) }
                }
                _state.value = HomeUiState.Success(
                    entries = newEntries,
                    loadedCount = sources.size,
                    enabledCount = enabledIds.size,
                )
            }
        } else if (_state.value !is HomeUiState.Success) {
            _state.value = HomeUiState.Success(
                entries = entries,
                loadedCount = sources.size,
                enabledCount = enabledIds.size,
            )
        }
    }

    /** 手动刷新：强制全部启用源增量抓取。 */
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
            repository.updateSources(sources)
            val newEntries = sources.flatMap { src ->
                repository.cachedItems(src.id).map { FeedEntry(it, src) }
            }
            _state.value = HomeUiState.Success(
                entries = newEntries,
                loadedCount = sources.size,
                enabledCount = enabledIds.size,
            )
        }
    }

    fun toggleSource(id: String, on: Boolean) {
        store.setEnabled(id, on)
        _enabled.value = store.enabledIds()
        load()
    }

    fun addCustom(title: String, url: String) {
        store.addCustom(title, url)
        _sources.value = store.allSources()
        _enabled.value = store.enabledIds()
        load()
    }

    fun removeCustom(id: String) {
        store.removeCustom(id)
        repository.clearCache(id)
        _sources.value = store.allSources()
        _enabled.value = store.enabledIds()
        load()
    }
}
