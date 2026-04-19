package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.BlackholeDecoder
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.voxly.data.local.cover.CoverUriProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PRELOAD_AHEAD_DEFAULT = 3
private const val PRELOAD_BEHIND_DEFAULT = 1
private const val HOT_LAYER_AHEAD = 2

@Composable
fun LazyListCoverPreloader(
    listState: LazyListState,
    filePaths: List<String>
) {
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleItemCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
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
    val firstVisibleIndex by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    val visibleItemCount by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size } }
    CoverPreloader(
        firstVisibleIndex = firstVisibleIndex,
        visibleItemCount = visibleItemCount,
        filePaths = filePaths
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun CoverPreloader(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    filePaths: List<String>
) {
    if (filePaths.isEmpty()) return

    val imageLoader = LocalContext.current.imageLoader
    val platformContext = LocalContext.current
    val coverUriProvider = remember(platformContext) { CoverUriProvider(platformContext) }
    val scope = rememberCoroutineScope()

    val preloadRange by remember(firstVisibleIndex, visibleItemCount, filePaths.size) {
        derivedStateOf {
            val listSize = filePaths.size
            val ahead = when {
                listSize <= 10 -> listSize.coerceAtMost(5)
                listSize <= 50 -> (listSize * 0.3).toInt().coerceAtLeast(PRELOAD_AHEAD_DEFAULT)
                else -> PRELOAD_AHEAD_DEFAULT
            }
            val behind = if (listSize <= 10) 0 else PRELOAD_BEHIND_DEFAULT
            val start = (firstVisibleIndex - behind).coerceAtLeast(0)
            val end = (firstVisibleIndex + visibleItemCount + ahead)
                .coerceAtMost(filePaths.lastIndex.coerceAtLeast(0))
            start to end
        }
    }

    LaunchedEffect(preloadRange, filePaths.size) {
        delay(100)
        val (start, end) = preloadRange
        if (start <= end && end < filePaths.size) {
            scope.launch {
                val hotEnd = (end + HOT_LAYER_AHEAD).coerceAtMost(filePaths.lastIndex)

                filePaths.subList(start, hotEnd + 1).forEach { path ->
                    val coverUri = coverUriProvider.getCoverUri(null, path)
                    if (coverUri != null) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(platformContext)
                                .data(coverUri)
                                .build()
                        )
                    }
                }

                val coldStart = hotEnd + 1
                if (coldStart <= filePaths.lastIndex) {
                    filePaths.subList(coldStart, filePaths.size).forEach { path ->
                        val coverUri = coverUriProvider.getCoverUri(null, path)
                        if (coverUri != null) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(platformContext)
                                    .data(coverUri)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .decoderFactory(BlackholeDecoder.Factory())
                                    .build()
                            )
                        }
                    }
                }
            }
        }
    }
}