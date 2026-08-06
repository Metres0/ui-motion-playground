package com.example.uiplayground

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring

/**
 * 统一动效 Token —— 对应仓库根目录 `motion-tokens.md` 的四张表。
 *
 * 改动规范必须改 motion-tokens.md，这里只做翻译，两处要保持一致。
 */
object MotionTokens {

    // ── 1. 时长 ────────────────────────────────────────────────
    object Duration {
        const val Instant = 0
        const val Micro = 100
        const val Fast = 180
        const val Base = 220
        const val Enter = 350 // 页面进入
        const val Exit = 90   // 页面退出（必须比进入快）
        const val Expansive = 500
    }

    // ── 2. 缓动 ────────────────────────────────────────────────
    object Easing {
        /** cubic-bezier(0.4, 0, 0.2, 1) */
        val Standard = FastOutSlowInEasing

        /** cubic-bezier(0.2, 0, 0, 1) —— Material 3 强调曲线，页面转场首选 */
        val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** cubic-bezier(0, 0, 0.2, 1) —— 入场减速 */
        val Decelerate = LinearOutSlowInEasing

        /** cubic-bezier(0.4, 0, 1, 1) —— 出场加速 */
        val Accelerate = FastOutLinearInEasing

        /** 弹性回弹，仅用于微交互 */
        fun spring(bouncy: Boolean = false) =
            if (bouncy) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
            else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    }

    // ── 3. 位移 ────────────────────────────────────────────────
    object Space {
        const val Micro = 4f   // dp / px
        const val Small = 8f
        const val Page = 56f   // 页面级水平推入
    }

    // ── 4. 遮罩 ────────────────────────────────────────────────
    object Overlay {
        const val ScrimLight = 0.12f
        const val ScrimDark = 0.40f
        const val BlurRadius = 24f // dp
    }

    // ── 标准动画速记 ───────────────────────────────────────────
    /** 页面进入（列表→详情）：350ms + emphasized */
    fun pageEnter() = tween<Float>(Duration.Enter, easing = Easing.Emphasized)

    /** 页面退出：90ms + emphasized（加速离场） */
    fun pageExit() = tween<Float>(Duration.Exit, easing = Easing.Emphasized)

    /** 微交互：180ms + standard */
    fun micro() = tween<Float>(Duration.Fast, easing = Easing.Standard)

    /** 弹层：300ms + emphasized */
    fun sheet() = tween<Float>(Duration.Base + 80, easing = Easing.Emphasized)
}
