package com.example.uiplayground.ui.list

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.animation.sharedElement
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import com.example.uiplayground.MotionTokens
import com.example.uiplayground.data.Article
import com.example.uiplayground.data.ArticleRepository
import com.example.uiplayground.ui.components.ProgressiveImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 列表页 —— 演示「Paging 预载 + stagger 进入 + 共享元素起点」。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ListScreen(
    repository: ArticleRepository,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onArticleClick: (Long) -> Unit,
) {
    val viewModel: ListViewModel = viewModel(
        factory = viewModelFactory { initializer { ListViewModel(repository) } }
    )
    val items = viewModel.articles.collectAsLazyPagingItems()

    Scaffold(
        topBar = { TopAppBar(title = { Text("列表 · Paging 预载 + 预取") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it?.id }) { article ->
                if (article != null) {
                    ArticleCard(
                        article = article,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { onArticleClick(article.id) },
                    )
                }
            }
            // Paging 尾部加载/重试（prefetchDistance 触发的前置预载在这里不可见，滚动到末尾只是结果）
            when (items.loadState.append) {
                LoadState.Loading -> item { LoadingRow() }
                is LoadState.Error -> item { RetryRow(onRetry = { items.retry() }) }
                else -> Unit
            }
        }
    }
}

/**
 * 列表卡片。
 * - `sharedElement`：封面图与详情页大图共享同一个 key，转场时自动位移缩放（研究报告 §2.2）
 * - stagger：进入时按 id 错峰 30ms 淡入+位移（研究报告 token 表第 7 项；真实项目应传 index）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ArticleCard(
    article: Article,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val enterOffset = remember(article.id) { Animatable(MotionTokens.Space.Small) }
    val enterAlpha = remember(article.id) { Animatable(0f) }

    LaunchedEffect(article.id) {
        delay(((article.id - 1) % 12) * 30L) // stagger 错峰
        coroutineScope {
            launch { enterOffset.animateTo(0f, MotionTokens.micro()) }
            launch { enterAlpha.animateTo(1f, MotionTokens.micro()) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(enterOffset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = enterAlpha.value }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressiveImage(
            url = article.coverUrl,
            seed = article.seed,
            contentDescription = article.title,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .sharedElement(
                    state = rememberSharedContentState(key = "cover_${article.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = article.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
    }
}
