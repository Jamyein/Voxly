package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.components.NetworkAlbumArtImage
import com.voxly.presentation.components.sharedBoundsIfAvailable
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.theme.MaterialShapes
import com.voxly.presentation.ui.loadAlbumArtThumbnail
import com.voxly.presentation.ui.loadAlbumArtOriginalBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album art section component with progressive loading.
 * Displays thumbnail first for fast shared element transition,
 * then crossfades to original resolution once loaded.
 *
 * @param filePath The file path used for shared element transition key.
 *                 When provided, enables Container Transform animation from list to detail.
 */
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

    // Layer 1: Thumbnail for fast display and shared element transition
    val cachedThumbnail by produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath
    ) {
        value = withContext(Dispatchers.IO) {
            if (!filePath.isNullOrBlank()) {
                loadAlbumArtThumbnail(context, filePath, 512)
            } else null
        }
    }

    // Layer 2: Original resolution for detail display
    val originalBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = filePath,
        key2 = albumArt
    ) {
        value = withContext(Dispatchers.IO) {
            when {
                albumArt != null -> decodeAlbumArtPreview(albumArt, 2048)
                !filePath.isNullOrBlank() -> loadAlbumArtOriginalBitmap(context, filePath, 2048)
                else -> null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (coverKey != null) {
                    Modifier.sharedBoundsIfAvailable(key = coverKey)
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
            // Progressive display: original > edited albumArt > thumbnail > fallback
            val displayBitmap = originalBitmap ?: run {
                when {
                    albumArt != null -> remember(albumArt.contentHashCode()) {
                        decodeAlbumArtPreview(albumArt)
                    }
                    cachedThumbnail != null -> cachedThumbnail
                    else -> fallbackBitmap
                }
            }

            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier.fillMaxSize()
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
    NetworkAlbumArtImage(
        url = coverArtUrl,
        contentDescription = stringResource(R.string.cd_cover_thumbnail),
        modifier = modifier,
        placeholder = {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.cd_no_cover),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
