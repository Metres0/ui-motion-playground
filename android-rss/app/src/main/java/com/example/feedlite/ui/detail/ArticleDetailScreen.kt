package com.example.feedlite.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feedlite.data.HtmlText
import com.example.feedlite.data.RssItem
import com.example.feedlite.ui.ArticleCache
import com.example.feedlite.ui.components.ProgressiveImage

/**
 * 文章详情页：从 [ArticleCache] 按 key 取文章。
 * 大图与列表缩略图共享 `thumb_{key}`，转场时从卡片位平滑放大。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.ArticleDetailScreen(
    itemKey: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val item = remember(itemKey) { ArticleCache.get(itemKey) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (item != null && item.link.isNotBlank()) {
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                            },
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "在浏览器打开")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("文章不存在，请返回重试", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 大图（与列表缩略图共享 key → 平滑放大转场）
            ProgressiveImage(
                url = item.imageUrl,
                seed = item.key.hashCode(),
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .sharedElement(
                        state = rememberSharedContentState(key = "thumb_${item.key}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
            )
            Column(Modifier.padding(16.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = listOf(item.author, item.pubDate).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = HtmlText.toPlainText(item.descriptionHtml).ifBlank { "（该源未提供正文摘要）" },
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(24.dp))
                if (item.link.isNotBlank()) {
                    Text(
                        text = "原文链接：${item.link}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
