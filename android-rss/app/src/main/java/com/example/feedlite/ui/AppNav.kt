package com.example.feedlite.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feedlite.MotionTokens
import com.example.feedlite.data.FeedSource
import com.example.feedlite.data.RssRepository
import com.example.feedlite.data.SubscriptionStore
import com.example.feedlite.data.Translator
import com.example.feedlite.data.TranslationStore
import com.example.feedlite.data.UpdateSettings
import com.example.feedlite.ui.articles.ArticleListScreen
import com.example.feedlite.ui.detail.ArticleDetailScreen
import com.example.feedlite.ui.home.HomeScreen
import com.example.feedlite.ui.settings.SettingsScreen

/**
 * 导航编排（v1.1）：
 *
 * - home                首页聚合流 + 侧边栏（源管理/设置/关于）
 * - articles/{sourceId} 单源文章列表（先 5 篇 + 加载更多）
 * - article/{itemKey}   文章详情（共享元素 + 翻译）
 * - settings            设置（翻译 API Key）
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
) {
    val nav = rememberNavController()

    SharedTransitionLayout {
        NavHost(
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
                    animatedVisibilityScope = scope,
                    onOpenSource = { source: FeedSource ->
                        nav.navigate("articles/${Uri.encode(source.id)}")
                    },
                    onOpenArticle = { item ->
                        ArticleCache.put(item.key, item)
                        nav.navigate("article/${Uri.encode(item.key)}")
                    },
                    onOpenSettings = { nav.navigate("settings") },
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
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
