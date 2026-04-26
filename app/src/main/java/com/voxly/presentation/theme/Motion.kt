@file:Suppress("DEPRECATION")

package com.voxly.presentation.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import timber.log.Timber

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
    // Duration tokens (milliseconds) - Material 3 Motion System
    const val Short1 = 50
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
    const val ExtraLong1 = 700
    const val ExtraLong2 = 800
    const val ExtraLong3 = 900
    const val ExtraLong4 = 1000

    // Easing curves - Material 3 Motion System
    val StandardInterpolator = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerateInterpolator = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerateInterpolator = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val EmphasizedInterpolator = CubicBezierEasing(0.05f, 0f, 0.133f, 0.167f)
    val EmphasizedDecelerateInterpolator = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerateInterpolator = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val LinearInterpolator = CubicBezierEasing(0f, 0f, 1f, 1f)

    // Predictive back gesture interpolator - M3 spec
    val PredictiveBackInterpolator = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    // Legacy easing for backward compatibility
    val ExpressiveEasing = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
    val StandardEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val LegacyFastOutSlowIn = FastOutSlowInEasing

    /**
     * Spring specs following Material 3 Motion System (v1.13.0+)
     * 
     * Spatial springs: damping=0.9 (slight bounce for movement)
     * Effects springs: damping=1.0 (no bounce for color/opacity)
     * 
     * Speed guide:
     * - Fast: Small components (switches, buttons) - stiffness 1400-3800
     * - Default: Partial screen (bottom sheets, drawers) - stiffness 700-1600
     * - Slow: Full screen transitions - stiffness 300-800
     */

    // Fast springs - small components
    val FastSpatial = SpringSpec(0.9f, 1400f)      // Small components like switches
    val FastEffects = SpringSpec(1.0f, 3800f)      // Color/opacity for small components

    // Default springs - partial screen animations
    val DefaultSpatial = SpringSpec(0.9f, 700f)    // Bottom sheets, nav drawers
    val DefaultEffects = SpringSpec(1.0f, 1600f)   // Color/opacity for partial screen

    // Slow springs - full screen transitions (primary for page navigation)
    val SlowSpatial = SpringSpec(0.85f, 450f)       // Optimized: higher stiffness for snappier transitions
    val SlowEffects = SpringSpec(1.0f, 1200f)       // Optimized: much higher stiffness for faster fade

    // Legacy specs (deprecated, use above)
    @Deprecated("Use SlowSpatial for page transitions")
    val PageTransition = SpringSpec(0.78f, 300f)
    @Deprecated("Use DefaultSpatial for bottom nav")
    val BottomNavTransition = SpringSpec(0.82f, 350f)
}

data class SpringSpec(val dampingRatio: Float, val stiffness: Float)

// ============================================================================
// Backward Compatibility: ExpressiveMotion
// ============================================================================

@Stable
object ExpressiveMotion {
    // Duration constants (milliseconds) - Material 3 Motion System
    const val ShortDuration = 150   // motionDurationShort3
    const val MediumDuration = 300  // motionDurationLong1
    const val LongDuration = 500    // motionDurationLong2

    // Legacy damping/stiffness constants (deprecated, use ExpressiveMotionTokens springs)
    @Deprecated("Use ExpressiveMotionTokens.SlowSpatial/FastSpatial etc.")
    const val DampingRatioMediumBouncy = Spring.DampingRatioMediumBouncy
    @Deprecated("Use ExpressiveMotionTokens.Effects springs with damping=1.0")
    const val DampingRatioNoBouncy = Spring.DampingRatioNoBouncy
    @Deprecated("Use ExpressiveMotionTokens.DefaultSpatial")
    const val DampingRatioLowBouncy = Spring.DampingRatioLowBouncy
    @Deprecated("Use specific stiffness from MotionTokens")
    const val StiffnessMedium = Spring.StiffnessMedium
    @Deprecated("Use specific stiffness from MotionTokens")
    const val StiffnessMediumLow = Spring.StiffnessMediumLow
    @Deprecated("Use specific stiffness from MotionTokens")
    const val StiffnessLow = Spring.StiffnessLow

