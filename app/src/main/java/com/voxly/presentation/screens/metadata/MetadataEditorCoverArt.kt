package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.voxly.R
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.voxly.presentation.components.LocalSharedTransitionScope
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
    val coverKey = coverTag ?: filePath?.let { createAlbumArtSharedElementKey(it) }
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.extraLarge
    val isAndroid12Plus = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val displayModel: Any? = albumArt ?: fallbackBitmap

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onPickAlbumArt),
        contentAlignment = Alignment.Center
    ) {
        val albumArtRequest = remember(displayModel, coverKey) {
            displayModel?.let { model ->
                ImageRequest.Builder(context)
                    .data(model)
                    .size(Size.ORIGINAL)
                    .memoryCacheKey(coverKey ?: when (model) {
                        is ByteArray -> "album_art_${model.contentHashCode()}"
                        else -> "album_art_${model.hashCode()}"
                    })
                    .placeholderMemoryCacheKey(coverKey)
                    .build()
            }
        }

        if (isAndroid12Plus && albumArtRequest != null) {
            AsyncImage(
                model = albumArtRequest,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 0.84f
                        scaleY = 0.84f
                        translationY = 34.dp.toPx()
                        alpha = 0.82f
                    }
                    .clip(shape)
                    .blur(
                        radius = 44.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    ),
                contentScale = ContentScale.Crop
            )
        }

        val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedContentScope = LocalNavAnimatedContentScope.current
    
    val hasSharedElement = coverKey != null && sharedTransitionScope != null && animatedContentScope != null
    val sharedModifier = if (hasSharedElement) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedTransitionScope.rememberSharedContentState(key = coverKey),
                animatedVisibilityScope = animatedContentScope
            )
        }
    } else {
        Modifier
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier)
                .clip(shape)
        ) {
            if (albumArtRequest != null) {
                AsyncImage(
                    model = albumArtRequest,
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier.fillMaxSize().clip(shape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
        modifier = Modifier
            .padding(16.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Icon(
            painter = com.voxly.presentation.icons.appIconPainter(com.voxly.presentation.icons.AppIcon.MusicNote),
            contentDescription = stringResource(R.string.cd_album_art),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
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
