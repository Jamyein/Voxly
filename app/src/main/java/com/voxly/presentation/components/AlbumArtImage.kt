package com.voxly.presentation.components

import android.content.Context
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import androidx.compose.ui.platform.LocalContext
import com.voxly.R
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

@Composable
fun NetworkCoverImage(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
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
    if (url.isNullOrBlank()) {
        placeholder()
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { /* silently fail for network images */ }
    )
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
    val density = LocalDensity.current
    val targetSizePx = with(density) { size.roundToPx() }
    val context = LocalContext.current
    val coverUriProvider = remember(context) { CoverUriProvider(context) }

    val model = remember(albumId, filePath, coverUriProvider) {
        when {
            albumId != null && albumId > 0 -> coverUriProvider.getCoverUri(albumId = albumId)
            !filePath.isNullOrBlank() -> coverUriProvider.getCoverUri(null, filePath)
            else -> null
        }
    }

    var loadFailed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
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