    // ===== Material 3 Spring Specs (v1.13.0+) =====
    // Full screen transitions
    val SlowSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )
    val SlowEffectsSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowEffects.stiffness
    )

    // Partial screen animations
    val DefaultSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )
    val DefaultEffectsSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultEffects.stiffness
    )

    // Small component animations
    val FastSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.FastSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastSpatial.stiffness
    )
    val FastEffectsSpring: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastEffects.stiffness
    )

    // Legacy springs (deprecated, use above)
    @Deprecated("Use SlowSpring for page transitions", ReplaceWith("SlowSpring"))
    val EmphasizedSpring: AnimationSpec<Float> = SlowSpring
    @Deprecated("Use DefaultSpring for general use", ReplaceWith("DefaultSpring"))
    val StandardSpring: AnimationSpec<Float> = DefaultSpring
    @Deprecated("Use FastSpring for responsive interactions", ReplaceWith("FastSpring"))
    val ResponsiveSpring: AnimationSpec<Float> = FastSpring
    @Deprecated("Use FastSpring", ReplaceWith("FastSpring"))
    val ShortSpring: AnimationSpec<Float> = FastSpring
    @Deprecated("Use DefaultSpring", ReplaceWith("DefaultSpring"))
    val MediumSpring: AnimationSpec<Float> = DefaultSpring

    // IntSize type Spring - for animateContentSize
    val SlowSpringSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )
    val DefaultSpringSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )
    @Deprecated("Use SlowSpringSize or DefaultSpringSize", ReplaceWith("SlowSpringSize"))
    val EmphasizedSpringSize: FiniteAnimationSpec<IntSize> = SlowSpringSize
    @Deprecated("Use DefaultSpringSize", ReplaceWith("DefaultSpringSize"))
    val StandardSpringSize: FiniteAnimationSpec<IntSize> = DefaultSpringSize

    // Dp type Spring - for animateDpAsState
    val SlowSpringDp: AnimationSpec<Dp> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )
    @Deprecated("Use SlowSpringDp", ReplaceWith("SlowSpringDp"))
    val EmphasizedSpringDp: AnimationSpec<Dp> = SlowSpringDp

    // Color type Spring - for animateColorAsState
    val SlowSpringColor: AnimationSpec<Color> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowEffects.stiffness
    )
    val DefaultSpringColor: AnimationSpec<Color> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultEffects.stiffness
    )

    val ExpressiveEasing = FastOutSlowInEasing
    val StandardEasing = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )
}

// ============================================================================
// ExpressiveAnimations
// Using tween with easing parameters that match MotionScheme.expressive()
// ============================================================================

object ExpressiveAnimations {
    // Duration constants matching Material Motion System
    private const val ContainerEnterDuration = 300  // motionDurationLong1
    private const val ContainerExitDuration = 250   // motionDurationMedium2
    private const val FadeThroughDuration = 300     // motionDurationLong1
    private const val FadeEnterDuration = 150       // motionDurationShort3
    private const val FadeExitDuration = 75         // motionDurationShort1
    private const val QuickDuration = 200

