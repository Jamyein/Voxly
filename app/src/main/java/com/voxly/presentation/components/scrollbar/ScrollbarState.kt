package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlin.math.max

/**
 * Core read-only state of a scrollable list/grid, consumed by [M3EScrollbar].
 *
 * This interface intentionally stays minimal. Derived properties
 * (e.g. *isAtStart*, *isAtEnd*) are computed by the caller or
 * [ScrollbarStateHolder] so that the scrollbar can decide how to react.
 */
interface ScrollbarState {
    /** Total number of items in the list/grid. */
    val totalItemsCount: Int

    /** Whether the list is currently being scrolled by the user. */
    val isScrollInProgress: Boolean

    /** Whether the content is scrollable (visible items < total items). */
    val isScrollable: Boolean

    /** Normalized scroll offset in the range [0, 1]. */
    val normalizedThumbOffset: Float

    /** Index of the first fully visible item. */
    val firstVisibleIndex: Int

    /** Index of the last fully visible item. */
    val lastVisibleIndex: Int

    /** Scroll by a velocity-driven amount (used for inertia after drag end). */
    suspend fun scrollByVelocity(velocity: Float)

    /** Jump to the given item index without animation. */
    suspend fun scrollToItem(index: Int)

    /** Smoothly animate to the given item index. */
    suspend fun animateScrollToItem(index: Int)
}

class LazyListScrollbarState(
    val listState: LazyListState
) : ScrollbarState {

    private val layoutInfo get() = listState.layoutInfo

    override val totalItemsCount: Int by derivedStateOf {
        layoutInfo.totalItemsCount
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        listState.isScrollInProgress
    }

    override val isScrollable: Boolean by derivedStateOf {
        layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount
    }

    override val normalizedThumbOffset: Float by derivedStateOf {
        val total = totalItemsCount
        if (total <= 1) return@derivedStateOf 0f
        val visibleCount = lastVisibleIndex - firstVisibleIndex + 1
        val maxFirstIndex = total - visibleCount
        if (maxFirstIndex <= 0) return@derivedStateOf 0f
        firstVisibleIndex.toFloat() / maxFirstIndex
    }

    override val firstVisibleIndex: Int by derivedStateOf {
        layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    }

    override val lastVisibleIndex: Int by derivedStateOf {
        layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }

    override suspend fun scrollToItem(index: Int) {
        listState.scrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    override suspend fun animateScrollToItem(index: Int) {
        listState.animateScrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        listState.scroll {
            scrollBy(velocity / VELOCITY_DIVISOR)
        }
    }
}

class LazyGridScrollbarState(
    val gridState: LazyGridState
) : ScrollbarState {

    private val layoutInfo get() = gridState.layoutInfo

    override val totalItemsCount: Int by derivedStateOf {
        layoutInfo.totalItemsCount
    }

    override val isScrollInProgress: Boolean by derivedStateOf {
        gridState.isScrollInProgress
    }

    override val isScrollable: Boolean by derivedStateOf {
        layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount
    }

    override val normalizedThumbOffset: Float by derivedStateOf {
        val total = totalItemsCount
        if (total <= 1) return@derivedStateOf 0f
        val visibleCount = lastVisibleIndex - firstVisibleIndex + 1
        val maxFirstIndex = total - visibleCount
        if (maxFirstIndex <= 0) return@derivedStateOf 0f
        firstVisibleIndex.toFloat() / maxFirstIndex
    }

    override val firstVisibleIndex: Int by derivedStateOf {
        layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    }

    override val lastVisibleIndex: Int by derivedStateOf {
        layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }

    override suspend fun scrollToItem(index: Int) {
        gridState.scrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    override suspend fun animateScrollToItem(index: Int) {
        gridState.animateScrollToItem(index.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }

    override suspend fun scrollByVelocity(velocity: Float) {
        gridState.scroll {
            scrollBy(velocity / VELOCITY_DIVISOR)
        }
    }
}

/** Internal constant to avoid magic number. */
private const val VELOCITY_DIVISOR = 5f
