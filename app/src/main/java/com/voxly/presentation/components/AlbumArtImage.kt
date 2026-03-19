package com.voxly.presentation.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.findCachedAlbumArt
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.ui.loadLocalAlbumArtSized
import com.voxly.presentation.ui.loadMediaStoreAlbumArtSized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified album art image composable that supports multiple sources:
 * 1. Local file embedded album art
 * 2. MediaStore album art (if albumId provided)
 * 3. Folder cover art (cover.jpg, folder.jpg, etc.)
 */
@Composable
fun AlbumArtImage(
    filePath: String?,
    mediaStoreAlbumId: Long? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    targetSize: Dp = size,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { targetSize.toPx().toInt() }

    val albumArtBitmap = produceAlbumArtBitmapState(
        filePath = filePath,
        mediaStoreAlbumId = mediaStoreAlbumId,
        targetSizePx = targetSizePx
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = albumArtBitmap.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                contentScale = contentScale
            )
        } else {
            placeholder()
        }
    }
}

/**
 * Network album art image composable for loading covers from URLs.
 */
@Composable
fun NetworkAlbumArtImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                loadImageBitmapFromUrl(url)?.asAndroidBitmap()
            }
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                contentScale = contentScale
            )
        } else {
            placeholder()
        }
    }
}

/**
 * Default placeholder for album art when no image is available.
 */
@Composable
fun DefaultAlbumArtPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Icon(
        painter = appIconPainter(AppIcon.MusicNote),
        contentDescription = stringResource(R.string.cd_no_cover),
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(size.coerceAtMost(24.dp))
    )
}

/**
 * Internal helper to produce album art bitmap state from multiple sources.
 * Uses DisposableEffect to support cancellation during list scrolling.
 */
@Composable
private fun produceAlbumArtBitmapState(
    filePath: String?,
    mediaStoreAlbumId: Long?,
    targetSizePx: Int
): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current

    // 1. Try to get from memory cache synchronously
    val cachedBitmap = remember(filePath, mediaStoreAlbumId, targetSizePx) {
        findCachedAlbumArt(filePath, mediaStoreAlbumId, targetSizePx)
    }

    // 2. If cache hit, return immediately
    if (cachedBitmap != null) {
        return remember { mutableStateOf(cachedBitmap) }
    }

    // 3. Async loading with cancellation support
    val loadingState = remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(filePath, mediaStoreAlbumId, targetSizePx) {
        val job = scope.launch {
            val loaded = loadAlbumArtInternal(context, filePath, mediaStoreAlbumId, targetSizePx)
            loadingState.value = loaded
        }
        onDispose {
            job.cancel()
        }
    }

    return loadingState
}

/**
 * Internal loading function
 */
private suspend fun loadAlbumArtInternal(
    context: Context,
    filePath: String?,
    mediaStoreAlbumId: Long?,
    targetSizePx: Int
): Bitmap? = withContext(Dispatchers.IO) {
    if (!filePath.isNullOrBlank()) {
        loadLocalAlbumArtSized(filePath, targetSizePx)?.let { return@withContext it }
    }
    if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
        loadMediaStoreAlbumArtSized(context, mediaStoreAlbumId, targetSizePx)?.let { return@withContext it }
    }
    null
}
