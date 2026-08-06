package com.example.feedlite.ui.articles

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.HtmlText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.TimeUtils
import com.example.feedlite.data.UpdateSettings
import com.example.feedlite.ui.components.ProgressiveImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 文章列表页。
 *
 * 加载策略（对应研究报告 §3）：
 * - 抓取一次完整 feed（一次网络请求）；
 * - UI 先展示前 5 篇，底部「加载更多」每次 +5；
 * - 顶栏刷新按钮 = 强制重新抓取；
 * - 文章封面与详情页大图共享元素转场。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.ArticleListScreen(
    sourceId: String,
    repository: RssRepository,
    store: SubscriptionStore,
    updateSettings: UpdateSettings,
    readingState: ReadingStateStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenArticle: (RssItem) -> Unit,
) {
    val viewModel: ArticleListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ArticleListViewModel(repository, store, updateSettings, sourceId) }
        }
    )
    val state by viewModel.state.collectAsState()
    val readVersion by readingState.version.collectAsState() // ★ 已读变化刷新

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val s = state) {
                        is ArticleListUiState.Success -> Text(
                            s.sourceTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        else -> Text("文章列表")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ArticleListUiState.Loading -> LoadingState(Modifier.padding(padding))
            is ArticleListUiState.Error -> ErrorState(
                message = s.message,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )
            is ArticleListUiState.Success -> {
                val items = s.items
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ★ 只渲染前 visibleCount 篇
                    items(s.visibleCount, key = { items[it].key }) { i ->
                        val item = items[i]
                        ArticleCard(
                            item = item,
                            index = i,
                            readingState = readingState,
                            refreshKey = readVersion,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onOpenArticle(item) },
                        )
                    }
                    // 「加载更多」按钮：仅在还有未展示条目时出现
                    if (s.visibleCount < items.size) {
                        item {
                            LoadMoreButton(
                                shown = s.visibleCount,
                                total = items.size,
                                onClick = viewModel::loadMore,
                            )
                        }
                    } else if (items.isNotEmpty()) {
                        item { Text("已加载全部 ${items.size} 篇", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
                    }
                    if (items.isEmpty()) {
                        item { Text("该源暂时没有文章", modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ArticleCard(
    item: RssItem,
    index: Int,
    readingState: ReadingStateStore,
    refreshKey: Int,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val enterOffset = remember(item.key) { Animatable(MotionTokens.Space.Small) }
    val enterAlpha = remember(item.key) { Animatable(0f) }
    val isRead = remember(refreshKey, item.key) { readingState.isRead(item.key) }

    LaunchedEffect(item.key) {
        delay((index % 10) * 30L) // stagger 30ms/项
        enterOffset.animateTo(0f, MotionTokens.micro())
        enterAlpha.animateTo(1f, MotionTokens.micro())
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
            url = item.imageUrl,
            seed = item.key.hashCode(),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .sharedElement(
                    state = rememberSharedContentState(key = "thumb_${item.key}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isRead) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    item.title,
                    style = if (isRead) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // ★ 过滤「点击查看原文」噪音
            if (HtmlText.hasMeaningfulContent(item.descriptionHtml)) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = HtmlText.excerpt(item.descriptionHtml),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = TimeUtils.timeAgo(item.pubDate).ifBlank {
                    item.author.takeIf { it.isNotBlank() } ?: ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadMoreButton(shown: Int, total: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("加载更多（${shown}/${total}）")
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在抓取订阅源…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
