package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.voxly.R
import com.voxly.presentation.components.applySharedMemoryCache
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.RoleGradientBadge
import com.voxly.presentation.components.rememberRoleAccent
import com.voxly.presentation.theme.emphasizedTitleMedium

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumArtSection(
    albumArt: ByteArray?,
    fallbackBitmap: Bitmap? = null,
    onPickAlbumArt: () -> Unit,
    coverTag: String? = null,
    onZoomAlbumArt: () -> Unit,
    onRotateAlbumArt: () -> Unit,
    onRemoveAlbumArt: () -> Unit,
    filePath: String? = null,
    formatLabel: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedVisibilityScope? = null
) {
    val coverKey = coverTag ?: filePath?.let { createAlbumArtSharedElementKey(it) }
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.extraLarge
    val isAndroid12Plus = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val displayModel: Any? = albumArt ?: fallbackBitmap

    // 空态占位用角色渐变，filePath hash 保证同一文件颜色稳定
    val roleAccent = rememberRoleAccent(filePath ?: "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onPickAlbumArt),
            contentAlignment = Alignment.Center
        ) {
            val albumArtRequest = remember(displayModel, coverKey) {
                displayModel?.let { model ->
                    val memoryKey = coverKey ?: when (model) {
                        is ByteArray -> "album_art_${model.contentHashCode()}"
                        else -> "album_art_${model.hashCode()}"
                    }
                    ImageRequest.Builder(context)
                        .data(model)
                        .size(Size.ORIGINAL)
                        .applySharedMemoryCache(memoryKey, placeholderKey = coverKey)
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
                        EmptyAlbumArtContent(
                            accent = roleAccent.accent,
                            onAccent = roleAccent.onAccent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 身份 pill：文件名 + 格式，给页面"这是哪首歌"的锚点（不重复可编辑的歌名）
        val fileName = filePath?.substringAfterLast('/')
        if (!fileName.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = com.voxly.presentation.icons.appIconPainter(com.voxly.presentation.icons.AppIcon.AudioFile),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (formatLabel.isNullOrBlank()) fileName else "$fileName · $formatLabel",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty album art placeholder content.
 * Cookie9Sided 角色渐变徽章 + 大字，呼应专辑/艺术家占位语言。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyAlbumArtContent(
    accent: Color = MaterialTheme.colorScheme.tertiary,
    onAccent: Color = MaterialTheme.colorScheme.onTertiary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        RoleGradientBadge(
            painter = com.voxly.presentation.icons.appIconPainter(com.voxly.presentation.icons.AppIcon.MusicNote),
            contentDescription = stringResource(R.string.cd_album_art),
            accent = accent,
            onAccent = onAccent,
            badgeSize = 72.dp,
            iconSize = 32.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tap_to_add_album_art),
            style = emphasizedTitleMedium,
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
