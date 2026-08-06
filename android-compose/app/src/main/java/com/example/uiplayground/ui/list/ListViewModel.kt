package com.example.uiplayground.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.example.uiplayground.data.ArticlePagingSource
import com.example.uiplayground.data.ArticleRepository

/**
 * 列表页 ViewModel —— Paging3「优先加载」配置点。
 *
 * 两个旋钮直接决定加载行为（研究报告 §3.1）：
 * - `prefetchDistance = 5`  ：距底部还剩 5 条时提前加载下一页 → 滚动到底永不失联
 * - `initialLoadSize = 40`  ：首屏直接预载 2 页 → 打开就有货
 * - `cachedIn()`            ：PagingData 缓存到 ViewModel，旋转屏幕/返回时不重新请求
 */
class ListViewModel(repository: ArticleRepository) : ViewModel() {

    val articles = Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 5,
            initialLoadSize = 40,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { ArticlePagingSource(repository) },
    ).flow.cachedIn(viewModelScope)
}
