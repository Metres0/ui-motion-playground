package com.example.feedlite.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.HtmlText
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.TimeUtils
import com.example.feedlite.data.UpdateSettings
import com.example.feedlite.ui.components.ProgressiveImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * 首页聚合流（v1.23）：
 * - **无侧边栏**：订阅管理 / 设置 / 帮助 等全部移入「设置」页（底部栏 Tab）
 * - 顶部无任何图标，纯内容区（状态栏安全区处理）
 * - 下拉刷新、滚动隐藏底部栏、未读圆点、共享元素转场保留
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    repository: RssRepository,
    store: SubscriptionStore,
    updateSettings: UpdateSettings,
    readingState: ReadingStateStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenArticle: (RssItem) -> Unit,
    refreshTick: Int, // ★ 设置页改订阅后返回首页时触发刷新
    onScrollVisibilityChange: (Boolean) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(repository, store, updateSettings) } }
    )
    val state by viewModel.state.collectAsState()
    val readVersion by readingState.version.collectAsState()

    // ★ 设置页改订阅后刷新首页
    LaunchedEffect(refreshTick) { if (refreshTick > 0) viewModel.load() }

    // ★ v1.22：下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if ((state as? HomeUiState.Success)?.updating != true) isRefreshing = false
    }

    // ★ 滚动方向监听（节流）：只在方向切换/回顶时通知，避免底部栏状态风暴
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        var lastDown: Boolean? = null
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val down = index > lastIndex || (index == lastIndex && offset > lastOffset)
                val atTop = index == 0 && offset == 0
                val target = if (atTop) true else !down
                if (target != lastDown) {
                    onScrollVisibilityChange(target)
                    lastDown = target
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    // ★ 顶部纯内容（状态栏安全区）——不再有任何顶部按钮，空间全部留给文章
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        when (val s = state) {
            HomeUiState.Loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在聚合订阅源…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is HomeUiState.Success -> {
                if (s.entries.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(com.example.feedlite.R.drawable.ic_brand_logo),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (s.enabledCount == 0) "还没有启用任何订阅源\n到底部「设置」页勾选感兴趣的源"
                            else "订阅源抓取失败，请检查网络\n（成功 ${s.loadedCount}/${s.enabledCount}）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::refresh) { Text("重试") }
                    }
                } else {
                    // ★ 下拉刷新
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.refresh()
                            onScrollVisibilityChange(true)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        ) {
                            items(s.entries.size, key = { s.entries[it].item.key }) { i ->
                                val entry = s.entries[i]
                                HomeArticleCard(
                                    entry = entry,
                                    index = i,
                                    readingState = readingState,
                                    refreshKey = readVersion,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = { onOpenArticle(entry.item) },
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 首页聚合文章卡片（v1.8 排版：源名 + 标题 + 相对时间，已读状态随版本刷新）。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.HomeArticleCard(
    entry: FeedEntry,
    index: Int,
    readingState: ReadingStateStore,
    refreshKey: Int,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val enterOffset = remember(entry.item.key) { Animatable(MotionTokens.Space.Small) }
    val enterAlpha = remember(entry.item.key) { Animatable(0f) }
    val isRead = remember(refreshKey, entry.item.key) { readingState.isRead(entry.item.key) }

    LaunchedEffect(entry.item.key) {
        delay((index % 10) * 30L)
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
        // ★ 无缩略图不显示色块，直接放标题
        if (entry.item.imageUrl != null) {
            ProgressiveImage(
                url = entry.item.imageUrl,
                seed = entry.item.key.hashCode(),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .sharedElement(
                        state = rememberSharedContentState(key = "thumb_${entry.item.key}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.source.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val time = TimeUtils.timeAgo(entry.item.pubDate)
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ★ 未读小圆点
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
                    text = entry.item.title,
                    style = if (isRead) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
