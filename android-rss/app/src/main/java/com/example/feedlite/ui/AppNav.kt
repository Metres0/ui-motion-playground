package com.example.feedlite.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feedlite.MotionTokens
import com.example.feedlite.R
import com.example.feedlite.data.ArticleFetcher
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.ReadingStateStore
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.ThemeSettings
import com.example.feedlite.data.Translator
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.UpdateSettings
import com.example.feedlite.ui.later.ReadLaterScreen
import com.example.feedlite.ui.starred.StarredScreen
import com.example.feedlite.ui.articles.ArticleListScreen
import com.example.feedlite.ui.detail.ArticleDetailScreen
import com.example.feedlite.ui.home.HomeScreen
import com.example.feedlite.ui.settings.SettingsScreen

/**
 * 导航编排（v1.14）：
 *
 * - 底部导航栏：首页 / 收藏 / 稍后再看 三个 Tab
 * - articles/{sourceId} 单源文章列表
 * - article/{itemKey}   文章详情（共享元素 + 翻译）
 * - settings            设置
 *
 * 统一转场 token：进 350ms / 出 90ms，emphasized，返回镜像。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(
    store: SubscriptionStore,
    repository: RssRepository,
    translator: Translator,
    translationStore: TranslationStore,
    updateSettings: UpdateSettings,
    fetcher: ArticleFetcher,
    readingState: ReadingStateStore,
    themeSettings: ThemeSettings,
) {
    val nav = rememberNavController()

    SharedTransitionLayout {
        // ★ 底部导航栏（首页 / 收藏 / 稍后再看）
        Scaffold(
            bottomBar = {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination
                NavigationBar {
                    val items = listOf(
                        Triple("home", R.drawable.ic_nav_home, "首页"),
                        Triple("starred", R.drawable.ic_nav_star, "收藏"),
                        Triple("later", R.drawable.ic_nav_bookmark, "稍后再看"),
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
                                Icon(
                                    painterResource(iconRes),
                                    contentDescription = label,
                                    tint = if (selected) Color.Unspecified else Color.Unspecified,
                                )
                            },
                            label = { Text(label) },
                        )
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
                    onOpenSource = { source: FeedSource ->
                        nav.navigate("articles/${Uri.encode(source.id)}")
                    },
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenStarred = { nav.navigate("starred") },
                    onOpenLater = { nav.navigate("later") },
                )
            }
            composable(
                route = "articles/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeOut(MotionTokens.pageExit())
                },
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
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeOut(MotionTokens.pageExit())
                },
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
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeOut(MotionTokens.pageExit())
                },
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
            composable(
                route = "starred",
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeOut(MotionTokens.pageExit())
                },
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
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(MotionTokens.Duration.Enter, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(MotionTokens.Duration.Exit, easing = MotionTokens.Easing.Emphasized),
                    ) + fadeOut(MotionTokens.pageExit())
                },
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
