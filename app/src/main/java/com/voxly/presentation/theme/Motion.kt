package com.voxly.presentation.theme

import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion configuration for MD3 Expressive animations.
 * 
 * MD3 Expressive introduces MotionScheme for physics-based animations with
 * expressive, playful motion characteristics.
 * 
 * Note: MotionScheme API requires Material 3 Compose 1.4.0+ (available in BOM 2024.02.00+)
 * This file provides both MotionScheme support (when available) and fallback animation
 * utilities for backward compatibility.
 */

// ============================================================================
// MotionScheme Configuration (for future Material 3 MotionScheme API)
// ============================================================================

/**
 * Expressive motion presets for MD3 Expressive theme.
 * These values provide playful, physics-based animations.
 */
@Stable
object ExpressiveMotion {
    // Standard durations for MD3 Expressive animations
    const val ShortDuration = 150
    const val MediumDuration = 300
    const val LongDuration = 500
    
    // Spring damping ratios (from Spring class constants)
    const val DampingRatioMediumBouncy = Spring.DampingRatioMediumBouncy
    const val DampingRatioNoBouncy = Spring.DampingRatioNoBouncy
    const val DampingRatioLowBouncy = Spring.DampingRatioLowBouncy
    
    // Spring stiffness values
    const val StiffnessMedium = Spring.StiffnessMedium
    const val StiffnessMediumLow = Spring.StiffnessMediumLow
    const val StiffnessLow = Spring.StiffnessLow
    
    // Spring configurations for physics-based animations
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
    
    // Easing curves for Expressive motion
    val ExpressiveEasing = FastOutSlowInEasing
    val StandardEasing = tween<Float>(durationMillis = MediumDuration, easing = FastOutSlowInEasing)
}

// ============================================================================
// Animation Utilities (Fallback when MotionScheme not available)
// ============================================================================

/**
 * Creates a remember animated float value with spring physics.
 * Useful for responsive, physics-based UI animations.
 */
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

/**
 * Infinite transition for continuous animations (loading states, etc.)
 */
@Composable
fun rememberInfiniteTransition(
    label: String = "infiniteTransition"
): androidx.compose.animation.core.InfiniteTransition {
    return androidx.compose.animation.core.rememberInfiniteTransition(label = label)
}

/**
 * Creates a pulsing scale animation for emphasis effects.
 */
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

/**
 * Creates a shimmer effect for loading states.
 */
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
// Motion Modifier Extensions
// ============================================================================

/**
 * Applies a scale animation to the composable.
 */
@Composable
fun animatedScale(
    targetScale: Float,
    dampingRatio: Float = ExpressiveMotion.DampingRatioMediumBouncy,
    stiffness: Float = ExpressiveMotion.StiffnessMedium
): Float {
    return rememberSpringAnimatedFloat(
        targetValue = targetScale,
        dampingRatio = dampingRatio,
        stiffness = stiffness
    )
}

/**
 * Applies an alpha animation to the composable.
 */
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

/**
 * Applies a translation animation to the composable.
 */
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
// Preset Animation Specs
// ============================================================================

/**
 * Common animation specs for UI transitions.
 */
object MotionPresets {
    // Enter animations
    val FadeIn: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    val SlideInUp: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.MediumDuration, easing = FastOutSlowInEasing)
    val ScaleIn: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioMediumBouncy,
        stiffness = ExpressiveMotion.StiffnessMedium
    )
    
    // Exit animations
    val FadeOut: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    val SlideOutDown: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.MediumDuration, easing = FastOutSlowInEasing)
    val ScaleOut: AnimationSpec<Float> = tween<Float>(durationMillis = ExpressiveMotion.ShortDuration)
    
    // State change animations
    val StateChange: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioNoBouncy,
        stiffness = ExpressiveMotion.StiffnessMediumLow
    )
    
    // Emphasis animations (for interactive elements)
    val Emphasis: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveMotion.DampingRatioLowBouncy,
        stiffness = ExpressiveMotion.StiffnessLow
    )
}

// ============================================================================
// Motion Logging (for debugging)
// ============================================================================

/**
 * Debug logging for motion configuration.
 * Enable via Timber when debugging animations.
 */
object MotionLogger {
    const val TAG = "Motion"
    
    fun logMotionEvent(event: String, details: String = "") {
        // Timber.d("$TAG: $event - $details")
    }
}
