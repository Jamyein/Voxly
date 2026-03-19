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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import kotlinx.coroutines.CoroutineScope
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

    val albumArtBitmap = produceAlbumArtBitmap(
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
    val context = LocalContext.current

    val imageBitmap = produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = url
    ) {
        value = withContext(Dispatchers.IO) {
            loadImageBitmapFromUrl(url)?.asAndroidBitmap()
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = imageBitmap.value
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
 * Internal helper to produce album art bitmap from multiple sources.
 * Uses DisposableEffect to support cancellation during list scrolling.
 */
@Composable
private fun produceAlbumArtBitmap(
    filePath: String?,
    mediaStoreAlbumId: Long?,
    targetSizePx: Int
): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current

    // 1. 尝试从内存缓存同步获取（调用 ImageLoader 的缓存查找）
    val cachedBitmap = remember(filePath, mediaStoreAlbumId, targetSizePx) {
        findCachedAlbumArt(filePath, mediaStoreAlbumId, targetSizePx)
    }

    // 2. 如果缓存命中，直接返回
    if (cachedBitmap != null) {
        return remember { mutableStateOf(cachedBitmap) }
    }

    // 3. 异步加载，带取消支持
    var loadingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(filePath, mediaStoreAlbumId, targetSizePx) {
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            val loaded = loadAlbumArtInternal(context, filePath, mediaStoreAlbumId, targetSizePx)
            loadingBitmap = loaded
        }
        onDispose {
            job.cancel()
        }
    }

    return loadingBitmap
}

/**
 * 从 ImageLoader 缓存中查找已缓存的封面
 */
private fun findCachedAlbumArt(filePath: String?, albumId: Long?, sizePx: Int): Bitmap? {
    if (!filePath.isNullOrBlank()) {
        localAlbumArtCache[getLocalArtCacheKey(filePath, sizePx)]?.let { return it }
    }
    if (albumId != null && albumId > 0) {
        mediaStoreAlbumCache[getMediaStoreCacheKey(albumId, sizePx)]?.let { return it }
    }
    return null
}

/**
 * 内部加载函数，生成尺寸目标的像素值
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

/**
 * 带尺寸的本地封面加载 - Chunk 5 中定义
 */
private fun loadLocalAlbumArtSized(filePath: String, targetSizePx: Int): Bitmap? {
    // Stub - will be implemented in Chunk 5
    return loadLocalAlbumArtSizedImpl(filePath, targetSizePx)
}

/**
 * 带尺寸的 MediaStore 封面加载 - Chunk 5 中定义
 */
private fun loadMediaStoreAlbumArtSized(context: Context, albumId: Long, targetSizePx: Int): Bitmap? {
    // Stub - will be implemented in Chunk 5
    return loadMediaStoreAlbumArtSizedImpl(context, albumId, targetSizePx)
}

// Temporary stub implementations that delegate to existing functions
private fun loadLocalAlbumArtSizedImpl(filePath: String, targetSizePx: Int): Bitmap? {
    return try {
        com.voxly.presentation.ui.loadLocalAlbumArt(filePath)
    } catch (e: Exception) {
        null
    }
}

private fun loadMediaStoreAlbumArtSizedImpl(context: Context, albumId: Long, targetSizePx: Int): Bitmap? {
    return try {
        com.voxly.presentation.ui.loadMediaStoreAlbumArt(context, albumId)
    } catch (e: Exception) {
        null
    }
}
