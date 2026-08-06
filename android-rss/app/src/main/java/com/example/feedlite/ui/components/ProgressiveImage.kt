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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 渐进式图片：模糊色块占位 → 原图 300ms 淡入（token 表 §5「渐进式图片」）。
 * 无图时仅显示占位色块（文章封面常缺图，占位色让列表节奏稳定）。
 */
@Composable
fun ProgressiveImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Int = 0,
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
    val hue = ((seed * 47) % 360 + 360) % 360
    return Color.hsv(hue.toFloat(), 0.22f, 0.92f)
}
