package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Remembers and calculates scroll progress for flexible bottom app bar.
 * Provides smooth progress value (0-1) based on scroll position.
 *
 * @param listState The LazyListState to observe
 * @param threshold The pixel threshold for full collapse (default 56.dp)
 * @param onProgressChange Callback with scroll progress (0 = expanded, 1 = collapsed)
 */
@Composable
fun rememberScrollProgress(
    listState: LazyListState,
    threshold: Int = 56,
    onProgressChange: (Float) -> Unit = {}
): Float {
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var accumulatedScrollDelta by remember { mutableFloatStateOf(0f) }
    var currentProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.distinctUntilChanged().collect { (isScrolling, index, offset) ->
            if (!isScrolling) {
                // Reset when scroll stops - keep current progress for smooth resume
                lastScrollIndex = index
                lastScrollOffset = offset
                accumulatedScrollDelta = 0f

                // If at top, fully expand
                if (index == 0 && offset == 0) {
                    currentProgress = 0f
                    onProgressChange(0f)
                }
                return@collect
            }

            val scrollDelta = when {
                index > lastScrollIndex -> threshold + 1f
                index < lastScrollIndex -> -(threshold + 1f)
                else -> (offset - lastScrollOffset).toFloat()
            }

            if (scrollDelta != 0f) {
                accumulatedScrollDelta = if (
                    (accumulatedScrollDelta >= 0 && scrollDelta > 0) ||
                    (accumulatedScrollDelta <= 0 && scrollDelta < 0)
                ) {
                    accumulatedScrollDelta + scrollDelta
                } else {
                    scrollDelta
                }
            }

            // Calculate progress (0 = fully visible, 1 = fully hidden)
            val newProgress = (abs(accumulatedScrollDelta) / (threshold * 10))
                .coerceIn(0f, 1f)

            // Only update if changed significantly to reduce recompositions
            if (abs(newProgress - currentProgress) > 0.01f) {
                currentProgress = newProgress
                onProgressChange(newProgress)
            }

            lastScrollIndex = index
            lastScrollOffset = offset
        }
    }

    return currentProgress
}
