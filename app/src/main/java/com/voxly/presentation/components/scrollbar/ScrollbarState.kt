package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

    private var _contentSize by mutableIntStateOf(0)
    private var _avgItemSize by mutableIntStateOf(100)
    private var isInitialized = false

    override val contentSize: Int
        get() {
            if (!isInitialized && layoutInfo.totalItemsCount > 0) {
                _avgItemSize = computeAvgItemSize()
                _contentSize = computeContentSize()
                isInitialized = true
            }
            return _contentSize
        }

    private fun computeContentSize(): Int {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return 0
        val totalItemsMinus1 = (info.totalItemsCount - 1).coerceAtLeast(0)
        if (totalItemsMinus1 == 0) return info.viewportEndOffset
        val lastItem = info.visibleItemsInfo.lastOrNull()
        val firstItem = info.visibleItemsInfo.firstOrNull()
        if (lastItem != null && firstItem != null) {
            val visibleRange = lastItem.offset + lastItem.size - firstItem.offset
            val visibleCount = info.visibleItemsInfo.size
            if (visibleCount > 0) {
                val avgSize = visibleRange / visibleCount
                val remainingItems = info.totalItemsCount - info.visibleItemsInfo.size
                return (lastItem.offset + lastItem.size) + (remainingItems * avgSize)
            } else {
                return _avgItemSize * info.totalItemsCount
            }
        } else {
            return _avgItemSize * info.totalItemsCount
        }
    }

    private fun computeAvgItemSize(): Int {
        val info = layoutInfo
        val visibleItems = info.visibleItemsInfo
        if (visibleItems.isEmpty()) return 100
        return (visibleItems.last().offset + visibleItems.last().size - visibleItems.first().offset) / visibleItems.size
    }

    override val scrollOffset: Int by derivedStateOf {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return@derivedStateOf 0
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
        listState.firstVisibleItemIndex
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

    private var _contentSize by mutableIntStateOf(0)
    private var isInitialized = false

    override val contentSize: Int
        get() {
            if (!isInitialized && layoutInfo.totalItemsCount > 0) {
                _contentSize = computeContentSize()
                isInitialized = true
            }
            return _contentSize
        }

    private fun computeContentSize(): Int {
        val info = layoutInfo
        val visibleItems = info.visibleItemsInfo
        if (visibleItems.isEmpty()) return 0

        val itemsByRow = visibleItems.groupBy { it.row }
        val sortedRows = itemsByRow.keys.sorted()
        val spanCount = itemsByRow.values.maxOfOrNull { it.size } ?: 1

        val rowHeights = sortedRows.mapNotNull { row ->
            val itemsInRow = itemsByRow[row]
            if (itemsInRow != null && itemsInRow.size >= spanCount) {
                itemsInRow.firstOrNull()?.let { it.size.height }
            } else if (itemsInRow != null && itemsInRow.size == 1) {
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
        return totalRows * rowHeight
    }

    override val scrollOffset: Int by derivedStateOf {
        val info = layoutInfo
        if (info.totalItemsCount == 0) return@derivedStateOf 0
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
        gridState.firstVisibleItemIndex
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
}
