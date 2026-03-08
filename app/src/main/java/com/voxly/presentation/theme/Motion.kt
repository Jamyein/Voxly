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
 * Animation parameters align with Material 3 1.5.0+ MotionScheme specifications.
 * The MotionScheme is applied via MaterialTheme in Theme.kt.
 *
 * MotionScheme.expressive() parameters:
 * - Emphasized: dampingRatio ~0.72, stiffness ~400
 * - Standard: dampingRatio ~1.0, stiffness ~400
 */

// ============================================================================
// Backward Compatibility: Motion Tokens
// ============================================================================

@Stable
object ExpressiveMotionTokens {
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

    val ExpressiveEasing = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
    val StandardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val LegacyFastOutSlowIn = FastOutSlowInEasing
    val M3E_Emphasized_Easing = CubicBezierEasing(0.05f, 0f, 0.133f, 0.167f)
    val M3E_Emphasized_Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val EmphasizedDecelerate = SpringSpec(0.72f, 400f)
    val EmphasizedAccelerate = SpringSpec(0.72f, 400f)
    val Emphasized = SpringSpec(0.75f, 450f)
    val PageTransition = SpringSpec(0.78f, 300f)
    val BottomNavTransition = SpringSpec(0.82f, 350f)
    val StandardDecelerate = SpringSpec(1.0f, 400f)
    val StandardAccelerate = SpringSpec(1.0f, 400f)
    val Standard = SpringSpec(1.0f, 450f)
}

data class SpringSpec(val dampingRatio: Float, val stiffness: Float)

// ============================================================================
// Backward Compatibility: ExpressiveMotion
// ============================================================================

@Stable
object ExpressiveMotion {
    const val ShortDuration = 150
    const val MediumDuration = 300
    const val LongDuration = 500
    const val DampingRatioMediumBouncy = Spring.DampingRatioMediumBouncy
    const val DampingRatioNoBouncy = Spring.DampingRatioNoBouncy
    const val DampingRatioLowBouncy = Spring.DampingRatioLowBouncy
    const val StiffnessMedium = Spring.StiffnessMedium
    const val StiffnessMediumLow = Spring.StiffnessMediumLow
    const val StiffnessLow = Spring.StiffnessLow

    val EmphasizedSpring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioMediumBouncy, stiffness = StiffnessMedium)
    val StandardSpring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioNoBouncy, stiffness = StiffnessMediumLow)
    val ResponsiveSpring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioLowBouncy, stiffness = StiffnessLow)
    val ShortSpring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    val MediumSpring: AnimationSpec<Float> = spring(dampingRatio = DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val ExpressiveEasing = FastOutSlowInEasing
    val StandardEasing = spring<Float>(dampingRatio = DampingRatioMediumBouncy, stiffness = StiffnessMediumLow)
}

// ============================================================================
// ExpressiveAnimations
// Using tween with easing parameters that match MotionScheme.expressive()
// ============================================================================

object ExpressiveAnimations {
    // Duration constants matching MotionScheme
    private const val EmphasizedEnterDuration = 400
    private const val EmphasizedExitDuration = 300
    private const val StandardDuration = 350
    private const val QuickDuration = 200

