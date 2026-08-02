@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

/**
 * Material Design 3 Expressive motion.
 *
 * Everything here derives from the single [MotionScheme] applied in [MP3TagTheme] via
 * `MaterialTheme.motionScheme` (default: `MotionScheme.expressive()`). Swap the scheme in
 * Theme.kt and every animation in the app follows — one source of truth, per the M3 Expressive
 * motion-model (Level 1: "use a default motion scheme").
 *
 * Speed mapping follows the M3 motion-physics spec:
 * - **slow** — full-screen page transitions (spatial + effects)
 * - **fast** — small components, list items, dialogs (spatial + effects)
 * - **tween** — gestures (predictive back) are time-based, not springs
 */

/** Predictive-back gesture curve per M3 spec. */
private val PredictiveBackInterpolator = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private val PredictiveBackTween = tween<Float>(
    durationMillis = 400,
    easing = PredictiveBackInterpolator,
)

private val PredictiveBackTweenSlide = tween<IntOffset>(
    durationMillis = 400,
    easing = PredictiveBackInterpolator,
)

/**
 * Prebuilt transitions for Navigation3 and [AnimatedVisibility].
 *
 * Getters are `@Composable` so they read the active [MotionScheme] and cache the result per
 * scheme — the spring objects are built once, not per recomposition.
 */
object ExpressiveAnimations {

    // ===== Container Transform (shared-element pages) — fade + scale only, no slide =====
    // (A slide would fight the shared-element bounds animation.)

    @Composable
    fun containerTransformSharedElementEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            fadeIn(animationSpec = scheme.slowEffectsSpec()) +
                scaleIn(initialScale = 0.97f, animationSpec = scheme.slowSpatialSpec())
        }
    }

    @Composable
    fun containerTransformSharedElementExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            fadeOut(animationSpec = scheme.slowEffectsSpec()) +
                scaleOut(targetScale = 0.97f, animationSpec = scheme.slowSpatialSpec())
        }
    }

    @Composable
    fun containerTransformSharedElementPopEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            scaleIn(initialScale = 0.97f, animationSpec = scheme.slowSpatialSpec()) +
                fadeIn(animationSpec = scheme.slowEffectsSpec())
        }
    }

    @Composable
    fun containerTransformSharedElementPopExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            fadeOut(animationSpec = scheme.slowEffectsSpec()) +
                scaleOut(targetScale = 0.97f, animationSpec = scheme.slowSpatialSpec())
        }
    }

    @Composable
    fun containerTransformSharedElementPredictiveBackEnter(): EnterTransition =
        fadeIn(animationSpec = PredictiveBackTween)

    @Composable
    fun containerTransformSharedElementPredictiveBackExit(): ExitTransition =
        fadeOut(animationSpec = PredictiveBackTween)

    // ===== Container Transform with predictive back — slide + scale + fade, gesture tween =====

    @Composable
    fun containerTransformPredictiveBackEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideInHorizontally(
                initialOffsetX = { -it / 5 },
                animationSpec = PredictiveBackTweenSlide,
            ) + scaleIn(initialScale = 0.95f, animationSpec = PredictiveBackTween) +
                fadeIn(animationSpec = PredictiveBackTween)
        }
    }

    @Composable
    fun containerTransformPredictiveBackExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideOutHorizontally(
                targetOffsetX = { it / 5 },
                animationSpec = PredictiveBackTweenSlide,
            ) + fadeOut(animationSpec = PredictiveBackTween) +
                scaleOut(targetScale = 0.95f, animationSpec = PredictiveBackTween)
        }
    }

    // ===== Shared Axis X — lateral navigation (settings, log viewer, metadata flows) =====

    @Composable
    fun sharedAxisXEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = scheme.slowSpatialSpec(),
            ) + fadeIn(animationSpec = scheme.slowEffectsSpec())
        }
    }

    @Composable
    fun sharedAxisXExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = scheme.slowSpatialSpec(),
            ) + fadeOut(animationSpec = scheme.slowEffectsSpec())
        }
    }

    @Composable
    fun sharedAxisXPopEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = scheme.slowSpatialSpec(),
            ) + fadeIn(animationSpec = scheme.slowEffectsSpec())
        }
    }

    @Composable
    fun sharedAxisXPopExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = scheme.slowSpatialSpec(),
            ) + fadeOut(animationSpec = scheme.slowEffectsSpec())
        }
    }

    // ===== Fade — dialogs, sheets, FABs, search result items =====

    @Composable
    fun fadeEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            fadeIn(animationSpec = scheme.fastEffectsSpec()) +
                scaleIn(initialScale = 0.95f, animationSpec = scheme.fastSpatialSpec())
        }
    }

    @Composable
    fun fadeExit(): ExitTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            fadeOut(animationSpec = scheme.fastEffectsSpec()) +
                scaleOut(targetScale = 0.95f, animationSpec = scheme.fastSpatialSpec())
        }
    }

    // ===== List item enter — lazy-list rows appearing =====

    @Composable
    fun listItemEnter(): EnterTransition {
        val scheme = MaterialTheme.motionScheme
        return remember(scheme) {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = scheme.fastSpatialSpec(),
            ) + fadeIn(animationSpec = scheme.fastEffectsSpec())
        }
    }
}

/**
 * Bounds transform for shared-element container transforms, from the active [MotionScheme]
 * (default spatial: 0.8 damping, 380 stiffness). Captured once per scheme so the spring is
 * built once, not per invocation.
 */
@Composable
fun rememberSharedElementBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}

/**
 * Bounds transform for small text shared elements (album titles, artists, names), from the
 * active [MotionScheme] fast-spatial spring (0.6 damping, 800 stiffness). Text reads best
 * when the morph is crisp and settles fast — the slower default spring makes glyphs visibly
 * "swim" across the gap between list and detail.
 */
@Composable
fun rememberSharedElementTextBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}

/**
 * Bounds transform for the hero title morph: an arc below the straight line over 400ms, per
 * the shared-element customization guidance. The arc gives the headline a pleasant "dip" as
 * it travels between list and detail, matching the app's expressive personality.
 */
@Composable
fun rememberSharedElementTitleBoundsTransform(): BoundsTransform = remember {
    BoundsTransform { initialBounds, targetBounds ->
        keyframes {
            durationMillis = 400
            initialBounds at 0 using ArcMode.ArcBelow using FastOutSlowInEasing
            targetBounds at 400
        }
    }
}

/**
 * Scales the modifier down while pressed, with the scheme's fast-spatial spring release.
 * Pass the same [interactionSource] the clickable uses so press state and ripple stay in sync.
 * Uses [androidx.compose.ui.composed] so this stays a plain (non-@Composable) Modifier
 * extension that can be imported anywhere.
 */
fun Modifier.scaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    label: String = "scaleOnPress",
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = label,
    )
    Modifier.scale(scale)
}
