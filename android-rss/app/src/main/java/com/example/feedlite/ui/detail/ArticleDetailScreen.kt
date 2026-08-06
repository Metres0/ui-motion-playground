package com.example.feedlite.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.HtmlText
import com.example.feedlite.data.RssItem
import com.example.feedlite.data.Translator
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.ui.ArticleCache
import com.example.feedlite.ui.components.ProgressiveImage

/**
 * 文章详情页：大图 + 正文 + AI 翻译。
 * - 大图与首页/列表缩略图共享 `thumb_{key}` 转场；
 * - 翻译：顶栏按钮调用 OpenAI 兼容接口，译文展示在正文下方，可切换原文/译文。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.ArticleDetailScreen(
    itemKey: String,
    translator: Translator,
    store: TranslationStore,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val item = remember(itemKey) { ArticleCache.get(itemKey) }
    val context = LocalContext.current

    val viewModel: ArticleDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ArticleDetailViewModel(itemKey, translator, store) }
        }
    )
    val translation by viewModel.translation.collectAsState()

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
                    // 翻译按钮
                    IconButton(onClick = {
                        if (item != null) viewModel.translate(HtmlText.toPlainText(item.descriptionHtml))
                    }) {
                        Icon(Icons.Default.Translate, contentDescription = "翻译全文")
                    }
                    if (item != null && item.link.isNotBlank()) {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                        }) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("文章不存在，请返回重试", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val plainBody = remember(item.descriptionHtml) { HtmlText.toPlainText(item.descriptionHtml) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 大图（详情用 1280px 解码，比缩略图清晰）
            ProgressiveImage(
                url = item.imageUrl,
                seed = item.key.hashCode(),
                contentDescription = item.title,
                decodeWidth = 1280,
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
                    text = plainBody.ifBlank { "（该源未提供正文摘要）" },
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                )

                // ── 翻译区块 ──────────────────────────────
                TranslationSection(
                    state = translation,
                    onRetry = { viewModel.translate(plainBody) },
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

/** 译文区块：Idle 显示引导，Translating 显示进度，Done 显示译文，Error 显示提示。 */
@Composable
private fun TranslationSection(
    state: TranslationUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        TranslationUiState.Idle -> {
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "点击右上角「翻译」按钮翻译全文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TranslationUiState.Translating -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在翻译…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        is TranslationUiState.Done -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Text("译文", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
            )
        }

        is TranslationUiState.Error -> {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(Modifier.padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}
