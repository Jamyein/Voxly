package com.voxly.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import com.voxly.R
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.loadAlbumArtThumbnail
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NetworkCoverImage(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onDimensionsLoaded: ((width: Int, height: Int) -> Unit)? = null,
    placeholder: @Composable () -> Unit = {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = appIconPainter(AppIcon.MusicNote),
                contentDescription = stringResource(R.string.cd_no_cover),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
) {
    val imageBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = url
    ) {
        value = withContext(Dispatchers.IO) {
            loadImageBitmapFromUrl(url)?.let { imageBitmap ->
                onDimensionsLoaded?.invoke(imageBitmap.width, imageBitmap.height)
                imageBitmap.asAndroidBitmap()
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val bitmap = imageBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            placeholder()
        }
    }
}

@Composable
fun AlbumArtImage(
    albumId: Long? = null,
    filePath: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetSizePx = with(density) { size.roundToPx() }
    val coverUriProvider = remember { CoverUriProvider(context) }
    
    val coverUri = remember(albumId, filePath) {
        coverUriProvider.getCoverUri(albumId = albumId, filePath = filePath)
    }
    
    val embeddedBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath,
        key2 = targetSizePx
    ) {
        value = if (coverUri == null && !filePath.isNullOrBlank()) {
            loadAlbumArtThumbnail(context, filePath, targetSizePx)
        } else null
    }
    
    var loadFailed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            coverUri != null && !loadFailed -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(coverUri)
                        .size(targetSizePx)
                        .scale(Scale.FILL)
                        .crossfade(crossfade)
                        .build(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    onError = { loadFailed = true }
                )
            }
            embeddedBitmap != null -> {
                Image(
                    bitmap = embeddedBitmap!!.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
            else -> {
                placeholder()
            }
        }
    }
}

@Composable
fun DefaultAlbumArtPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = appIconPainter(AppIcon.MusicNote),
            contentDescription = stringResource(R.string.cd_no_cover),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(size.coerceAtMost(24.dp))
        )
    }
}