    // ===== Material 3 Spring Specifications (v1.13.0+) =====
    // Full screen transitions - Slow springs
    private val PageEnterSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )

    private val PageExitSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )

    private val PageEffectsSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.SlowEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowEffects.stiffness
    )

    // Partial screen - Default springs
    private val PartialEnterSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )

    private val PartialExitSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )

    // ===== Spring animations - IntOffset type for slides =====
    private val PageEnterSpringSlide = spring<IntOffset>(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )

    private val PageExitSpringSlide = spring<IntOffset>(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )

    // ===== Material Motion Transitions (v1.13.0+) =====
    // Container Transform - for list item to detail page transitions
    // Combines slide (for spatial movement), scale, and fade for smooth expand/collapse feel
    val ContainerTransformEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { it / 5 },
            animationSpec = PageEnterSpringSlide
        ) +
        fadeIn(animationSpec = PageEffectsSpring) +
        scaleIn(
            initialScale = 0.95f,
            animationSpec = PageEnterSpring
        )

    val ContainerTransformExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -it / 5 },
            animationSpec = PageExitSpringSlide
        ) +
        fadeOut(animationSpec = PageEffectsSpring) +
        scaleOut(
            targetScale = 0.95f,
            animationSpec = PageExitSpring
        )

    // Container Transform Pop (return) - reverse of enter
    val ContainerTransformPopEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { -it / 5 },
            animationSpec = PageEnterSpringSlide
        ) +
        scaleIn(
            initialScale = 0.95f,
            animationSpec = PageEnterSpring
        ) +
        fadeIn(animationSpec = PageEffectsSpring)

    val ContainerTransformPopExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it / 5 },
            animationSpec = PageExitSpringSlide
        ) +
        fadeOut(animationSpec = PageEffectsSpring) +
        scaleOut(
            targetScale = 0.95f,
            animationSpec = PageExitSpring
        )

    // Predictive Back - optimized for gesture-driven animation
    // Uses CubicBezierEasing(0.1f, 0.1f, 0f, 1f) per M3 spec for natural deceleration
    private val PredictiveBackTween = tween<Float>(
        durationMillis = 400,
        easing = ExpressiveMotionTokens.PredictiveBackInterpolator
    )

    private val PredictiveBackTweenSlide = tween<IntOffset>(
        durationMillis = 400,
        easing = ExpressiveMotionTokens.PredictiveBackInterpolator
    )

    val ContainerTransformPredictiveBackEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { -it / 5 },
            animationSpec = PredictiveBackTweenSlide
        ) +
        scaleIn(
            initialScale = 0.95f,
            animationSpec = PredictiveBackTween
        ) +
        fadeIn(animationSpec = PredictiveBackTween)

    val ContainerTransformPredictiveBackExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it / 5 },
            animationSpec = PredictiveBackTweenSlide
        ) +
        fadeOut(animationSpec = PredictiveBackTween) +
        scaleOut(
            targetScale = 0.95f,
            animationSpec = PredictiveBackTween
        )

    // Shared Axis X - for lateral navigation (settings, log viewer)
    val SharedAxisXEnter: EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = PageEnterSpringSlide
    ) + fadeIn(
        animationSpec = PageEffectsSpring
    )

    val SharedAxisXExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = PageExitSpringSlide
    ) + fadeOut(
        animationSpec = PageEffectsSpring
    )

    val SharedAxisXPopEnter: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = PageEnterSpringSlide
    ) + fadeIn(
        animationSpec = PageEffectsSpring
    )

    val SharedAxisXPopExit: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = PageExitSpringSlide
    ) + fadeOut(
        animationSpec = PageEffectsSpring
    )

    // Shared Axis Z - for parent-child navigation (album/artist detail)
    val SharedAxisZEnter: EnterTransition = scaleIn(
        initialScale = 0.8f,
        animationSpec = PageEnterSpring
    ) + fadeIn(
        animationSpec = PageEffectsSpring
    )

    val SharedAxisZExit: ExitTransition = scaleOut(
        targetScale = 0.8f,
        animationSpec = PageExitSpring
    ) + fadeOut(
        animationSpec = PageEffectsSpring
    )

    // Fade Through - for bottom navigation (no spatial relationship)
    // Pure fade in/out without scaling for clean tab transitions
    val FadeThroughEnter: EnterTransition = fadeIn(
            animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.SlowEffects.dampingRatio,
                stiffness = ExpressiveMotionTokens.SlowEffects.stiffness
            )
        )
    
    val FadeThroughExit: ExitTransition = fadeOut(
            animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.SlowEffects.dampingRatio,
                stiffness = ExpressiveMotionTokens.SlowEffects.stiffness
            )
        )

    // Fade - for dialogs, bottom sheets, FABs
    val FadeEnter: EnterTransition = fadeIn(
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
            stiffness = ExpressiveMotionTokens.FastEffects.stiffness
        )
    ) + scaleIn(
        initialScale = 0.9f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.FastSpatial.dampingRatio,
            stiffness = ExpressiveMotionTokens.FastSpatial.stiffness
        )
    )

    val FadeExit: ExitTransition = fadeOut(
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
            stiffness = ExpressiveMotionTokens.FastEffects.stiffness
        )
    ) + scaleOut(
        targetScale = 0.9f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.FastSpatial.dampingRatio,
            stiffness = ExpressiveMotionTokens.FastSpatial.stiffness
        )
    )

    // ===== Component Animations (using Fast springs for small components) =====
    private val FastEnterSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.FastSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastSpatial.stiffness
    )

    private val FastEnterSpringSlide = spring<IntOffset>(
        dampingRatio = ExpressiveMotionTokens.FastSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastSpatial.stiffness
    )

    private val FastEffectsSpring = spring<Float>(
        dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastEffects.stiffness
    )

    // List item enter - for RecyclerView/LazyColumn items
    val ListItemEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = FastEnterSpringSlide
    ) + fadeIn(animationSpec = FastEffectsSpring)

    val ListItemExit = slideOutVertically(
        targetOffsetY = { -it },
        animationSpec = FastEnterSpringSlide
    ) + fadeOut(animationSpec = FastEffectsSpring)

    val CardExpand = expandVertically(
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
            stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
        )
    )

    val CardCollapse = shrinkVertically(
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
            stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
        )
    )

    val FabEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = FastEnterSpringSlide
    ) + fadeIn(animationSpec = FastEffectsSpring)

    val FabExit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = FastEnterSpringSlide
    ) + fadeOut(animationSpec = FastEffectsSpring)

    // Dialog/BottomSheet - using Default springs
    val DialogEnter = slideInVertically(
        initialOffsetY = { (it * 0.25).toInt() },
        animationSpec = spring<IntOffset>(
            dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
            stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
        )
    ) + fadeIn(
        animationSpec = spring<Float>(
            dampingRatio = ExpressiveMotionTokens.DefaultEffects.dampingRatio,
            stiffness = ExpressiveMotionTokens.DefaultEffects.stiffness
        )
    )

    // ===== State Change Animations =====
    val SelectionChange: AnimationSpec<Float> = FastEnterSpring
    val ValueChange: AnimationSpec<Float> = FastEnterSpring
    val ExpandCollapse: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )


