package com.example.feedlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.ui.AppNav
import com.example.feedlite.ui.theme.FeedLiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ★ v1.28：沉浸式全屏——隐藏状态栏，下滑手势可临时唤出
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())

        // ★ v1.32：依赖全部来自进程级容器（配置变更不重建，缓存/在途请求不丢）
        val container = (application as FeedLiteApp).container

        setContent {
            // ★ 深色模式：跟随系统 / 浅色 / 深色
            val themeMode by container.themeSettings.mode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeSettings.MODE_LIGHT -> false
                ThemeSettings.MODE_DARK -> true
                else -> isSystemInDarkTheme()
            }

            FeedLiteTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav(container)
                }
            }
        }
    }
}
