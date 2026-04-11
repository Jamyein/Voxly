package com.voxly.presentation.components.scrollbar

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

    private val layoutInfo get() = listState.layoutInfo

    override val contentSize: Int by derivedStateOf {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return@derivedStateOf 0
        // Use viewportStartOffset as the actual scroll offset when at rest at index 0
        // This is more accurate than avgItemSize * totalItems
        val totalItemsMinus1 = (info.totalItemsCount - 1).coerceAtLeast(0)
        if (totalItemsMinus1 == 0) return@derivedStateOf info.viewportEndOffset
        // Estimate: total scrollable space = lastVisibleItem offset + size - firstVisible offset + remaining items
        val lastItem = info.visibleItemsInfo.lastOrNull()
        val firstItem = info.visibleItemsInfo.firstOrNull()
        if (lastItem != null && firstItem != null) {
            val visibleRange = lastItem.offset + lastItem.size - firstItem.offset
            val visibleCount = info.visibleItemsInfo.size
            if (visibleCount > 0) {
                val avgItemSize = visibleRange / visibleCount
                val remainingItems = info.totalItemsCount - info.visibleItemsInfo.size
                (lastItem.offset + lastItem.size) + (remainingItems * avgItemSize)
            } else {
                avgItemSize * info.totalItemsCount
            }
        } else {
            avgItemSize * info.totalItemsCount
        }
    }

    private val avgItemSize: Int by derivedStateOf {
        val info = layoutInfo
        val visibleItems = info.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf 100
        (visibleItems.last().offset + visibleItems.last().size - visibleItems.first().offset) / visibleItems.size
    }

    override val scrollOffset: Int by derivedStateOf {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return@derivedStateOf 0
        // Use viewportStartOffset directly - it's the actual scroll position
        info.viewportStartOffset
    }

    override val viewportSize: Int by derivedStateOf {
        val info = layoutInfo
        max(0, info.viewportEndOffset - info.viewportStartOffset)
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        listState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        layoutInfo.totalItemsCount
    }

    override val currentItemIndex: Int by derivedStateOf {
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems <= 0) return@derivedStateOf 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToItem(index: Int) {
        listState.scrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    suspend fun animateScrollToItem(index: Int) {
        listState.animateScrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
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

    private val layoutInfo get() = gridState.layoutInfo

    private val gridMetrics: GridMetrics by derivedStateOf {
        val info = layoutInfo
        val visibleItems = info.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return@derivedStateOf GridMetrics(rowHeight = 200, spanCount = 1, contentSize = 0)
        }

        val itemsByRow = visibleItems.groupBy { it.row }
        val sortedRows = itemsByRow.keys.sorted()
        val spanCount = itemsByRow.values.maxOfOrNull { it.size } ?: 1

        // Calculate row height from all visible rows
        val rowHeights = sortedRows.mapNotNull { row ->
            val itemsInRow = itemsByRow[row]
            if (itemsInRow != null && itemsInRow.size >= spanCount) {
                // All items in this row have the same y offset and height
                itemsInRow.firstOrNull()?.let { it.size.height }
            } else if (itemsInRow != null && itemsInRow.size == 1) {
                // Single item in row - need to estimate row height
                itemsInRow.firstOrNull()?.size?.height
            } else null
        }

        val rowHeight = if (rowHeights.size >= 2) {
            rowHeights.average().toInt()
        } else if (rowHeights.size == 1) {
            rowHeights.first()
        } else {
            visibleItems.maxOfOrNull { it.size.height } ?: 200
        }

        val totalRows = (info.totalItemsCount + spanCount - 1) / spanCount
        val estimatedContentSize = totalRows * rowHeight

        GridMetrics(rowHeight = rowHeight, spanCount = spanCount, contentSize = estimatedContentSize)
    }

    override val contentSize: Int by derivedStateOf {
        gridMetrics.contentSize
    }

    override val scrollOffset: Int by derivedStateOf {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return@derivedStateOf 0
        // Use viewportStartOffset for actual scroll position - it's maintained by LazyList
        info.viewportStartOffset
    }

    override val viewportSize: Int by derivedStateOf {
        val info = layoutInfo
        max(0, info.viewportEndOffset - info.viewportStartOffset)
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        gridState.isScrollInProgress
    }

    override val totalItemsCount: Int by derivedStateOf {
        layoutInfo.totalItemsCount
    }

    override val currentItemIndex: Int by derivedStateOf {
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems <= 0) return@derivedStateOf 0

        val scrollRange = max(1, contentSize - viewportSize)
        val progress = if (scrollRange > 0) {
            scrollOffset.toFloat() / scrollRange
        } else 0f

        (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
    }

    suspend fun scrollToItem(index: Int) {
        gridState.scrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    suspend fun animateScrollToItem(index: Int) {
        gridState.animateScrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
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

    private data class GridMetrics(val rowHeight: Int, val spanCount: Int, val contentSize: Int)
}
