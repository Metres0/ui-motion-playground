package com.example.feedlite.ui.starred

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssItem
import com.example.feedlite.ui.ArticleCache
import com.example.feedlite.ui.components.ProgressiveImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 收藏列表页（v1.8）：
 * - 版本号驱动刷新：返回本页 / 其他页取消收藏时列表自动更新；
 * - 顶栏「导出」：把收藏导出为 JSON 文件（SAF）。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.StarredScreen(
    readingState: ReadingStateStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenArticle: (RssItem) -> Unit,
) {
    val context = LocalContext.current
    val version by readingState.version.collectAsState()
    var items by remember { mutableStateOf(readingState.starredItems()) }

    // ★ 收藏变更时刷新列表
    LaunchedEffect(version) { items = readingState.starredItems() }

    // ★ 导出为 JSON 文件
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(readingState.exportJson().toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, "收藏已导出", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "导出失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("feedlite_favorites.json") }) {
                        Icon(Icons.Default.Share, contentDescription = "导出收藏")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有收藏的文章\n在文章详情页点右上角星标即可收藏", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items.size, key = { items[it].key }) { i ->
                    val item = items[i]
                    val enterOffset = remember(item.key) { Animatable(MotionTokens.Space.Small) }
                    val enterAlpha = remember(item.key) { Animatable(0f) }
                    LaunchedEffect(item.key) {
                        delay((i % 10) * 30L)
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
                            .clickable {
                                ArticleCache.put(item.key, item)
                                onOpenArticle(item)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressiveImage(
                            url = item.imageUrl,
                            seed = item.key.hashCode(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .sharedElement(
                                    state = rememberSharedContentState(key = "thumb_${item.key}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = item.author.takeIf { it.isNotBlank() } ?: "已收藏",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
