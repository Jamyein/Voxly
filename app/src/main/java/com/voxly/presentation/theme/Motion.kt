package com.voxly.presentation.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Expressive Motion System
 * 
 * MD3 Expressive Motion特点：
 * 1. 基于物理的动画 - 使用Spring而非简单的tween
 * 2. Expressive动画 - 更有弹性和活力的动画
 * 3. Standard动画 - 标准的平滑动画
 * 4. 自定义Easing - 更流畅的曲线
 * 
 * Motion Tokens:
 * - Emphasized: 用于强调交互（按钮点击、选中状态）
 * - Standard: 用于一般过渡
 * - Legacy: 向后兼容的tween动画
 */

// ============================================================================
// Motion Tokens (M3 Expressive规范)
// ============================================================================

/**
 * Material 3 Expressive Motion Tokens
 * 
 * 基于Spring的物理动画参数
 */
@Stable
object ExpressiveMotionTokens {
    // ===== Duration Tokens =====
    const val Short1 = 75
    const val Short2 = 100
    const val Short3 = 150
    const val Short4 = 200
    
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600
    
    // ===== Spring Tokens =====
    // Emphasized - 用于强调的交互效果（更有弹性）
    val EmphasizedDecelerate = SpringSpec(
        dampingRatio = 0.72f,
        stiffness = 400f
    )

    val EmphasizedAccelerate = SpringSpec(
        dampingRatio = 0.72f,
        stiffness = 400f
    )

    val Emphasized = SpringSpec(
        dampingRatio = 0.75f,
        stiffness = 450f
    )

    // Page transition - 更流畅的弹簧参数
    val PageTransition = SpringSpec(
        dampingRatio = 0.78f,
        stiffness = 300f
    )

    // Bottom navigation - 更平滑的参数
    val BottomNavTransition = SpringSpec(
        dampingRatio = 0.82f,
        stiffness = 350f
    )
    
    // Standard - 用于标准过渡
    val StandardDecelerate = SpringSpec(
        dampingRatio = 1.0f,
        stiffness = 400f
    )
    
    val StandardAccelerate = SpringSpec(
        dampingRatio = 1.0f,
        stiffness = 400f
    )
    
    val Standard = SpringSpec(
        dampingRatio = 1.0f,
        stiffness = 450f
    )
    
    // Legacy Easing (向后兼容)
    val ExpressiveEasing = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
    val StandardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val LegacyFastOutSlowIn = FastOutSlowInEasing
}

/**
 * Spring规格数据类
 */
data class SpringSpec(
    val dampingRatio: Float,
    val stiffness: Float
)

// ============================================================================
// ExpressiveMotion Object (保留向后兼容)
// ============================================================================

@Stable
object ExpressiveMotion {
    // Duration constants
    const val ShortDuration = 150
    const val MediumDuration = 300
    const val LongDuration = 500
    
    // Spring damping ratios
    const val DampingRatioMediumBouncy = Spring.DampingRatioMediumBouncy
    const val DampingRatioNoBouncy = Spring.DampingRatioNoBouncy
    const val DampingRatioLowBouncy = Spring.DampingRatioLowBouncy
    
    // Spring stiffness
    const val StiffnessMedium = Spring.StiffnessMedium
    const val StiffnessMediumLow = Spring.StiffnessMediumLow
    const val StiffnessLow = Spring.StiffnessLow
    
    // Animation specs
    val EmphasizedSpring: AnimationSpec<Float> = spring(
        dampingRatio = DampingRatioMediumBouncy,
        stiffness = StiffnessMedium
    )
    
    val StandardSpring: AnimationSpec<Float> = spring(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMediumLow
    )
    
    val ResponsiveSpring: AnimationSpec<Float> = spring(
        dampingRatio = DampingRatioLowBouncy,
        stiffness = StiffnessLow
    )
    
    val ExpressiveEasing = FastOutSlowInEasing
    val StandardEasing = tween<Float>(durationMillis = MediumDuration, easing = FastOutSlowInEasing)
}

// ============================================================================
// Expressive Animations (可复用的动画效果)
// ============================================================================

/**
 * Material 3 Expressive动画预设
 * 用于常见的UI动画场景
 */
