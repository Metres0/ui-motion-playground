package com.example.feedlite.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.HtmlText
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.ui.components.ProgressiveImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 首页 = 聚合文章流 + 侧边栏（源管理 / 设置 / 关于）。
 * 源的管理不再占用独立页面，全部收敛进抽屉。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    repository: RssRepository,
    store: SubscriptionStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenSource: (FeedSource) -> Unit,
    onOpenArticle: (RssItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(repository, store) } }
    )
    val state by viewModel.state.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val enabled by viewModel.enabled.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

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
                    onToggle = { id, on ->
                        viewModel.toggleSource(id, on)
                    },
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
                    onAbout = { showAbout = true },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("轻阅 RSS") },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新全部")
                        }
                    },
                )
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
                            Icon(Icons.Default.RssFeed, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(s.entries.size, key = { s.entries[it].item.key }) { i ->
                                val entry = s.entries[i]
                                HomeArticleCard(
                                    entry = entry,
                                    index = i,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onClick = { onOpenArticle(entry.item) },
                                )
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
            onDismiss = { showAddDialog = false },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** 首页聚合文章卡片（缩略图与详情共享元素转场）。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.HomeArticleCard(
    entry: FeedEntry,
    index: Int,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val enterOffset = remember(entry.item.key) { Animatable(MotionTokens.Space.Small) }
    val enterAlpha = remember(entry.item.key) { Animatable(0f) }

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
        ProgressiveImage(
            url = entry.item.imageUrl,
            seed = entry.item.key.hashCode(),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .sharedElement(
                    state = rememberSharedContentState(key = "thumb_${entry.item.key}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.source.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = HtmlText.excerpt(entry.item.descriptionHtml, 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 侧边栏：订阅源管理 + 设置 + 关于。 */
@Composable
private fun DrawerContent(
    sources: List<FeedSource>,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpenSource: (FeedSource) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("轻阅 RSS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "订阅源 · ${enabled.size}/${sources.size} 已启用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        Text(
            "订阅源",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        sources.forEach { source ->
            DrawerSourceRow(
                source = source,
                checked = source.id in enabled,
                onToggle = { onToggle(source.id, it) },
                onClick = { onOpenSource(source) },
                onDelete = { onDelete(source.id) },
            )
        }
        NavigationDrawerItem(
            label = { Text("添加订阅源") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            selected = false,
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        NavigationDrawerItem(
            label = { Text("翻译设置") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("关于") },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            selected = false,
            onClick = onAbout,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** 抽屉中的单源行：点击进入该源，Switch 管理启用。 */
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
private fun AddSourceDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
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

/** 关于对话框。 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("轻阅 RSS v1.1") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("极简 RSS 阅读器 · 基于 Android 16")
                Text("内置 8 个订阅源 · 支持自定义 · 集成 AI 翻译", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("动效：共享元素转场 / stagger / 渐进式图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("好的") }
        },
    )
}
