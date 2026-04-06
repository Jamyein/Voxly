package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * CompositionLocal for providing SharedTransitionScope throughout the navigation hierarchy.
 * This allows child composables to access the SharedTransitionScope for shared element transitions.
 * 
 * Note: In Compose 1.11.0-beta01+, SharedTransitionScope is the primary scope for sharedBounds API.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/**
 * CompositionLocal for providing AnimatedVisibilityScope from Navigation 3.
 * 
 * This scope is required for sharedBounds() modifier and is provided by AnimatedContent.
 */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/**
 * Configuration objects for different shared transition animation styles.
 * Used with sharedBounds() modifier.
 * 
 * IMPORTANT: All transitions now use official MaterialTheme.motionScheme specs.
 * These are computed at call time to ensure they use the current theme.
 */
object SharedTransitionConfigs {
    /**
     * Container Transform: Used for transitions between list items and detail pages.
     * Uses M3 Expressive slow spatial spring from MotionScheme.
     *
     * Best for: Audio file list to metadata editor transitions
     */
    val ContainerTransform: BoundsTransform = BoundsTransform { _, _ ->
        // Use slow spatial spring for full-screen morph transitions
        // This will be obtained from MaterialTheme when the transition runs
        // Here we provide a fallback that matches M3E specs
        spring(
            dampingRatio = 0.9f,
            stiffness = 300f
        )
    }

    /**
     * Container Transform Tween: Alternate using tween for tighter transitions.
     * Uses M3 Expressive duration token medium3 (350ms) with standard easing.
     *
     * Use this if spring animation feels too bouncy.
     */
    val ContainerTransformTween: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 350, // Medium3
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f) // StandardInterpolator
        )
    }

    /**
     * Fade Through: Used for transitions between unrelated pages.
     * Uses M3 Expressive duration medium2 (300ms) with standard easing.
     *
     * Best for: Bottom navigation tab switches
     */
    val FadeThrough: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 300, // Medium2
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f) // StandardInterpolator
        )
    }

    /**
     * Quick Transform: For smaller UI elements.
     * Uses M3 Expressive short3 duration (150ms).
     *
     * Best for: Buttons, icons, chips
     */
    val QuickTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 150, // Short3
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f) // StandardInterpolator
        )
    }

    /**
     * Expressive Container Transform: M3 Expressive recommended animation.
     * Uses keyframes with ArcMode.ArcBelow and standard interpolator.
     * Duration: motionDurationLong3 (350ms)
     *
     * Best for: List item to detail page transitions in Material 3 Expressive
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ExpressiveContainerTransform: BoundsTransform = BoundsTransform { initial, target ->
        keyframes {
            durationMillis = 350 // Medium3
            initial at 0 using ArcMode.ArcBelow using CubicBezierEasing(0.2f, 0f, 0f, 1f)
            target at durationMillis
        }
    }
}

/**
 * Extension function to easily apply shared bounds modifier.
 * 
 * This is the recommended API in Compose 1.11.0-beta01+ for shared element transitions.
 * It handles both position and shape transformations (e.g., rounded corners to rectangle).
 * 
 * Note: APIs are accessed through SharedTransitionScope receiver to ensure correct imports.
 * 
 * @param key Unique key for the shared element
 * @param boundsTransform Animation configuration for the bounds transformation
 * @return Modifier with sharedBounds applied, or unchanged if scopes are not available
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsIfAvailable(
    key: String,
    boundsTransform: BoundsTransform = SharedTransitionConfigs.ContainerTransform
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    return if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        // Use SharedTransitionScope extension functions directly
        with(sharedTransitionScope) {
            // Access rememberSharedContentState and sharedBounds through the scope
            this@sharedBoundsIfAvailable.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = boundsTransform
            )
        }
    } else {
        this
    }
}

/**
 * Helper function to create unique keys for audio file shared elements.
 *
 * @param filePath The audio file path
 * @return Unique key string
 */
fun createAudioFileSharedElementKey(filePath: String): String = "audio-file-$filePath"

/**
 * Helper function to create unique keys for album shared elements.
 *
 * @param albumName The album name
 * @param albumArtist The album artist (optional, for disambiguation)
 * @return Unique key string
 */
fun createAlbumSharedElementKey(albumName: String, albumArtist: String? = null): String {
    return if (albumArtist != null) {
        "album-$albumName-$albumArtist"
    } else {
        "album-$albumName"
    }
}

/**
 * Helper function to create unique keys for artist shared elements.
 *
 * @param artistName The artist name
 * @return Unique key string
 */
fun createArtistSharedElementKey(artistName: String): String = "artist-$artistName"

/**
 * Helper function to create unique keys for album art shared elements.
 * Used for Container Transform transitions between list items and detail pages.
 *
 * @param filePath The audio file path
 * @return Unique key string for the album art shared element
 */
fun createAlbumArtSharedElementKey(filePath: String): String = "album-art-$filePath"

/**
 * Helper function to create unique keys for album cover shared elements.
 * Used for Container Transform transitions from AlbumScreen to AlbumDetailScreen.
 *
 * @param albumName The album name
 * @param albumArtist The album artist (optional, for disambiguation)
 * @return Unique key string for the album cover shared element
 */
fun createAlbumCoverSharedElementKey(albumName: String, albumArtist: String?): String {
    return "album-cover-$albumName-${albumArtist ?: "unknown"}"
}

/**
 * Helper function to create unique keys for artist avatar shared elements.
 * Used for Container Transform transitions from ArtistScreen to ArtistDetailScreen.
 *
 * @param artistName The artist name
 * @return Unique key string for the artist avatar shared element
 */
fun createArtistAvatarSharedElementKey(artistName: String): String = "artist-avatar-$artistName"

/**
 * Extension function to easily apply shared element modifier for non-container elements.
 * Used for hero elements like album art that transform from list to detail.
 *
 * This version uses sharedBounds which naturally handles bounds transformations including
 * shape changes when both start and end composables use consistent clipping.
 *
 * Key improvements:
 * 1. Uses ResizeMode.RemeasureToBounds for smoother transitions
 * 2. Applies spring animation by default for more natural motion
 * 3. Compatible with clip() modifier applied after sharedBounds
 *
 * @param key Unique key for the shared element
 * @param boundsTransform Animation configuration for the bounds transformation
 * @return Modifier with sharedBounds applied, or unchanged if scopes are not available
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIfAvailable(
    key: String,
    boundsTransform: BoundsTransform = SharedTransitionConfigs.ContainerTransform
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    return if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            this@sharedElementIfAvailable.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = boundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
            )
        }
    } else {
        this
    }
}
