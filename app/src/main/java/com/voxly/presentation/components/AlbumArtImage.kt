package com.voxly.presentation.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
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
    preResolvedUri: Uri? = null,
    shimmerWhileLoading: Boolean = true,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = size) }
) {
    val context = LocalContext.current
    val coverUriProvider = remember(context) { CoverUriProvider(context) }

    var model by remember(albumId, filePath, preResolvedUri) { mutableStateOf<Uri?>(preResolvedUri) }
    var isLoading by remember(albumId, filePath, preResolvedUri) { mutableStateOf(preResolvedUri == null) }
    var loadFailed by remember(albumId, filePath, preResolvedUri) { mutableStateOf(false) }

    LaunchedEffect(albumId, filePath, coverUriProvider, preResolvedUri) {
        if (preResolvedUri == null) {
            isLoading = true
            loadFailed = false
            model = coverUriProvider.getCoverUri(albumId = albumId, filePath = filePath)
            isLoading = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !loadFailed) {
            val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .scale(Scale.FILL)
                .crossfade(crossfade)

            model?.let { uri ->
                val cacheKey = uri.toString()
                imageRequestBuilder
                    .memoryCacheKey(cacheKey)
                    .placeholderMemoryCacheKey(cacheKey)
            }

            AsyncImage(
                model = imageRequestBuilder.build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { loadFailed = true }
            )
        } else if (isLoading && shimmerWhileLoading) {
            ShimmerAlbumArtPlaceholder()
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

@Composable
fun ShimmerAlbumArtPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                -200f at 0 with LinearEasing
                400f at 1200 with LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        surfaceColor,
                        highlightColor,
                        surfaceColor
                    ),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + 200f, 0f)
                )
            )
    )
}
