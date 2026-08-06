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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.Star
import com.example.feedlite.data.FeedCategory
import com.example.feedlite.data.FeedSource
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 首页 = 聚合文章流（按分类分段）+ 侧边栏（分类分组的源管理 / 设置 / 关于 / 转源帮助）。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    repository: RssRepository,
    store: SubscriptionStore,
    updateSettings: UpdateSettings,
    readingState: ReadingStateStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenSource: (FeedSource) -> Unit,
    onOpenArticle: (RssItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenLater: () -> Unit,
    onScrollVisibilityChange: (Boolean) -> Unit, // ★ 首页下拉时隐藏底部栏
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(repository, store, updateSettings) }
        }
    )
    val state by viewModel.state.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val readVersion by readingState.version.collectAsState() // ★ 已读变化刷新

    // ★ 滚动方向监听：向下滚动（内容下移）隐藏底部栏，向上滚动显示
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                onScrollVisibilityChange(!scrollingDown)
                lastIndex = index
                lastOffset = offset
            }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showConvertHelp by remember { mutableStateOf(false) }

    fun closeDrawer() {
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    sources = sources,
                    enabled = enabled,
                    onToggle = { id, on -> viewModel.toggleSource(id, on) },
                    onOpenSource = { src ->
                        closeDrawer()
                        onOpenSource(src)
                    },
                    onAdd = { showAddDialog = true },
                    onDelete = viewModel::removeCustom,
                    onOpenSettings = {
                        closeDrawer()
                        onOpenSettings()
                    },
                    onOpenStarred = {
                        closeDrawer()
                        onOpenStarred()
                    },
                    onOpenLater = {
                        closeDrawer()
                        onOpenLater()
                    },
                    onAbout = { showAbout = true },
                    onConvertHelp = { showConvertHelp = true },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                // ★ 极简顶栏：再缩小一半——图标 16dp、按钮 28dp、无 padding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { coroutineScope.launch { drawerState.open() } },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单", modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新全部", modifier = Modifier.size(16.dp))
                    }
                }
            },
        ) { padding ->
            when (val s = state) {
                HomeUiState.Loading -> Column(
                    Modifier.fillMaxSize().padding(padding),
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
                            Modifier.fillMaxSize().padding(padding).padding(32.dp),
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
                                text = if (s.enabledCount == 0) "还没有启用任何订阅源\n打开左侧菜单勾选感兴趣的源"
                                else "订阅源抓取失败，请检查网络\n（成功 ${s.loadedCount}/${s.enabledCount}）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::refresh) { Text("重试") }
                        }
                    } else {
                        // ★ 按分类分段的聚合流
                        val groups = FeedCategory.ORDER.mapNotNull { cat ->
                            val list = s.entries.filter { it.source.category == cat }
                            if (list.isEmpty()) null else cat to list
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            groups.forEach { (cat, list) ->
                                item(key = "header_$cat") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            cat,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "${list.size} 篇",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                                items(list.size, key = { list[it].item.key }) { i ->
                                    val entry = list[i]
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

    if (showAddDialog) {
        AddSourceDialog(
            onConfirm = { title, url -> viewModel.addCustom(title, url) },
            onShowConvertHelp = { showConvertHelp = true },
            onDismiss = { showAddDialog = false },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showConvertHelp) {
        ConvertHelpDialog(onDismiss = { showConvertHelp = false })
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

/** 侧边栏：订阅源（按分类分组）+ 添加源 + 设置 + 关于。 */
@Composable
private fun DrawerContent(
    sources: List<FeedSource>,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpenSource: (FeedSource) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenLater: () -> Unit,
    onAbout: () -> Unit,
    onConvertHelp: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(com.example.feedlite.R.drawable.ic_brand_logo),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("轻阅 RSS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "订阅源 · ${enabled.size}/${sources.size} 已启用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        // ★ 源搜索框（v1.10）
        var search by remember { mutableStateOf("") }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("搜索源 / 分类…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // ★ 按分类分组展示（支持搜索过滤）
        val kw = search.trim()
        FeedCategory.ORDER.forEach { cat ->
            val list = sources.filter { it.category == cat }.filter {
                kw.isEmpty() || it.title.contains(kw, true) || it.description.contains(kw, true) || cat.contains(kw, true)
            }
            if (list.isNotEmpty()) {
                Text(
                    cat,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
                list.forEach { source ->
                    DrawerSourceRow(
                        source = source,
                        checked = source.id in enabled,
                        onToggle = { onToggle(source.id, it) },
                        onClick = { onOpenSource(source) },
                        onDelete = { onDelete(source.id) },
                    )
                }
            }
        }

        NavigationDrawerItem(
            label = { Text("添加订阅源") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            selected = false,
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("公众号 / 微博转源帮助") },
            icon = { Icon(painterResource(com.example.feedlite.R.drawable.ic_nav_help), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false,
            onClick = onConvertHelp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ★ 我的收藏
        NavigationDrawerItem(
            label = { Text("我的收藏") },
            icon = { Icon(painterResource(com.example.feedlite.R.drawable.ic_nav_star), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false,
            onClick = onOpenStarred,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        // ★ 稍后再看
        NavigationDrawerItem(
            label = { Text("稍后再看") },
            icon = { Icon(painterResource(com.example.feedlite.R.drawable.ic_nav_bookmark), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false,
            onClick = onOpenLater,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("设置") },
            icon = { Icon(painterResource(com.example.feedlite.R.drawable.ic_nav_settings), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("关于") },
            icon = { Icon(painterResource(com.example.feedlite.R.drawable.ic_nav_help), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** 抽屉单源行：点击进入，Switch 管理启用，自定义源可删除。 */
@Composable
private fun DrawerSourceRow(
    source: FeedSource,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = source.initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (source.id.startsWith("custom_")) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除 ${source.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** 添加订阅源对话框。 */
@Composable
private fun AddSourceDialog(
    onConfirm: (String, String) -> Unit,
    onShowConvertHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
        title = { Text("添加 RSS 源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称（如：我的博客）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed 地址（如：example.com/feed）") },
                    singleLine = true,
                )
                TextButton(onClick = onShowConvertHelp) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("公众号 / 微博怎么转成 RSS？", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onConfirm(title.trim(), url.trim())
                    onDismiss()
                },
            ) { Text("添加") }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "取消") }
        },
    )
}

/** 转源帮助对话框：公众号 / 微博 → RSS 的路径。 */
@Composable
private fun ConvertHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
        title = { Text("公众号 / 微博 转 RSS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("微信公众号", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 自建 Wechat2RSS（wechat2rss.xlab.app，需一台服务器）；\n" +
                        "2. 或使用 RSSHub 的 /wechat/ 相关路由；\n" +
                        "3. 得到 feed 地址后，通过「添加订阅源」填入即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text("微博", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. 使用 RSSHub 路由 /weibo/user/{uid}；\n" +
                        "2. uid 为微博用户数字 ID（可在个人主页 URL 中查看）；\n" +
                        "3. 公共实例可能限流，建议自建 RSSHub（github.com/DIYgod/RSSHub）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        },
    )
}

/** 关于对话框。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = painterResource(com.example.feedlite.R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        },
        title = { Text("轻阅 RSS v1.2") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("极简 RSS 阅读器 · 基于 Android 16")
                Text("内置 13 个订阅源（技术/AI/Go/商业/国际分类）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("富文本排版 · 阅读设置 · AI 翻译（代码块保留原文）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("好的") }
        },
    )
}
