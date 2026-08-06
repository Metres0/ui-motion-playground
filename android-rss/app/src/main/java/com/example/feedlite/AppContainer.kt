package com.example.feedlite

import android.content.Context
import com.example.feedlite.data.ArticleFetcher
import com.example.feedlite.data.FullTextCache
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.UpdateSettings
import java.io.File

/**
 * 应用级依赖容器（v1.32：手写最小 DI）。
 *
 * 替代 MainActivity 里逐个构造 + 8 参透传：所有依赖进程级单例一次创建，
 * 配置变更（旋转/字体缩放）时不再重建，预取缓存与在途请求不丢。
 * 规模再大可平滑替换为 Hilt/Koin。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val subscriptionStore = SubscriptionStore(appContext)
    val repository = RssRepository(appContext)
    val translationStore = TranslationStore(appContext)
    val translator = Translator(translationStore, File(appContext.filesDir, "translations"))
    val updateSettings = UpdateSettings(appContext)
    val fetcher = ArticleFetcher(FullTextCache(appContext))
    val readingState = ReadingStateStore(appContext)
    val themeSettings = ThemeSettings(appContext)
}