object ExpressiveAnimations {
    // ===== Enter Animations =====
    
    /** 列表项进入动画 - 带有弹性 */
    val ListItemEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
            stiffness = ExpressiveMotionTokens.Emphasized.stiffness
        )
    ) + fadeIn(animationSpec = tween(150))
    
    /** 卡片展开动画 - 标准平滑 */
    val CardExpand = expandVertically(
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
            stiffness = ExpressiveMotionTokens.Standard.stiffness
        )
    )
    
    /** FAB出现动画 - 强调弹性 */
    val FabEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.EmphasizedDecelerate.dampingRatio,
            stiffness = ExpressiveMotionTokens.EmphasizedDecelerate.stiffness
        )
    ) + fadeIn(animationSpec = tween(100))
    
    /** FAB退出动画 - 向上滑出+淡出 */
    val FabExit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.EmphasizedAccelerate.dampingRatio,
            stiffness = ExpressiveMotionTokens.EmphasizedAccelerate.stiffness
        )
    ) + fadeOut(animationSpec = tween(100))
    
    /** 对话框进入动画 - 缩放+淡入 */
    val DialogEnter = slideInVertically(
        initialOffsetY = { (it * 0.25).toInt() },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
            stiffness = ExpressiveMotionTokens.Emphasized.stiffness
        )
    ) + fadeIn(animationSpec = tween(100))
    
    /** 页面进入动画 - 滑动+淡入 */
    val PageEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
            stiffness = ExpressiveMotionTokens.Standard.stiffness
        )
    ) + fadeIn(animationSpec = tween(150))

    /** 页面进入动画 - 从右侧滑入 + 缩放 + 淡入 (更有表现力) */
    val PageEnterExpressive = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeIn(animationSpec = tween(200))

    /** 页面退出动画 - 向左滑出 + 缩放 + 淡出 */
    val PageExitExpressive = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeOut(animationSpec = tween(150))

    /** 页面进入动画 - 从左侧滑入 (用于返回导航) */
    val PageEnterFromLeft = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
            stiffness = ExpressiveMotionTokens.Standard.stiffness
        )
    ) + fadeIn(animationSpec = tween(200))

    /** 页面退出动画 - 向右滑出 (用于返回导航) */
    val PageExitToRight = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
            stiffness = ExpressiveMotionTokens.Standard.stiffness
        )
    ) + fadeOut(animationSpec = tween(150))

    /** 底部导航主页间切换 - 滑动+淡入 (更有表现力) */
    val BottomNavSlideEnter = slideInVertically(
        initialOffsetY = { (it * 0.3).toInt() },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.BottomNavTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.BottomNavTransition.stiffness
        )
    ) + fadeIn(animationSpec = tween(200))

    /** 底部导航主页间切换 - 向下滑出 */
    val BottomNavSlideExit = slideOutVertically(
        targetOffsetY = { (it * 0.3).toInt() },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.BottomNavTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.BottomNavTransition.stiffness
        )
    ) + fadeOut(animationSpec = tween(200))

    /** 底部导航主页间切换 - 交叉淡入淡出 (备用) */
    val CrossFadeEnter = fadeIn(animationSpec = tween(300))
    val CrossFadeExit = fadeOut(animationSpec = tween(300))

    /** 页面进入动画 - 带缩放效果 (更流畅) */
    val PageEnterWithScale = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeIn(animationSpec = tween(200)) + scaleIn(
        initialScale = 0.95f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    )

    /** 页面退出动画 - 带缩放效果 */
    val PageExitWithScale = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeOut(animationSpec = tween(150)) + scaleOut(
        targetScale = 0.95f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    )

    /** Shared Axis - 新页面从右滑入，旧页面同时向左轻微移动 */
    val SharedAxisEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeIn(animationSpec = tween(150))

    /** Shared Axis - 旧页面同时向右轻微移动 */
    val SharedAxisExit = slideOutHorizontally(
        targetOffsetX = { (it * 0.3).toInt() },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeOut(animationSpec = tween(150))

    /** Shared Axis - 返回时新页面从左滑入 */
    val SharedAxisPopEnter = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeIn(animationSpec = tween(150))

    /** Shared Axis - 返回时旧页面向右滑出 */
    val SharedAxisPopExit = slideOutHorizontally(
        targetOffsetX = { (it * 0.3).toInt() },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.PageTransition.dampingRatio,
            stiffness = ExpressiveMotionTokens.PageTransition.stiffness
        )
    ) + fadeOut(animationSpec = tween(150))
    
    // ===== Exit Animations =====
    
    /** 列表项退出动画 */
    val ListItemExit = slideOutVertically(
        targetOffsetY = { -it },
        animationSpec = tween(150)
    ) + fadeOut(animationSpec = tween(100))
    
    /** 卡片收起动画 */
    val CardCollapse = shrinkVertically(
        animationSpec = tween(200)
    )
    
    /** 页面退出动画 */
    val PageExit = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
            stiffness = ExpressiveMotionTokens.Standard.stiffness
        )
    ) + fadeOut(animationSpec = tween(100))
    
    // ===== State Change Animations =====
    
    /** 选中状态变化 - 弹性效果 */
    val SelectionChange: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
    )
    
    /** 值变化 - 平滑过渡 */
    val ValueChange = tween<Float>(durationMillis = 200)
    
    /** 展开/收起 - 带弹性 */
    val ExpandCollapse: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.Standard.dampingRatio,
        stiffness = ExpressiveMotionTokens.Standard.stiffness
    )
}

