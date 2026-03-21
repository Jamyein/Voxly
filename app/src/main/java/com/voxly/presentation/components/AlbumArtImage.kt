package com.voxly.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.findCachedAlbumArt
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import kotlinx.coroutines.Dispatchers
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
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val albumArtBitmap = produceAlbumArtBitmap(
        filePath = filePath,
        mediaStoreAlbumId = mediaStoreAlbumId
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
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) },
    onDimensionsLoaded: ((width: Int, height: Int) -> Unit)? = null
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                loadImageBitmapFromUrl(url)?.asAndroidBitmap()
            }
            bitmap = loaded
            if (loaded != null) {
                onDimensionsLoaded?.invoke(loaded.width, loaded.height)
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                placeholder()
            }
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
 */
@Composable
private fun produceAlbumArtBitmap(
    filePath: String?,
    mediaStoreAlbumId: Long?
): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath,
        key2 = mediaStoreAlbumId
    ) {
        value = withContext(Dispatchers.IO) {
            // First try: local file embedded album art
            if (!filePath.isNullOrBlank()) {
                val localArt = loadLocalAlbumArt(filePath)
                if (localArt != null) {
                    return@withContext localArt
                }
            }

            // Second try: MediaStore album art
            if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
                val mediaStoreArt = loadMediaStoreAlbumArt(context, mediaStoreAlbumId)
                if (mediaStoreArt != null) {
                    return@withContext mediaStoreArt
                }
            }

            // Third try: folder cover art (already included in loadLocalAlbumArt)
            // If filePath is provided, loadLocalAlbumArt already tried folder covers

            null
        }
    }
}