    // Easing matching MotionScheme.expressive() specifications
    private val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0f, 0.133f, 0.167f)
    private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val StandardEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1.0f)

    // ===== Enter Animations =====

    val ListItemEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val CardExpand = expandVertically(animationSpec = tween(StandardDuration, easing = StandardEasing))

    val FabEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(QuickDuration))

    val FabExit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(QuickDuration))

    val DialogEnter = slideInVertically(
        initialOffsetY = { (it * 0.25).toInt() },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(QuickDuration))

    val PageEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val PageEnterExpressive = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val PageExitExpressive = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing))

    val PageEnterFromLeft = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val PageExitToRight = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing))

    val BottomNavSlideEnter = slideInVertically(
        initialOffsetY = { (it * 0.3).toInt() },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val BottomNavSlideExit = fadeOut(animationSpec = tween(QuickDuration))
    val CrossFadeEnter = fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))
    val CrossFadeExit = fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing))

    val PageEnterWithScale = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    )

    val PageExitWithScale = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    )

    val SharedAxisEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val SharedAxisExit = fadeOut(animationSpec = tween(QuickDuration))
    val SharedAxisPopEnter = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))
    val SharedAxisPopExit = fadeOut(animationSpec = tween(QuickDuration))

    // ===== M3E 规范页面过渡动画 =====

    val M3E_PageExit = fadeOut(
        animationSpec = tween(300, easing = ExpressiveMotionTokens.M3E_Emphasized_Accelerate)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(300, easing = ExpressiveMotionTokens.M3E_Emphasized_Accelerate)
    )

    val M3E_PopExit = slideOutVertically(
        targetOffsetY = { it / 10 },
        animationSpec = tween(300, easing = ExpressiveMotionTokens.M3E_Emphasized_Accelerate)
    ) + fadeOut(
        animationSpec = tween(300, easing = ExpressiveMotionTokens.M3E_Emphasized_Accelerate)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(300, easing = ExpressiveMotionTokens.M3E_Emphasized_Accelerate)
    )

    // ===== Navigation Transition Animations =====

    val SlideInHorizontallyInitialOffsetForward = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val SlideOutHorizontallyInitialOffsetForward = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing))

    val SlideInHorizontallyInitialOffsetBackward = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    ) + fadeIn(animationSpec = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing))

    val SlideOutHorizontallyInitialOffsetBackward = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing))

    // ===== Exit Animations =====

    val ListItemExit = slideOutVertically(
        targetOffsetY = { -it },
        animationSpec = tween(EmphasizedExitDuration, easing = EmphasizedAccelerateEasing)
    ) + fadeOut(animationSpec = tween(QuickDuration))

    val CardCollapse = shrinkVertically(animationSpec = tween(StandardDuration, easing = StandardEasing))
    val PageExit = fadeOut(animationSpec = tween(QuickDuration))

    // ===== State Change Animations =====

    val SelectionChange: AnimationSpec<Float> = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    val ValueChange: AnimationSpec<Float> = tween(EmphasizedEnterDuration, easing = EmphasizedDecelerateEasing)
    val ExpandCollapse: AnimationSpec<Float> = tween(StandardDuration, easing = StandardEasing)
}

// ============================================================================
// Animation Utilities
// ============================================================================

@Composable
fun rememberSpringAnimatedFloat(
    targetValue: Float,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Float = animateFloatAsState(
    targetValue = targetValue,
    animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
    label = "springAnimation"
).value

@Composable
fun rememberInfiniteTransition(label: String = "infiniteTransition") =
    androidx.compose.animation.core.rememberInfiniteTransition(label = label)

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
fun rememberShimmerOffset(width: Float, durationMillis: Int = 1200): Offset {
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
        animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness)
    )
)

@Composable
fun animatedAlpha(
    targetAlpha: Float,
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMedium
): Float = animateFloatAsState(
    targetValue = targetAlpha,
    animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
    label = "alphaAnimation"
).value

@Composable
fun animatedTranslationY(
    targetTranslation: Dp,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Dp {
    val animatedValue: Float by animateFloatAsState(
        targetValue = targetTranslation.value,
        animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
        label = "translationYAnimation"
    )
    return animatedValue.dp
}

// ============================================================================
// Motion Presets
// ============================================================================

object MotionPresets {
    private val QuickDuration = 200
    private val StandardDuration = 350

    val FadeIn: AnimationSpec<Float> = tween(QuickDuration)
    val SlideInUp: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = ExpressiveMotion.StiffnessMedium)
    val ScaleIn: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = ExpressiveMotion.StiffnessMedium)
    val FadeOut: AnimationSpec<Float> = tween(QuickDuration)
    val SlideOutDown: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = ExpressiveMotion.StiffnessMedium)
    val ScaleOut: AnimationSpec<Float> = tween(QuickDuration)
    val StateChange: AnimationSpec<Float> = spring(dampingRatio = ExpressiveMotion.DampingRatioNoBouncy, stiffness = ExpressiveMotion.StiffnessMediumLow)
    val Emphasis: AnimationSpec<Float> = spring(dampingRatio = ExpressiveMotion.DampingRatioLowBouncy, stiffness = ExpressiveMotion.StiffnessLow)
}

// ============================================================================
// Motion Logging
// ============================================================================

object MotionLogger {
    const val TAG = "Motion"
    fun logMotionEvent(event: String, details: String = "") { }
}
