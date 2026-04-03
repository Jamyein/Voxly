package com.voxly.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Gramophone-style album art image component using Coil 3.
 * 
 * Loading priority:
 * 1. MediaStore album art URI (fast, system cached)
 * 2. Folder cover files (cover.jpg, folder.jpg, etc.)
 * 
 * Note: This is for list/playback screens only. Metadata editor uses OriginalCoverImage
 * to display raw cover art from audio files.
 *
 * @param albumId MediaStore album ID for MediaStore lookup
 * @param filePath Audio file path for folder cover lookup
 * @param size Target display size (used for Coil's size-aware decoding)
 * @param crossfade Enable crossfade animation (default: true for lists, false for playback)
 */
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
    
    // Get cover URI (MediaStore or folder cover)
    val coverUri = remember(albumId, filePath) {
        val provider = CoverUriProvider(context)
        provider.getCoverUri(albumId = albumId, filePath = filePath)
    }
    
    var loadFailed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (coverUri != null && !loadFailed) {
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
        } else {
            placeholder()
        }
    }
}

/**
 * Placeholder for album art when no image is available.
 */
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
