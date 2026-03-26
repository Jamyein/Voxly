package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Creates a Material 3 Expressive scrollbar for a LazyColumn.
 *
 * This is a convenience function that creates the appropriate ScrollbarState
 * and renders the scrollbar. Use this when you want a standalone scrollbar
 * composable (as a sibling to your LazyColumn).
 *
 * For Modifier-based approach, use [Modifier.m3eScrollbar] instead.
 *
 * @param state The LazyListState of the LazyColumn
 * @param modifier Modifier for the scrollbar container
 * @param showBubble Whether to show the preview bubble when dragging
 * @param bubbleFormatter Optional formatter for bubble text
 * @param config Optional scrollbar configuration
 */
@Composable
fun LazyColumnScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    config: ScrollbarConfig = ScrollbarConfig.Default
) {
    val scrollbarState = remember(state) {
        LazyListScrollbarState(state)
    }

    M3EScrollbar(
        state = scrollbarState,
        modifier = modifier,
        config = config,
        showBubble = showBubble,
        bubbleFormatter = bubbleFormatter
    )
}

/**
 * Creates a Material 3 Expressive scrollbar for a LazyVerticalGrid.
 *
 * @param state The LazyGridState of the LazyVerticalGrid
 * @param modifier Modifier for the scrollbar container
 * @param showBubble Whether to show the preview bubble when dragging
 * @param bubbleFormatter Optional formatter for bubble text
 * @param config Optional scrollbar configuration
 */
@Composable
fun LazyVerticalGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    config: ScrollbarConfig = ScrollbarConfig.Default
) {
    val scrollbarState = remember(state) {
        LazyGridScrollbarState(state)
    }

    M3EScrollbar(
        state = scrollbarState,
        modifier = modifier,
        config = config,
        showBubble = showBubble,
        bubbleFormatter = bubbleFormatter
    )
}

/**
 * Modifier extension to add a Material 3 Expressive scrollbar to a LazyColumn.
 *
 * Usage:
 * ```kotlin
 * LazyColumn(
 *     state = listState,
 *     modifier = Modifier.fillMaxSize().m3eScrollbar(listState)
 * ) {
 *     // items
 * }
 * ```
 *
 * @param state The LazyListState of the LazyColumn
 * @param showBubble Whether to show the preview bubble
 * @param bubbleFormatter Optional formatter for bubble text
 * @param config Optional scrollbar configuration
 */
@Composable
fun Modifier.m3eScrollbar(
    state: LazyListState,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier {
    // This is a placeholder - Modifier-based approach requires custom layout
    // For now, use LazyColumnScrollbar composable instead
    return this
}

/**
 * Modifier extension to add a Material 3 Expressive scrollbar to a LazyVerticalGrid.
 *
 * @param state The LazyGridState of the LazyVerticalGrid
 * @param showBubble Whether to show the preview bubble
 * @param bubbleFormatter Optional formatter for bubble text
 * @param config Optional scrollbar configuration
 */
@Composable
fun Modifier.m3eGridScrollbar(
    state: LazyGridState,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null,
    config: ScrollbarConfig = ScrollbarConfig.Default
): Modifier {
    // Placeholder - use LazyVerticalGridScrollbar composable instead
    return this
}