// ============================================================================
// Animation Utilities
// ============================================================================

@Composable
fun rememberSpringAnimatedFloat(
    targetValue: Float,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Float {
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        ),
        label = "springAnimation"
    ).value
}

@Composable
fun rememberInfiniteTransition(
    label: String = "infiniteTransition"
): androidx.compose.animation.core.InfiniteTransition {
    return androidx.compose.animation.core.rememberInfiniteTransition(label = label)
}

@Composable
fun rememberPulseScale(
    initialScale: Float = 1f,
    pulsedScale: Float = 1.05f,
    durationMillis: Int = 1000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    return infiniteTransition.animateFloat(
        initialValue = initialScale,
        targetValue = pulsedScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    ).value
}

@Composable
fun rememberShimmerOffset(
    width: Float,
    durationMillis: Int = 1200
): Offset {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    return Offset(
        x = infiniteTransition.animateFloat(
            initialValue = -width,
            targetValue = width * 2,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerX"
        ).value,
        y = 0f
    )
}

// ============================================================================
// Motion Modifiers
// ============================================================================

@Composable
fun Modifier.animateScale(
    targetScale: Float,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Modifier = this.then(
    Modifier.animateContentSize(
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
    )
)

@Composable
fun animatedAlpha(
    targetAlpha: Float,
    durationMillis: Int = ExpressiveMotion.MediumDuration
): Float {
    return animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = durationMillis),
        label = "alphaAnimation"
    ).value
}

@Composable
fun animatedTranslationY(
    targetTranslation: Dp,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Dp {
    val animatedValue: Float by animateFloatAsState(
        targetValue = targetTranslation.value,
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        ),
        label = "translationYAnimation"
    )
    
    return animatedValue.dp
}

// ============================================================================
// Motion Presets (Backward Compatibility)
// ============================================================================

object MotionPresets {
    val FadeIn: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    val SlideInUp: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.MediumDuration, easing = FastOutSlowInEasing)
    val ScaleIn: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioMediumBouncy,
        stiffness = ExpressiveMotion.StiffnessMedium
    )
    
    val FadeOut: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    val SlideOutDown: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.MediumDuration, easing = FastOutSlowInEasing)
    val ScaleOut: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    
    val StateChange: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioNoBouncy,
        stiffness = ExpressiveMotion.StiffnessMediumLow
    )
    
    val Emphasis: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioLowBouncy,
        stiffness = ExpressiveMotion.StiffnessLow
    )
}

// ============================================================================
// Motion Logging
// ============================================================================

object MotionLogger {
    const val TAG = "Motion"
    
    fun logMotionEvent(event: String, details: String = "") {
        // Timber.d("$TAG: $event - $details")
    }
}
