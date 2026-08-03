package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Slide-in / slide-out wrapper for the floating pill bottom navigation bar.
 *
 * Uses [AnimatedVisibility] with vertical expand/shrink + cross-fade to keep the gesture
 * snappy without a hard jump. We do NOT use `Modifier.graphicsLayer.translationY` for the
 * hide animation: the Scaffold's bottomBar slot collapses to zero height on hide, which
 * means the list content gets an extra row of room — that is the whole point of the
 * pattern (read more of the list while the bar is hidden).
 *
 * The hide/show decision is read from [BottomBarVisibilityController.isVisible] via the
 * [LocalBottomBarVisibilityController] provided by [ProvideBottomBarVisibilityController].
 *
 * Timing is kept short (120 ms): the slot collapse re-lays out the whole Scaffold content, so a
 * long shrink compounds with the page transition that triggers it. 120 ms still reads as a
 * responsive hide while minimizing the re-layout jank window.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedBottomBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val controller = LocalBottomBarVisibilityController.current
    val isVisible = controller.isVisible

    // We use AnimatedVisibility so the layout slot collapses on hide — that is what gives
    // the underlying LazyColumn more vertical space to scroll through. expandVertically /
    // shrinkVertically animate the slot's measured height in lockstep with the bar.
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(durationMillis = 120)) +
            expandVertically(animationSpec = tween(durationMillis = 120)),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
            shrinkVertically(animationSpec = tween(durationMillis = 120))
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            content()
        }
    }
}
