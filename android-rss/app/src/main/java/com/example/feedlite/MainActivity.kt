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
import com.example.feedlite.data.ArticleFetcher
import java.io.File
import com.example.feedlite.data.FullTextCache
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.UpdateSettings
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

        val store = SubscriptionStore(applicationContext)
        val repository = RssRepository(applicationContext)
        val translationStore = TranslationStore(applicationContext)
        val translator = Translator(
            translationStore,
            File(filesDir, "translations"),
        )
        val updateSettings = UpdateSettings(applicationContext)
        val fetcher = ArticleFetcher(FullTextCache(applicationContext))
        val readingState = ReadingStateStore(applicationContext)
        val themeSettings = ThemeSettings(applicationContext)

        setContent {
            // ★ 深色模式：跟随系统 / 浅色 / 深色
            val themeMode by themeSettings.mode.collectAsState()
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
                    AppNav(
                        store = store,
                        repository = repository,
                        translator = translator,
                        translationStore = translationStore,
                        updateSettings = updateSettings,
                        fetcher = fetcher,
                        readingState = readingState,
                        themeSettings = themeSettings,
                    )
                }
            }
        }
    }
}
