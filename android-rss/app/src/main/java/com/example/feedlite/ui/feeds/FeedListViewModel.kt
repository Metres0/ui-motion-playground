package com.example.feedlite.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.SubscriptionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 订阅管理 ViewModel。
 * 数据源：内置目录 + 用户自定义源；启用状态实时持久化。
 */
class FeedListViewModel(private val store: SubscriptionStore) : ViewModel() {

    private val _sources = MutableStateFlow<List<FeedSource>>(store.allSources())
    val sources: StateFlow<List<FeedSource>> = _sources.asStateFlow()

    private val _enabled = MutableStateFlow<Set<String>>(store.enabledIds())
    val enabled: StateFlow<Set<String>> = _enabled.asStateFlow()

    fun toggle(id: String, on: Boolean) {
        store.setEnabled(id, on)
        _enabled.value = store.enabledIds()
    }

    fun addCustom(title: String, url: String) {
        viewModelScope.launch {
            store.addCustom(title, url)
            _sources.value = store.allSources()
            _enabled.value = store.enabledIds()
        }
    }

    fun removeCustom(id: String) {
        viewModelScope.launch {
            store.removeCustom(id)
            _sources.value = store.allSources()
            _enabled.value = store.enabledIds()
        }
    }
}
