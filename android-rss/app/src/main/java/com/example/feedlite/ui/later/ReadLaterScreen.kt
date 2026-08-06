package com.example.feedlite.ui.later

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
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
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
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
 * 稍后再看列表页（v1.9）：挂起的文章，点击进入详情，可移除。
 * 版本号流驱动刷新。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.ReadLaterScreen(
    readingState: ReadingStateStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenArticle: (RssItem) -> Unit,
) {
    val version by readingState.version.collectAsState()
    var items by remember { mutableStateOf(readingState.readLaterItems()) }

    LaunchedEffect(version) { items = readingState.readLaterItems() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("稍后再看") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(com.example.feedlite.R.drawable.ic_brand_logo),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("没有挂起的文章\n在文章详情页点「书签」图标加入稍后再看", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // 有图才显示缩略图，无图直接标题（不要色块）
                        if (item.imageUrl != null) {
                            ProgressiveImage(
                                url = item.imageUrl,
                                seed = item.key.hashCode(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .sharedElement(
                                        state = rememberSharedContentState(key = "thumb_${item.key}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = item.author.takeIf { it.isNotBlank() }?.take(1) ?: "阅",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "挂起 · 点击阅读",
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
