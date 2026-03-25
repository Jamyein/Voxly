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
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/**
 * CompositionLocal for providing AnimatedVisibilityScope from NavDisplay.
 * This is required for shared element transitions to work with Navigation3.
 */
val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }

/**
 * Configuration objects for different shared transition animation styles.
 */
object SharedTransitionConfigs {
    /**
     * Container Transform: Used for transitions between list items and detail pages.
     * Duration: 300ms, Easing: FastOutSlowIn (Material standard)
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ContainerTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    }

    /**
     * Fade Through: Used for transitions between unrelated pages.
     * Duration: 250ms, Easing: Linear
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val FadeThrough: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 250,
            easing = LinearEasing
        )
    }

    /**
     * Quick Transform: For smaller UI elements.
     * Duration: 200ms, Easing: FastOutSlowIn
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val QuickTransform: BoundsTransform = BoundsTransform { _, _ ->
        tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        )
    }
}

/**
 * Extension function to easily apply shared element modifier.
 * Checks if required scopes are available before applying.
 *
 * @param key Unique key for the shared element
 * @param boundsTransform Animation configuration (default: ContainerTransform)
 * @return Modifier with sharedElement applied if scopes are available
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
            this@sharedElementIfAvailable.sharedElement(
                state = rememberSharedContentState(key = key),
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
