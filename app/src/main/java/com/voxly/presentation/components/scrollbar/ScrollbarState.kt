package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.max

/**
 * Interface representing the state of a scrollbar.
 *
 * This mimics the official [androidx.compose.foundation.ScrollIndicatorState] API
 * for future compatibility, while working with current Compose versions.
 */
interface ScrollbarState {
    /** The total size of the scrollable content, typically in pixels */
    val contentSize: Int

    /** The current scroll offset of the content from the start, typically in pixels */
    val scrollOffset: Int

    /** The size of the visible portion of the scrollable content, typically in pixels */
    val viewportSize: Int
}

/**
 * Implementation of [ScrollbarState] for [LazyListState].
 *
 * Provides real-time scroll information for LazyColumn scrollbars.
 *
 * @param listState The LazyListState to track
 */
class LazyListScrollbarState(
    private val listState: LazyListState
) : ScrollbarState {

    override val contentSize: Int
        get() = calculateContentSize()

    override val scrollOffset: Int
        get() = calculateScrollOffset()

    override val viewportSize: Int
        get() = calculateViewportSize()

    private fun calculateContentSize(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return 0
        }

        val avgItemSize = if (visibleItems.size > 1) {
            val first = visibleItems.first()
            val last = visibleItems.last()
            (last.offset + last.size - first.offset) / visibleItems.size
        } else {
            visibleItems.firstOrNull()?.size ?: 100
        }

        return avgItemSize * layoutInfo.totalItemsCount
    }

    private fun calculateScrollOffset(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            return 0
        }

        val firstVisible = visibleItems.first()
        return firstVisible.offset + listState.firstVisibleItemScrollOffset
    }

    private fun calculateViewportSize(): Int {
        val layoutInfo = listState.layoutInfo
        return max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    /**
     * Get the current item index based on scroll progress.
     */
    fun getCurrentItemIndex(): Int {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        return (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    /**
     * Scroll to a specific progress (0.0 to 1.0).
     */
    suspend fun scrollToProgress(progress: Float) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        listState.scrollToItem(targetIndex)
    }
}

/**
 * Implementation of [ScrollbarState] for [LazyGridState].
 *
 * Provides real-time scroll information for LazyVerticalGrid scrollbars.
 *
 * @param gridState The LazyGridState to track
 */
class LazyGridScrollbarState(
    private val gridState: LazyGridState
) : ScrollbarState {

    override val contentSize: Int
        get() = calculateContentSize()

    override val scrollOffset: Int
        get() = calculateScrollOffset()

    override val viewportSize: Int
        get() = calculateViewportSize()

    private fun calculateContentSize(): Int {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return 0
        }

        val avgItemHeight = if (visibleItems.size > 1) {
            val first = visibleItems.first()
            val last = visibleItems.last()
            (last.offset.y + last.size.height - first.offset.y) / visibleItems.size
        } else {
            visibleItems.firstOrNull()?.size?.height ?: 100
        }

        return avgItemHeight * layoutInfo.totalItemsCount
    }

    private fun calculateScrollOffset(): Int {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            return 0
        }

        val firstVisible = visibleItems.first()
        return firstVisible.offset.y + gridState.firstVisibleItemScrollOffset
    }

    private fun calculateViewportSize(): Int {
        val layoutInfo = gridState.layoutInfo
        return max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    /**
     * Get the current item index based on scroll progress.
     */
    fun getCurrentItemIndex(): Int {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        return (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    /**
     * Scroll to a specific progress (0.0 to 1.0).
     */
    suspend fun scrollToProgress(progress: Float) {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        gridState.scrollToItem(targetIndex)
    }
}
