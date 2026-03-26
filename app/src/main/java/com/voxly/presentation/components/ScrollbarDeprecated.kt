package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.voxly.presentation.components.scrollbar.AlphabetScrollbarM3E as NewAlphabetScrollbarM3E
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.scrollbar.LazyGridScrollbarState
import com.voxly.presentation.components.scrollbar.LazyListScrollbarState
import com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar
import com.voxly.presentation.components.scrollbar.M3EScrollbar
import com.voxly.presentation.components.scrollbar.ScrollbarConfig

/**
 * **DEPRECATED**: Use [com.voxly.presentation.components.scrollbar.LazyColumnScrollbar] instead.
 *
 * This function is kept for backward compatibility and will be removed in a future version.
 */
@Deprecated(
    message = "Use LazyColumnScrollbar from com.voxly.presentation.components.scrollbar package",
    replaceWith = ReplaceWith(
        "LazyColumnScrollbar(state = listState, modifier = modifier, showBubble = showBubble, bubbleFormatter = bubbleFormatter)",
        "com.voxly.presentation.components.scrollbar.LazyColumnScrollbar"
    )
)
@Composable
fun M3EScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null
) {
    LazyColumnScrollbar(
        state = listState,
        modifier = modifier,
        showBubble = showBubble,
        bubbleFormatter = bubbleFormatter
    )
}

/**
 * **DEPRECATED**: Use [com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar] instead.
 *
 * This function is kept for backward compatibility and will be removed in a future version.
 */
@Deprecated(
    message = "Use LazyVerticalGridScrollbar from com.voxly.presentation.components.scrollbar package",
    replaceWith = ReplaceWith(
        "LazyVerticalGridScrollbar(state = gridState, modifier = modifier, showBubble = showBubble, bubbleFormatter = bubbleFormatter)",
        "com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar"
    )
)
@Composable
fun M3EGridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    showBubble: Boolean = true,
    bubbleFormatter: ((Int) -> String)? = null
) {
    LazyVerticalGridScrollbar(
        state = gridState,
        modifier = modifier,
        showBubble = showBubble,
        bubbleFormatter = bubbleFormatter
    )
}

/**
 * **DEPRECATED**: Use [com.voxly.presentation.components.scrollbar.AlphabetScrollbarM3E] instead.
 *
 * This function is kept for backward compatibility and will be removed in a future version.
 */
@Deprecated(
    message = "Use AlphabetScrollbarM3E from com.voxly.presentation.components.scrollbar package",
    replaceWith = ReplaceWith(
        "AlphabetScrollbarM3E(state = listState, letterToIndex = letterToIndex, totalItems = totalItems, modifier = modifier)",
        "com.voxly.presentation.components.scrollbar.AlphabetScrollbarM3E"
    )
)
@Composable
fun AlphabetScrollbarM3E(
    listState: LazyListState,
    letterToIndex: Map<Char, Int>,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    NewAlphabetScrollbarM3E(
        state = listState,
        letterToIndex = letterToIndex,
        totalItems = totalItems,
        modifier = modifier
    )
}
