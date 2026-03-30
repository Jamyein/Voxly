package com.voxly.presentation.screens.metadata

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.components.NetworkAlbumArtImage
import com.voxly.presentation.components.sharedBoundsIfAvailable
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.theme.MaterialShapes

/**
 * Album art section component with click to pick.
 *
 * @param filePath The file path used for shared element transition key.
 *                 When provided, enables Container Transform animation from list to detail.
 */
@Composable
fun AlbumArtSection(
    albumArt: ByteArray?,
    onPickAlbumArt: () -> Unit,
    coverTag: String? = null,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    filePath: String? = null
) {
    // Detail page uses rounded rectangle shape (different from list item's cookie shape)
    // The sharedBounds transition will smoothly morph between these shapes
    val coverKey = filePath?.let { createAlbumArtSharedElementKey(it) }
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
            // Use Crossfade for smooth album art transitions
            // Note: Shared element transition is applied outside Crossfade for proper animation
            androidx.compose.animation.Crossfade(
                targetState = albumArt != null,
                label = "album_art_crossfade"
            ) { hasArt ->
                if (hasArt && albumArt != null) {
                    val bitmap = remember(albumArt.contentHashCode()) {
                        decodeAlbumArtPreview(albumArt)
                    }
                    if (bitmap != null) {
                        val albumArtModifier = Modifier.fillMaxSize()
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_album_art),
                            modifier = albumArtModifier
                        )
                    } else {
                        EmptyAlbumArtContent()
                    }
                } else {
                    EmptyAlbumArtContent()
                }
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
