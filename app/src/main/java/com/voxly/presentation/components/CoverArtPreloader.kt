package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.voxly.presentation.ui.preloadAlbumArtRange

private const val PRELOAD_AHEAD = 5
private const val PRELOAD_BEHIND = 3

@Composable
fun LazyListCoverPreloader(
    listState: LazyListState,
    filePaths: List<String>
) {
    val firstVisibleIndex by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val visibleItemCount by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }
    CoverPreloader(
        firstVisibleIndex = firstVisibleIndex,
        visibleItemCount = visibleItemCount,
        filePaths = filePaths
    )
}

@Composable
fun LazyGridCoverPreloader(
    gridState: LazyGridState,
    filePaths: List<String>
) {
    val firstVisibleIndex by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex }
    }
    val visibleItemCount by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size }
    }
    CoverPreloader(
        firstVisibleIndex = firstVisibleIndex,
        visibleItemCount = visibleItemCount,
        filePaths = filePaths
    )
}

@Composable
private fun CoverPreloader(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    filePaths: List<String>
) {
    val context = LocalContext.current
    val preloadRange by remember(firstVisibleIndex, visibleItemCount, filePaths.size) {
        derivedStateOf {
            val start = (firstVisibleIndex - PRELOAD_BEHIND).coerceAtLeast(0)
            val end = (firstVisibleIndex + visibleItemCount + PRELOAD_AHEAD)
                .coerceAtMost(filePaths.lastIndex)
            start to end
        }
    }

    LaunchedEffect(preloadRange, filePaths.size) {
        val (start, end) = preloadRange
        if (start <= end && filePaths.isNotEmpty()) {
            preloadAlbumArtRange(context, filePaths, start, end)
        }
    }
}
