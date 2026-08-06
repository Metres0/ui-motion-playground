package com.example.uiplayground

import android.app.Application
import com.example.uiplayground.data.ArticleRepository
import com.example.uiplayground.data.FakeArticleApi
import com.example.uiplayground.data.PrefetchRouter

/**
 * 应用级容器：数据层在此构造一次，与进程同寿。
 *
 * 问题背景：ArticleRepository / PrefetchRouter 若在 MainActivity.onCreate 里
 * 每次新建，配置变更（旋转屏幕等）会孤儿化旧的 CoroutineScope、清空预取缓存。
 * 放进 [AppContainer] 后，Activity 重建时仓库与缓存原样保留。
 *
 * 无 DI 框架：MainActivity 直接 `(applicationContext as PlaygroundApplication).container` 取用。
 */
class PlaygroundApplication : Application() {

    val container: AppContainer by lazy { AppContainer() }

    class AppContainer {
        val repository: ArticleRepository = ArticleRepository(FakeArticleApi)
        val prefetchRouter: PrefetchRouter = PrefetchRouter(repository)
    }
}