// ============================================================================
// Animation Utilities
// ============================================================================

@Composable
fun rememberSpringAnimatedFloat(
    targetValue: Float,
    dampingRatio: Float = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
    stiffness: Float = ExpressiveMotionTokens.SlowSpatial.stiffness
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
            animation = keyframes {
                this.durationMillis = durationMillis
                initialScale at 0
                (pulsedScale at durationMillis).with(FastOutSlowInEasing)
            },
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
                animation = keyframes {
                    this.durationMillis = durationMillis
                    (-width at 0).with(LinearEasing)
                    (width * 2 at durationMillis).with(LinearEasing)
                },
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
    dampingRatio: Float = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
    stiffness: Float = ExpressiveMotionTokens.SlowSpatial.stiffness
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
    dampingRatio: Float = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
    stiffness: Float = ExpressiveMotionTokens.SlowSpatial.stiffness
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
    // Material 3 Motion Presets using standardized springs

    // Fade animations (using effects springs for color/opacity)
    val FadeIn: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastEffects.stiffness
    )
    val FadeOut: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.FastEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.FastEffects.stiffness
    )

    // Slide animations (using spatial springs for position)
    val SlideInUp: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )
    val SlideOutDown: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )

    // Scale animations
    val ScaleIn: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )
    val ScaleOut: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultSpatial.stiffness
    )

    // State change (no bounce for utility)
    val StateChange: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.DefaultEffects.dampingRatio,
        stiffness = ExpressiveMotionTokens.DefaultEffects.stiffness
    )

    // Emphasis (slight bounce for prominent UI)
    val Emphasis: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotionTokens.SlowSpatial.dampingRatio,
        stiffness = ExpressiveMotionTokens.SlowSpatial.stiffness
    )

    // Legacy compatibility
    @Deprecated("Use FadeIn", ReplaceWith("FadeIn"))
    val LegacyFadeIn = FadeIn
    @Deprecated("Use FadeOut", ReplaceWith("FadeOut"))
    val LegacyFadeOut = FadeOut
}

// ============================================================================
// Motion Logging
// ============================================================================

object MotionLogger {
    private const val TAG = "Motion"
    fun logMotionEvent(event: String, details: String = "") {
        Timber.tag(TAG).d("$event | $details")
    }
    fun logMotionStart(animationType: String, spec: String = "") {
        Timber.tag(TAG).d("Animation START: $animationType $spec")
    }
    fun logMotionEnd(animationType: String, durationMs: Long = 0) {
        Timber.tag(TAG).d("Animation END: $animationType (${durationMs}ms)")
    }
}
}
