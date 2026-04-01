package com.voxly.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.ui.loadAlbumArtThumbnail
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    val density = LocalDensity.current
    val albumArtBitmap = produceAlbumArtBitmap(
        filePath = filePath,
        mediaStoreAlbumId = mediaStoreAlbumId,
        targetSizePx = with(density) { size.toPx().toInt() }
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val bitmap = albumArtBitmap.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                placeholder()
            }
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
    mediaStoreAlbumId: Long?,
    targetSizePx: Int
): androidx.compose.runtime.State<Bitmap?> {
    val context = LocalContext.current
    return androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath,
        key2 = mediaStoreAlbumId
    ) {
        value = withContext(Dispatchers.IO) {
            // First try: MediaStore album art (default path, fastest)
            if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
                val mediaStoreArt = loadMediaStoreAlbumArt(context, mediaStoreAlbumId)
                if (mediaStoreArt != null) {
                    return@withContext mediaStoreArt
                }
            }

            // Second try: file-level cached thumbnail (embedded art)
            if (!filePath.isNullOrBlank()) {
                val localArt = loadAlbumArtThumbnail(
                    context = context,
                    filePath = filePath,
                    targetSizePx = targetSizePx
                )
                if (localArt != null) {
                    return@withContext localArt
                }
            }

            // Third try: folder cover art (cover.jpg, folder.jpg, etc.)
            if (!filePath.isNullOrBlank()) {
                val folderArt = loadFolderCoverArt(filePath, targetSizePx)
                if (folderArt != null) {
                    return@withContext folderArt
                }
            }

            null
        }
    }
}

/**
 * Loads folder cover art from the parent directory of the audio file.
 */
private fun loadFolderCoverArt(filePath: String, targetSizePx: Int): Bitmap? {
    val folder = File(filePath).parentFile ?: return null
    val coverFileNames = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "album.jpg", "album.png")

    for (fileName in coverFileNames) {
        val coverFile = File(folder, fileName)
        if (coverFile.exists()) {
            return try {
                decodeBitmapFromFile(coverFile.absolutePath, targetSizePx)
            } catch (e: Exception) {
                null
            }
        }
    }
    return null
}

/**
 * Decodes a bitmap from file with sampling to reduce memory usage.
 */
private fun decodeBitmapFromFile(filePath: String, targetSize: Int): Bitmap? {
    val options = android.graphics.BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    android.graphics.BitmapFactory.decodeFile(filePath, options)

    var sampleSize = 1
    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
        sampleSize *= 2
    }

    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions)
}
