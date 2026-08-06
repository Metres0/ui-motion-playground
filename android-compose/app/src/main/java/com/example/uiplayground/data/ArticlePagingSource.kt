package com.example.uiplayground.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Paging3 分页源。
 *
 * 职责边界（研究报告 §3.1）：
 * - `load()` 处理 Refresh / Append / Prepend 三种请求；
 * - `getRefreshKey()` 在配置变更后从锚点位置恢复用户浏览位置。
 */
class ArticlePagingSource(
    private val repository: ArticleRepository,
) : PagingSource<Int, Article>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Article> {
        val page = params.key ?: 1
        return try {
            val data = repository.getArticles(page, params.loadSize)
            LoadResult.Page(
                data = data,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Article>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
