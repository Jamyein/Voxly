package com.voxly.presentation.components.scrollbar

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlin.math.max

/**
 * Interface representing the state of a scrollbar.
 *
 * Mimics the official [androidx.compose.foundation.ScrollIndicatorState] API
 * for future compatibility, while working with current Compose versions.
 */
interface ScrollbarState {
    /** The total size of the scrollable content, typically in pixels */
    val contentSize: Int

    /** The current scroll offset of the content from the start, typically in pixels */
    val scrollOffset: Int

    /** The size of the visible portion of the scrollable content, typically in pixels */
    val viewportSize: Int

    /** Whether the list is currently scrolling */
    val isScrollInProgress: Boolean

    /** Total number of items in the list */
    val totalItemsCount: Int

    /** Scroll by velocity for inertia effect */
    suspend fun scrollByVelocity(velocity: Float)
}

/**
 * Implementation of [ScrollbarState] for [LazyListState].
 *
 * Provides real-time scroll information for LazyColumn scrollbars.
 *
 * @param listState The LazyListState to track
 */
class LazyListScrollbarState(
    val listState: LazyListState
) : ScrollbarState {

    override val contentSize: Int by derivedStateOf {
        calculateContentSize()
    }

    override val scrollOffset: Int by derivedStateOf {
        calculateScrollOffset()
    }

    override val viewportSize: Int by derivedStateOf {
        calculateViewportSize()
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        listState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        listState.layoutInfo.totalItemsCount
    }

    private fun calculateScrollOffset(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return 0
        }

        val firstVisible = visibleItems.first()
        val avgItemSize = calculateAvgItemSize()
        
        // Global offset = (index * avgSize) + internal offset within the item
        return (listState.firstVisibleItemIndex * avgItemSize) + listState.firstVisibleItemScrollOffset
    }

    private fun calculateAvgItemSize(): Int {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return 100
        
        return (visibleItems.last().offset + visibleItems.last().size - visibleItems.first().offset) / visibleItems.size
    }

    private fun calculateContentSize(): Int {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return 0
        return calculateAvgItemSize() * layoutInfo.totalItemsCount
    }

    private fun calculateViewportSize(): Int {
        val layoutInfo = listState.layoutInfo
        return max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    fun getCurrentItemIndex(): Int {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        return (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToProgress(progress: Float) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        listState.scrollToItem(targetIndex)
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        listState.scroll {
            scrollBy(velocity / 5f)
        }
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
    val gridState: LazyGridState
) : ScrollbarState {

    override val contentSize: Int by derivedStateOf {
        calculateContentSize()
    }

    override val scrollOffset: Int by derivedStateOf {
        calculateScrollOffset()
    }

    override val viewportSize: Int by derivedStateOf {
        calculateViewportSize()
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        gridState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        gridState.layoutInfo.totalItemsCount
    }

    private fun calculateScrollOffset(): Int {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return 0
        }

        val firstVisible = visibleItems.first()
        val avgItemHeight = calculateAvgItemHeight()
        
        // Items are in rows (assume rows are uniform height)
        val rowSize = if (layoutInfo.visibleItemsInfo.size > 1) {
             val first = layoutInfo.visibleItemsInfo.first()
             val last = layoutInfo.visibleItemsInfo.last()
             val rows = (last.row - first.row).coerceAtLeast(1)
             (last.offset.y + last.size.height - first.offset.y) / rows
        } else 100

        return (gridState.firstVisibleItemIndex / maxOf(1, layoutInfo.visibleItemsInfo.size / (layoutInfo.visibleItemsInfo.last().row - layoutInfo.visibleItemsInfo.first().row + 1)) * rowSize) + gridState.firstVisibleItemScrollOffset
    }

    private fun calculateAvgItemHeight(): Int {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return 100
        
        val first = visibleItems.first()
        val last = visibleItems.last()
        val rows = (last.row - first.row).coerceAtLeast(1)
        return (last.offset.y + last.size.height - first.offset.y) / rows
    }

    private fun calculateContentSize(): Int {
        val layoutInfo = gridState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return 0
        
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            // Fallback: estimate based on first item size if available
            return layoutInfo.totalItemsCount * 200 // rough estimate
        }
        
        // Calculate rows more accurately
        val firstItem = visibleItems.first()
        val lastItem = visibleItems.last()
        val visibleRows = (lastItem.row - firstItem.row + 1).coerceAtLeast(1)
        val avgRowHeight = if (visibleRows > 1) {
            (lastItem.offset.y + lastItem.size.height - firstItem.offset.y) / (visibleRows - 1)
        } else {
            visibleItems.maxOf { it.size.height }
        }
        
        // Calculate total rows
        val spanCount = visibleItems.groupBy { it.row }.values.firstOrNull()?.size ?: 1
        val totalRows = (layoutInfo.totalItemsCount + spanCount - 1) / spanCount
        
        return avgRowHeight * totalRows
    }

    private fun calculateViewportSize(): Int {
        val layoutInfo = gridState.layoutInfo
        return max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    fun getCurrentItemIndex(): Int {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        return (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToProgress(progress: Float) {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        gridState.scrollToItem(targetIndex)
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        gridState.scroll {
            scrollBy(velocity / 5f)
        }
    }
}
