package com.example.uiplayground.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlin.math.max

/**
 * Paging3 分页源。
 *
 * 职责边界（研究报告 §3.1）：
 * - `load()` 处理 Refresh / Append / Prepend 三种请求；
 * - `getRefreshKey()` 在配置变更后从锚点位置恢复用户浏览位置。
 *
 * Key 采用**数据偏移量**（0-based），不是页码：
 * Refresh 从 0 起拉 loadSize 条，Append 从上一页 nextKey 继续，
 * 保证任何一次追加都不与已有页重叠（页码 + loadSize 语义在
 * initialLoadSize=40 / pageSize=20 时会重复取 20~39）。
 */
class ArticlePagingSource(
    private val repository: ArticleRepository,
) : PagingSource<Int, Article>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Article> {
        val start = params.key ?: 0
        return try {
            val data = repository.getArticles(start, params.loadSize)
            LoadResult.Page(
                data = data,
                prevKey = if (start == 0) null else max(0, start - params.loadSize),
                nextKey = if (data.isEmpty()) null else start + data.size,
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
