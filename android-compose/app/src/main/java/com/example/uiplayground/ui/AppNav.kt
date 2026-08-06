package com.example.uiplayground.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.uiplayground.MotionTokens
import com.example.uiplayground.data.ArticleRepository
import com.example.uiplayground.data.PrefetchRouter
import com.example.uiplayground.ui.detail.DetailScreen
import com.example.uiplayground.ui.list.ListScreen

/**
 * 导航编排层 —— 演示三件事：
 * 1. 统一转场 token：进出场时长不对称（进 350ms / 出 90ms），均用 emphasized 曲线；
 * 2. L1 路由预取：点击瞬间 `PrefetchRouter.intercept` 先发请求，再 navigate；
 * 3. 共享元素：`SharedTransitionLayout` 包裹 NavHost，列表与详情共用 `cover_{id}` key。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(
    repository: ArticleRepository,
    prefetchRouter: PrefetchRouter,
) {
    val navController = rememberNavController()

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = "list",
            // NavHost 级兜底转场（token 表 §5 淡入淡出）
            enterTransition = { fadeIn(MotionTokens.pageEnter()) },
            exitTransition = { fadeOut(MotionTokens.pageExit()) },
            popEnterTransition = { fadeIn(MotionTokens.pageEnter()) },
            popExitTransition = { fadeOut(MotionTokens.pageExit()) },
        ) {
            composable("list") {
                val scope = this
                ListScreen(
                    repository = repository,
                    animatedVisibilityScope = scope,
                    onArticleClick = { id ->
                        // ★ 点击瞬间先预取，再导航：350ms 转场掩盖剩余加载时间
                        prefetchRouter.intercept("detail", mapOf("id" to id))
                        navController.navigate("detail/$id")
                    },
                )
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                // 进入：水平右入左出（与返回镜像）+ 淡入
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(
                            MotionTokens.Duration.Enter,
                            easing = MotionTokens.Easing.Emphasized,
                        ),
                    ) + fadeIn(MotionTokens.pageEnter())
                },
                // 返回：快速右出 + 淡出（exit 必须快于 enter）
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(
                            MotionTokens.Duration.Exit,
                            easing = MotionTokens.Easing.Emphasized,
                        ),
                    ) + fadeOut(MotionTokens.pageExit())
                },
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: return@composable
                val scope = this
                DetailScreen(
                    articleId = id,
                    repository = repository,
                    animatedVisibilityScope = scope,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
