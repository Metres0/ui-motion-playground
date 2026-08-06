package com.example.uiplayground.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.hsv
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * 渐进式图片组件 —— 模拟「BlurHash → 原图」的加载体验（研究报告 §3.2）。
 *
 * 加载流程：色块占位（高斯模糊）→ 网络完成 → 原图 Crossfade 淡入。
 * 用户「看到图片」的时间被大大提前，感知加载速度快于实际。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProgressiveImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Int = 0,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val placeholder = remember(seed) { placeholderColor(seed) }

    Box(modifier) {
        // 模糊占位层：图片未就绪时展示，就绪后保持不透明底色避免闪烁
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(placeholder)
                // 动画进行中不改变 blur 半径；这里只在 loaded 翻转瞬间切换一次
                .then(if (loaded) Modifier else Modifier.blur(24.dp))
        )

        // 原图层：加载完成后 alpha 置 1，叠加 Coil 自身 300ms crossfade
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(300) // 渐进式淡入 300ms（Coil 2.x API）
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

/** 基于 seed 生成稳定的柔和占位色（HSL 均匀分布，避免相邻卡片同色）。 */
private fun placeholderColor(seed: Int): Color {
    val hue = ((seed * 47) % 360 + 360) % 360
    return Color.hsv(hue.toFloat(), 0.22f, 0.92f)
}
