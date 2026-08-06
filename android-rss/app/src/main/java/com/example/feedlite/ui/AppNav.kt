package com.example.feedlite.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feedlite.AppContainer
import com.example.feedlite.MotionTokens
import com.example.feedlite.R
import com.example.feedlite.data.FeedSource
import com.example.feedlite.ui.later.ReadLaterScreen
import com.example.feedlite.ui.starred.StarredScreen
import com.example.feedlite.ui.articles.ArticleListScreen
import com.example.feedlite.ui.detail.ArticleDetailScreen
import com.example.feedlite.ui.home.HomeScreen
import com.example.feedlite.ui.settings.SettingsScreen
import com.example.feedlite.ui.sources.SourceManageScreen

/**
 * 导航编排（v1.14；v1.32：容器化 + 转场收敛）。
 *
 * - 底部导航栏：首页 / 收藏 / 稍后再看 三个 Tab
 * - articles/{sourceId} 单源文章列表
 * - article/{itemKey}   文章详情（共享元素 + 翻译）
 * - settings            设置
 *
 * 统一转场 token：进 350ms / 出 90ms，emphasized，返回镜像。
 * 依赖一律来自 [AppContainer]，不再 8 参透传。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(container: AppContainer) {
    val store = container.subscriptionStore
    val repository = container.repository
    val translator = container.translator
    val translationStore = container.translationStore
    val updateSettings = container.updateSettings
    val fetcher = container.fetcher
    val readingState = container.readingState
    val themeSettings = container.themeSettings

    val nav = rememberNavController()
    // ★ 首页下拉时隐藏底部栏（仅首页滚动触发）
    var showHomeBar by remember { mutableStateOf(true) }
    // ★ v1.23：设置页改订阅后，返回首页触发刷新
    var homeRefreshTick by remember { mutableStateOf(0) }

    SharedTransitionLayout {
        // ★ 底部导航栏（首页 / 收藏 / 稍后再看 / 设置）
        // ★ v1.27：contentWindowInsets=0 去掉 Scaffold 默认状态栏 padding，
        //   避免与各页面自身的 windowInsetsPadding(statusBars) 叠加成双倍顶部空白
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination
                // ★ 详情页自动隐藏；首页在滚动下拉时隐藏
                val showBar = current?.route?.startsWith("article") != true &&
                    (current?.route != "home" || showHomeBar)
                if (showBar) {
                NavigationBar {
                    val items = listOf(
                        Triple("home", R.drawable.ic_nav_home, R.string.nav_home),
                        Triple("starred", R.drawable.ic_nav_star, R.string.nav_starred),
                        Triple("later", R.drawable.ic_nav_bookmark, R.string.nav_later),
                        Triple("sources", R.drawable.ic_nav_source, R.string.nav_sources),
                        Triple("settings", R.drawable.ic_nav_settings, R.string.nav_settings),
                    )
                    items.forEach { (route, iconRes, label) ->
                        val selected = current?.hierarchy?.any { it.route == route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                // ★ iOS 透明图标：靠 tint 着色，选中=品牌色，未选中=默认灰
                                Icon(
                                    painterResource(iconRes),
                                    contentDescription = stringResource(label), // 无障碍标签，不显示文字
                                    tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            },
                            label = {}, // ★ v1.31：底部导航只保留图标，去掉文字
                        )
                    }
                }
                }
            },
        ) { padding ->
            NavHost(
                modifier = Modifier.padding(padding),
                navController = nav,
                startDestination = "home",
                enterTransition = { fadeIn(MotionTokens.pageEnter()) },
                exitTransition = { fadeOut(MotionTokens.pageExit()) },
                popEnterTransition = { fadeIn(MotionTokens.pageEnter()) },
                popExitTransition = { fadeOut(MotionTokens.pageExit()) },
            ) {
            composable("home") {
                val scope = this
                HomeScreen(
                    repository = repository,
                    store = store,
                    updateSettings = updateSettings,
                    readingState = readingState,
                    animatedVisibilityScope = scope,
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                    refreshTick = homeRefreshTick,
                    onScrollVisibilityChange = { visible -> showHomeBar = visible },
                )
            }
            composable(
                route = "articles/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) { entry ->
                val sourceId = Uri.decode(entry.arguments?.getString("sourceId").orEmpty())
                val scope = this
                ArticleListScreen(
                    sourceId = sourceId,
                    repository = repository,
                    store = store,
                    updateSettings = updateSettings,
                    readingState = readingState,
                    animatedVisibilityScope = scope,
                    onBack = { nav.popBackStack() },
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                )
            }
            composable(
                route = "article/{itemKey}",
                arguments = listOf(navArgument("itemKey") { type = NavType.StringType }),
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) { entry ->
                val itemKey = Uri.decode(entry.arguments?.getString("itemKey").orEmpty())
                val scope = this
                ArticleDetailScreen(
                    itemKey = itemKey,
                    translator = translator,
                    store = translationStore,
                    fetcher = fetcher,
                    readingState = readingState,
                    animatedVisibilityScope = scope,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = "settings",
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) {
                SettingsScreen(
                    store = translationStore,
                    translator = translator,
                    updateSettings = updateSettings,
                    themeSettings = themeSettings,
                    subscriptionStore = store,
                    readingState = readingState,
                    onBack = { nav.popBackStack() },
                )
            }
            // ★ v1.30：源管理升级为底部导航 Tab（asTab 模式无返回行）
            composable(
                route = "sources",
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) {
                SourceManageScreen(
                    store = store,
                    asTab = true,
                    onOpenSource = { source: FeedSource ->
                        nav.navigate("articles/${Uri.encode(source.id)}")
                    },
                    onSubscriptionChanged = { homeRefreshTick++ },
                )
            }
            composable(
                route = "starred",
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) {
                val scope = this
                StarredScreen(
                    readingState = readingState,
                    animatedVisibilityScope = scope,
                    onBack = { nav.popBackStack() },
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                )
            }
            composable(
                route = "later",
                enterTransition = { slideEnter() },
                popExitTransition = { slideExit() },
            ) {
                val scope = this
                ReadLaterScreen(
                    readingState = readingState,
                    animatedVisibilityScope = scope,
                    onBack = { nav.popBackStack() },
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                )
            }
            }
        }
    }
}

/** 页面进入转场：从右往左推入 350ms + 淡入（token 收敛，杜绝 5 份拷贝）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideEnter() =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
    ) + fadeIn(MotionTokens.pageEnter())

/** 页面退出转场：向右滑出 90ms + 淡出（返回镜像）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideExit() =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
    ) + fadeOut(MotionTokens.pageExit())
