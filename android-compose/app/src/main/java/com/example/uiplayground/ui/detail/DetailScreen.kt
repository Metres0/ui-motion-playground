package com.example.uiplayground.ui.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.animation.sharedElement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.uiplayground.data.ArticleRepository
import com.example.uiplayground.ui.components.ProgressiveImage

/**
 * 详情页 —— 演示「共享元素终点 + 渐进式大图 + 骨架屏 + 预取命中标记」。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.DetailScreen(
    articleId: Long,
    repository: ArticleRepository,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        factory = viewModelFactory { initializer { DetailViewModel(repository, articleId) } }
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> DetailSkeleton(Modifier.padding(padding))

            is DetailUiState.Error -> DetailError(
                modifier = Modifier.padding(padding),
                onRetry = viewModel::load,
            )

            is DetailUiState.Success -> {
                val detail = s.detail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 与列表页封面共享 key → 转场时大图从卡片位置平滑放大
                    ProgressiveImage(
                        url = detail.article.coverUrl,
                        seed = detail.article.seed,
                        contentDescription = detail.article.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 2f)
                            .sharedElement(
                                state = rememberSharedContentState(key = "cover_${detail.article.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                    )
                    Column(Modifier.padding(16.dp)) {
                        if (s.prefetched) {
                            PrefetchBadge()
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(detail.article.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${detail.article.subtitle} · 阅读约 ${detail.readTimeMin} 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = detail.body,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 26.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 预取命中徽章：路由预取生效的直接可视化证明。 */
@Composable
private fun PrefetchBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "预取命中 · 秒开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 骨架屏：shimmer 脉冲 + 与真实布局一致的占位块（研究报告 §3.4）。 */
@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "skeleton",
    )
    val block = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .graphicsLayer { alpha = pulse }

    Column(modifier.padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(16.dp))
        Box(block.fillMaxWidth(0.6f).height(24.dp))
        Spacer(Modifier.height(12.dp))
        Box(block.fillMaxWidth(0.4f).height(14.dp))
        Spacer(Modifier.height(24.dp))
        repeat(6) {
            Box(block.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DetailError(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
