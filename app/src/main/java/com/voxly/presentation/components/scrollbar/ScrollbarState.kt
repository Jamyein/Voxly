package com.voxly.presentation.components.scrollbar

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlin.math.max

interface ScrollbarState {
    val contentSize: Int
    val scrollOffset: Int
    val viewportSize: Int
    val isScrollInProgress: Boolean
    val totalItemsCount: Int
    val currentItemIndex: Int
    suspend fun scrollByVelocity(velocity: Float)
}

class LazyListScrollbarState(
    val listState: LazyListState
) : ScrollbarState {

    private val layoutInfo get() = listState.layoutInfo

    override val contentSize: Int by derivedStateOf {
        layoutInfo.viewportEndOffset
    }

    override val scrollOffset: Int by derivedStateOf {
        layoutInfo.viewportStartOffset
    }

    override val viewportSize: Int by derivedStateOf {
        max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
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

    override suspend fun scrollByVelocity(velocity: Float) {
        listState.scroll {
            scrollBy(velocity / 5f)
        }
    }
}

class LazyGridScrollbarState(
    val gridState: LazyGridState
) : ScrollbarState {

    private val layoutInfo get() = gridState.layoutInfo

    override val contentSize: Int by derivedStateOf {
        layoutInfo.viewportEndOffset
    }

    override val scrollOffset: Int by derivedStateOf {
        layoutInfo.viewportStartOffset
    }

    override val viewportSize: Int by derivedStateOf {
        max(0, layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
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

    override suspend fun scrollByVelocity(velocity: Float) {
        gridState.scroll {
            scrollBy(velocity / 5f)
        }
    }
}
