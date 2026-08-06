package com.example.feedlite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size

/**
 * 渐进式图片：模糊色块占位 → 原图 300ms 淡入。
 *
 * 图片优化策略（token 表 §5「渐进式图片」+ 研究报告 §3.2）：
 * - [decodeWidth] 按用途限制解码尺寸：列表缩略图 360px、详情大图 1280px，
 *   避免把原图全尺寸解码进内存（webp 原图可能 2~5MB，解码后浪费严重）；
 * - 只解码一次（Coil 内存缓存命中即不再解码）；
 * - URL 为空时仅显示占位色块。
 */
@Composable
fun ProgressiveImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Int = 0,
    decodeWidth: Int = 360, // 默认按缩略图解码；详情页传 1280
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val placeholder = remember(seed) { placeholderColor(seed) }

    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholder)
                .then(if (loaded) Modifier else Modifier.blur(24.dp))
        )
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(300)
                    .size(Size(decodeWidth, decodeWidth)) // 限制解码尺寸，节省内存
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (loaded) 1f else 0f },
                onSuccess = { loaded = true },
            )
        }
    }
}

private fun placeholderColor(seed: Int): Color {
    // 固定 seed 映射到柔和色调；对负数 seed 取绝对值避免极端色相
    val hue = (((seed.toLong() * 47) % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), 0.22f, 0.92f)
}
