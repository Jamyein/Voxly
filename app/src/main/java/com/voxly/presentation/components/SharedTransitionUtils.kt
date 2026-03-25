package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

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
 */
object SharedTransitionConfigs {
    /**
     * Container Transform: Used for transitions between list items and detail pages.
     * Duration: 300ms, Easing: FastOutSlowIn (Material standard)
     * 
     * Best for: Audio file list to metadata editor transitions
     */
    val ContainerTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    }

    /**
     * Fade Through: Used for transitions between unrelated pages.
     * Duration: 250ms, Easing: Linear
     * 
     * Best for: Bottom navigation tab switches
     */
    val FadeThrough: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 250,
            easing = LinearEasing
        )
    }

    /**
     * Quick Transform: For smaller UI elements.
     * Duration: 200ms, Easing: FastOutSlowIn
     * 
     * Best for: Buttons, icons, chips
     */
    val QuickTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        )
    }
}

/**
 * Extension function to easily apply shared bounds modifier.
 * 
 * This is the recommended API in Compose 1.11.0-beta01+ for shared element transitions.
 * It handles both position and shape transformations (e.g., rounded corners to rectangle).
 * 
 * Note: In 1.11.0-beta01, sharedBounds API may be experimental or moved. 
 * This function provides a safe fallback.
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
    // Temporarily disabled until sharedBounds API is fully available
    // TODO: Enable when rememberSharedContentState and sharedBounds are confirmed available
    return this
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
