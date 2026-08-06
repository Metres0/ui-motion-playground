package com.example.feedlite.ui.feeds

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.SubscriptionStore
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 订阅管理页：勾选启用内置源、添加/删除自定义源、点击源进入文章列表。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.FeedListScreen(
    store: SubscriptionStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenSource: (FeedSource) -> Unit,
) {
    val viewModel: FeedListViewModel = viewModel(
        factory = viewModelFactory { initializer { FeedListViewModel(store) } }
    )
    val sources by viewModel.sources.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅源") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加订阅")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "共 ${enabled.size}/${sources.size} 个已启用 · 点击进入文章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(sources.size, key = { sources[it].id }) { i ->
                val source = sources[i]
                SourceCard(
                    source = source,
                    checked = source.id in enabled,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onToggle = { viewModel.toggle(source.id, it) },
                    onOpen = { onOpenSource(source) },
                    onDelete = { viewModel.removeCustom(source.id) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onConfirm = { title, url -> viewModel.addCustom(title, url) },
            onDismiss = { showAddDialog = false },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.SourceCard(
    source: FeedSource,
    checked: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val enterOffset = remember(source.id) { Animatable(MotionTokens.Space.Small) }
    val enterAlpha = remember(source.id) { Animatable(0f) }

    LaunchedEffect(source.id) {
        delay(((source.seed) % 8) * 30L)
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
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // favicon 占位（共享元素：进入文章列表时徽章放大为页头标识）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .sharedElement(
                    state = rememberSharedContentState(key = "source_${source.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = source.initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                text = source.description.ifBlank { source.url },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        if (source.id.startsWith("custom_")) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
