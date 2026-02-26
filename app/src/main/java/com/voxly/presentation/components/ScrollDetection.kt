package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Scroll detection helper for hiding/showing UI elements based on scroll direction.
 * Use this to detect scroll direction and trigger UI element visibility changes.
 */
@Composable
fun rememberScrollDetection(
    listState: LazyListState,
    threshold: Int = 56,
    onVisibilityChange: (Boolean) -> Unit
) {
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var accumulatedScrollDelta by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.distinctUntilChanged().collect { (isScrolling, index, offset) ->
            if (!isScrolling) {
                lastScrollIndex = index
                lastScrollOffset = offset
                accumulatedScrollDelta = 0
                if (index == 0 && offset == 0) {
                    onVisibilityChange(true)
                }
                return@collect
            }

            val scrollDelta = when {
                index > lastScrollIndex -> threshold + 1
                index < lastScrollIndex -> -(threshold + 1)
                else -> offset - lastScrollOffset
            }

            if (scrollDelta != 0) {
                accumulatedScrollDelta = if (
                    (accumulatedScrollDelta >= 0 && scrollDelta > 0) ||
                    (accumulatedScrollDelta <= 0 && scrollDelta < 0)
                ) {
                    accumulatedScrollDelta + scrollDelta
                } else {
                    scrollDelta
                }
            }

            when {
                index == 0 && offset == 0 -> {
                    onVisibilityChange(true)
                    accumulatedScrollDelta = 0
                }
                accumulatedScrollDelta > threshold -> {
                    onVisibilityChange(false)
                    accumulatedScrollDelta = 0
                }
                accumulatedScrollDelta < -threshold -> {
                    onVisibilityChange(true)
                    accumulatedScrollDelta = 0
                }
            }

            lastScrollIndex = index
            lastScrollOffset = offset
        }
    }
}

/**
 * Calculate whether we can scroll to top based on list state.
 */
@Composable
fun rememberCanScrollToTop(
    listState: LazyListState
): Boolean {
    return remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }.value
}
