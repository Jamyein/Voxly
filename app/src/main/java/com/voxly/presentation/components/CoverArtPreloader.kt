package com.voxly.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.presentation.components.AlbumCoverDecodeSize
import kotlinx.coroutines.delay

private const val PRELOAD_AHEAD_DEFAULT = 5
private const val PRELOAD_BEHIND_DEFAULT = 2
// 列表行封面（48-72dp 显示）的预加载像素：200px 覆盖所有密度的行封面显示，避免硬编码 300px
// 对网格封面（[AlbumCoverDecodeSize] 解码）造成小尺寸条目复用（INEXACT 兼容范围内会直接拿小图放大）。
private const val PRELOAD_LIST_PX = 200

/**
 * A cover source as resolved by the renderer: (filePath, mediaStoreAlbumId).
 * Must match the (albumId, filePath) pair passed to [AlbumArtImage] so the
 * resolved URI — and thus the Coil memory-cache key — hits.
 */
typealias CoverSource = Pair<String?, Long?>

@Composable
fun LazyListCoverPreloader(
    listState: LazyListState,
    covers: List<CoverSource>
) {
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleItemCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size } }
    CoverPreloader(
        firstVisibleIndex = firstVisibleIndex,
        visibleItemCount = visibleItemCount,
        covers = covers,
        preloadSizePx = PRELOAD_LIST_PX
    )
}

@Composable
fun LazyGridCoverPreloader(
    gridState: LazyGridState,
    covers: List<CoverSource>
) {
    val firstVisibleIndex by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    val visibleItemCount by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size } }
    val density = LocalDensity.current
    CoverPreloader(
        firstVisibleIndex = firstVisibleIndex,
        visibleItemCount = visibleItemCount,
        covers = covers,
        preloadSizePx = with(density) { AlbumCoverDecodeSize.roundToPx() }
    )
}

@Composable
private fun CoverPreloader(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    covers: List<CoverSource>,
    preloadSizePx: Int
) {
    if (covers.isEmpty()) return

    val imageLoader = LocalContext.current.imageLoader
    val platformContext = LocalContext.current
    val coverUriProvider = remember(platformContext) { CoverUriProvider(platformContext) }

    val preloadRange by remember(firstVisibleIndex, visibleItemCount, covers.size) {
        derivedStateOf {
            val listSize = covers.size
            val ahead = when {
                listSize <= 10 -> listSize.coerceAtMost(5)
                listSize <= 50 -> (listSize * 0.3).toInt().coerceAtLeast(PRELOAD_AHEAD_DEFAULT)
                else -> PRELOAD_AHEAD_DEFAULT
            }
            val behind = if (listSize <= 10) 0 else PRELOAD_BEHIND_DEFAULT
            val start = (firstVisibleIndex - behind).coerceAtLeast(0)
            val end = (firstVisibleIndex + visibleItemCount + ahead)
                .coerceAtMost(covers.lastIndex.coerceAtLeast(0))
            start to end
        }
    }

    LaunchedEffect(preloadRange) {
        delay(200)
        val (start, end) = preloadRange
        if (start <= end && end < covers.size) {
            covers.subList(start, end + 1).forEach { (path, albumId) ->
                if (path != null) {
                    val coverUri = coverUriProvider.getCoverUri(albumId, path)
                    if (coverUri != null) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(platformContext)
                                .data(coverUri)
                                .size(Size(preloadSizePx, preloadSizePx))
                                .precision(Precision.INEXACT)
                                .memoryCacheKey(coverUri.toString())
                                .build()
                        )
                    }
                }
            }
        }
    }
}