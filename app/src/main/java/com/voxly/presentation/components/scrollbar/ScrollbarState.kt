package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
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

    /** Current visible item index based on scroll position */
    val currentItemIndex: Int

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

    // Cache avgItemSize to avoid recalculating on every contentSize/scrollOffset access
    private val avgItemSize: Int by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf 100
        (visibleItems.last().offset + visibleItems.last().size - visibleItems.first().offset) / visibleItems.size
    }

    override val contentSize: Int by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return@derivedStateOf 0
        avgItemSize * layoutInfo.totalItemsCount
    }

    override val scrollOffset: Int by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return@derivedStateOf 0
        }

        // Global offset = (index * avgSize) + internal offset within the item
        (listState.firstVisibleItemIndex * avgItemSize) + listState.firstVisibleItemScrollOffset
    }

    override val viewportSize: Int by derivedStateOf {
        val layoutInfo = listState.layoutInfo
        max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        listState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        listState.layoutInfo.totalItemsCount
    }

    override val currentItemIndex: Int by derivedStateOf {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return@derivedStateOf 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToProgress(progress: Float) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        listState.scrollToItem(targetIndex)
    }

    suspend fun scrollToOffset(targetOffset: Int) {
        val currentOffset = scrollOffset
        val delta = (targetOffset - currentOffset).toFloat()
        listState.scroll {
            scrollBy(delta)
        }
    }

    suspend fun scrollByDelta(delta: Float) {
        listState.scroll {
            scrollBy(delta)
        }
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

    // Cache rowHeight and spanCount to avoid recalculating on every contentSize/scrollOffset access
    private val gridMetrics: GridMetrics by derivedStateOf {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return@derivedStateOf GridMetrics(rowHeight = 200, spanCount = 1)
        }

        val firstItem = visibleItems.first()
        val lastItem = visibleItems.last()
        val visibleRows = (lastItem.row - firstItem.row + 1).coerceAtLeast(1)
        val rowHeight = if (visibleRows > 1) {
            (lastItem.offset.y + lastItem.size.height - firstItem.offset.y) / (visibleRows - 1)
        } else {
            visibleItems.maxOf { it.size.height }
        }
        val spanCount = inferSpanCount(layoutInfo)

        GridMetrics(rowHeight = rowHeight, spanCount = spanCount)
    }

    override val contentSize: Int by derivedStateOf {
        val layoutInfo = gridState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return@derivedStateOf 0

        val totalRows = (layoutInfo.totalItemsCount + gridMetrics.spanCount - 1) / gridMetrics.spanCount
        totalRows * gridMetrics.rowHeight
    }

    override val scrollOffset: Int by derivedStateOf {
        val layoutInfo = gridState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) {
            return@derivedStateOf 0
        }

        val topItem = visibleItems.minByOrNull { it.offset.y } ?: visibleItems.first()
        val topItemRow = topItem.row

(topItemRow * gridMetrics.rowHeight) + topItem.offset.y
    }

    override val viewportSize: Int by derivedStateOf {
        val layoutInfo = gridState.layoutInfo
        max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        gridState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        gridState.layoutInfo.totalItemsCount
    }

    override val currentItemIndex: Int by derivedStateOf {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return@derivedStateOf 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    private fun inferSpanCount(layoutInfo: LazyGridLayoutInfo): Int {
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return 1

        val itemsByRow = visibleItems.groupBy { it.row }
        val rowCounts = itemsByRow.values.map { it.size }

        return if (rowCounts.isNotEmpty()) {
            rowCounts.maxOrNull() ?: 1
        } else 1
    }

    suspend fun scrollToProgress(progress: Float) {
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return

        val targetIndex = (progress * (totalItems - 1)).toInt()
            .coerceIn(0, totalItems - 1)

        gridState.scrollToItem(targetIndex)
    }

    suspend fun scrollToOffset(targetOffset: Int) {
        val currentOffset = scrollOffset
        val delta = (targetOffset - currentOffset).toFloat()
        gridState.scroll {
            scrollBy(delta)
        }
    }

    suspend fun scrollByDelta(delta: Float) {
        gridState.scroll {
            scrollBy(delta)
        }
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        gridState.scroll {
            scrollBy(velocity / 5f)
        }
    }

    private data class GridMetrics(val rowHeight: Int, val spanCount: Int)
}
