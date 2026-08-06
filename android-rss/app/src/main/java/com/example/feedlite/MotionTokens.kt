package com.example.feedlite

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 统一动效 Token —— 与仓库根目录 motion-tokens.md 四张表严格一致。
 * 页面转场 = 进 350ms / 出 90ms + emphasized 曲线；stagger 30ms/项。
 */
object MotionTokens {

    object Duration {
        const val Instant = 0
        const val Micro = 100
        const val Fast = 180
        const val Base = 220
        const val Enter = 350
        const val Exit = 90
        const val Expansive = 500
    }

    object Easing {
        val Standard = FastOutSlowInEasing
        val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Decelerate = LinearOutSlowInEasing
        val Accelerate = FastOutLinearInEasing
        fun spring(bouncy: Boolean = false): SpringSpec<Float> =
            if (bouncy) spring(dampingRatio = 0.5f, stiffness = 500f)
            else spring(dampingRatio = 0.8f, stiffness = 350f)
    }

    object Space {
        const val Small = 8f
        const val Page = 56f
    }

    object Overlay {
        const val ScrimLight = 0.12f
        const val ScrimDark = 0.40f
        const val BlurRadius = 24f
    }

    fun pageEnter() = tween<Float>(Duration.Enter, easing = Easing.Emphasized)
    fun pageExit() = tween<Float>(Duration.Exit, easing = Easing.Emphasized)
    fun micro() = tween<Float>(Duration.Fast, easing = Easing.Standard)
}
