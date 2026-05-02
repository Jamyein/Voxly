package com.voxly.presentation.components.scrollbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/**
 * Dummy [ScrollbarState] for previews.
 */
private class PreviewScrollbarState(
    override val totalItemsCount: Int = 100,
    override val isScrollInProgress: Boolean = false,
    override val isScrollable: Boolean = true,
    override val normalizedThumbOffset: Float = 0.3f,
    override val firstVisibleIndex: Int = 30,
    override val lastVisibleIndex: Int = 40
) : ScrollbarState {
    override suspend fun scrollByVelocity(velocity: Float) {}
    override suspend fun scrollToItem(index: Int) {}
    override suspend fun animateScrollToItem(index: Int) {}
}

/**
 * Provides sample [ScrollbarConfig] instances for previews.
 */
private class ScrollbarConfigProvider : PreviewParameterProvider<ScrollbarConfig> {
    override val values = sequenceOf(
        ScrollbarConfig.Default,
        ScrollbarConfig.Compact
    )
}

@Preview(showBackground = true, name = "Default Config")
@Composable
private fun M3EScrollbarPreviewDefault() {
    MaterialTheme {
        val state = remember { PreviewScrollbarState() }
        M3EScrollbar(state = state)
    }
}

@Preview(showBackground = true, name = "Compact Config")
@Composable
private fun M3EScrollbarPreviewCompact() {
    MaterialTheme {
        val state = remember { PreviewScrollbarState() }
        M3EScrollbar(
            state = state,
            config = ScrollbarConfig.Compact
        )
    }
}

@Preview(showBackground = true, name = "With Bubble")
@Composable
private fun M3EScrollbarPreviewWithBubble() {
    MaterialTheme {
        val state = remember { PreviewScrollbarState() }
        M3EScrollbar(
            state = state,
            showBubble = true,
            bubbleFormatter = { index -> "Song ${index + 1}" }
        )
    }
}

@Preview(showBackground = true, name = "Dragging State")
@Composable
private fun M3EScrollbarPreviewDragging() {
    MaterialTheme {
        val state = remember { PreviewScrollbarState() }
        val holder = rememberScrollbarStateHolder().apply {
            isDragging = true
            dragY = 300f
            isVisible = true
        }
        M3EScrollbar(
            state = state,
            holder = holder
        )
    }
}
