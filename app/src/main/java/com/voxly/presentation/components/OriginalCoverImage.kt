package com.voxly.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import com.voxly.data.local.cover.CoverUriProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Displays the original cover art from the audio file.
 * 
 * This is used in the metadata editor to show the raw, unprocessed cover art
 * without any caching or compression. It shows the exact bytes stored in the
 * audio file's metadata.
 * 
 * Loading priority:
 * 1. Provided albumArtBytes (from ViewModel state - already loaded)
 * 2. Direct file reading with TagLib (fallback)
 * 3. MediaStore/folder cover (last resort)
 * 
 * @param filePath The audio file path
 * @param albumArtBytes Raw cover art bytes from ViewModel (preferred)
 * @param albumId MediaStore album ID (fallback)
 * @param size Target display size
 */
@Composable
fun OriginalCoverImage(
    filePath: String,
    albumArtBytes: ByteArray?,
    albumId: Long? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. Use provided bytes (from ViewModel state)
            albumArtBytes != null -> {
                val bitmap = remember(albumArtBytes) {
                    BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.size)
                        ?.asImageBitmap()
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale
                    )
                } else {
                    placeholder()
                }
            }
            
            // 2. Fallback to MediaStore/folder cover
            else -> {
                AlbumArtImage(
                    albumId = albumId,
                    filePath = filePath,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    size = size,
                    contentScale = contentScale,
                    crossfade = false, // No animation for metadata editor
                    placeholder = placeholder
                )
            }
        }
    }
}

/**
 * Displays original cover art with zoom support for preview dialog.
 */
@Composable
fun OriginalCoverImageZoomable(
    albumArtBytes: ByteArray?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (albumArtBytes != null) {
            val bitmap = remember(albumArtBytes) {
                BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.size)
                    ?.asImageBitmap()
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                DefaultAlbumArtPlaceholder(size = 200.dp)
            }
        } else {
            DefaultAlbumArtPlaceholder(size = 200.dp)
        }
    }
}
