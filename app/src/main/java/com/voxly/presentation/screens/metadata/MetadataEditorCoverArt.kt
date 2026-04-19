package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.voxly.R
import com.voxly.presentation.components.createAlbumArtSharedElementKey

/**
 * Album art section for metadata editor.
 *
 * Displays the original cover art from the audio file without any caching
 * or compression. Shows the exact bytes stored in the file's metadata.
 * Uses Coil for background decoding to prevent main thread jank.
 *
 * @param albumArt Raw cover art bytes from ViewModel (direct from audio file)
 * @param fallbackBitmap MediaStore fallback bitmap (shown if no embedded cover)
 * @param onPickAlbumArt Callback to open album art picker
 * @param coverTag Optional shared element transition tag
 * @param onZoomAlbumArt Callback to zoom/view the cover art
 * @param onRotateAlbumArt Callback to rotate the cover art
 * @param onRemoveAlbumArt Callback to remove the album art
 * @param filePath File path for shared element transition key
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumArtSection(
    albumArt: ByteArray?,
    fallbackBitmap: Bitmap? = null,
    onPickAlbumArt: () -> Unit,
    coverTag: String? = null,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    filePath: String? = null
) {
    val coverKey = filePath?.let { createAlbumArtSharedElementKey(it) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (coverKey != null) {
                    Modifier
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onPickAlbumArt),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val albumArtRequest = remember(albumArt) {
                albumArt?.let { bytes ->
                    ImageRequest.Builder(context)
                        .data(bytes)
                        .size(Size.ORIGINAL)
                        .memoryCacheKey("album_art_${bytes.contentHashCode()}")
                        .build()
                }
            }

            if (albumArt != null && albumArtRequest != null) {
                AsyncImage(
                    model = albumArtRequest,
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                EmptyAlbumArtContent()
            }
        }
    }
}

/**
 * Empty album art placeholder content.
 */
@Composable
fun EmptyAlbumArtContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            painter = com.voxly.presentation.icons.appIconPainter(com.voxly.presentation.icons.AppIcon.MusicNote),
            contentDescription = stringResource(R.string.cd_album_art),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tap_to_add_album_art),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Cover candidate thumbnail for online cover search results.
 */
@Composable
fun CoverCandidateThumbnail(
    coverArtUrl: String?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(coverArtUrl)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.cd_cover_thumbnail),
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
