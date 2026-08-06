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
import com.example.feedlite.ui.articles.ArticleListScreen
import com.example.feedlite.ui.detail.ArticleDetailScreen
import com.example.feedlite.ui.feeds.FeedListScreen

/**
 * 导航编排：三个页面 + 统一转场 token（进 350ms / 出 90ms，emphasized，返回镜像）。
 *
 * 路由：
 * - feeds                    订阅管理
 * - articles/{sourceId}      文章列表（先 5 篇 + 加载更多）
 * - article/{itemKey}        文章详情（itemKey 经 Uri.encode 安全传递）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(store: SubscriptionStore, repository: RssRepository) {
    val nav = rememberNavController()

    SharedTransitionLayout {
        NavHost(
            navController = nav,
            startDestination = "feeds",
            enterTransition = { fadeIn(MotionTokens.pageEnter()) },
            exitTransition = { fadeOut(MotionTokens.pageExit()) },
            popEnterTransition = { fadeIn(MotionTokens.pageEnter()) },
            popExitTransition = { fadeOut(MotionTokens.pageExit()) },
        ) {
            composable("feeds") {
                val scope = this
                FeedListScreen(
                    store = store,
                    animatedVisibilityScope = scope,
                    onOpenSource = { source: FeedSource ->
                        nav.navigate("articles/${Uri.encode(source.id)}")
                    },
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
                    animatedVisibilityScope = scope,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
