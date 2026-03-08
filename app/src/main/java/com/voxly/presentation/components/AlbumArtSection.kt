package com.voxly.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.decodeBitmapFromBytes

/**
 * Album art display section with placeholder and click handler.
 */
@Composable
fun AlbumArtSection(
    albumArt: ByteArray?,
    onPickAlbumArt: () -> Unit,
    modifier: Modifier = Modifier,
    coverTag: String? = null,
    onZoomAlbumArt: (() -> Unit)? = null,
    onRotateAlbumArt: (() -> Unit)? = null,
    onRemoveAlbumArt: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onPickAlbumArt),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (albumArt != null) {
                val bitmap = remember(albumArt.contentHashCode()) {
                    decodeAlbumArtPreview(albumArt)
                }
                if (bitmap != null) {
                    // Note: coverTag is kept for potential future SharedElement transitions
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize()
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

@Composable
private fun EmptyAlbumArtContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Icon(
            painter = appIconPainter(AppIcon.MusicNote),
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

private fun decodeAlbumArtPreview(
    bytes: ByteArray,
    targetSizePx: Int = 1024
): Bitmap? {
    return decodeBitmapFromBytes(bytes, targetSizePx)
}
