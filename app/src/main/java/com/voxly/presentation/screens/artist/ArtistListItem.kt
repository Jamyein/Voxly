package com.voxly.presentation.screens.artist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.ArtistListItemState
import androidx.compose.foundation.layout.aspectRatio
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.components.rememberRoleAccent
import com.voxly.presentation.components.roleAccentGradient
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.components.createArtistNameSharedElementKey
import androidx.compose.animation.core.spring
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.MaterialShapes
import com.voxly.presentation.theme.scaleOnPress

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistListItem(
    artist: ArtistListItemState,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val avatarKey = createArtistAvatarSharedElementKey(artist.name)
    val artistNameKey = createArtistNameSharedElementKey(artist.name)
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = if (canUseSharedTransition) {
                with(sharedTransitionScope) {
                    Modifier
                        .size(48.dp)
                        .sharedElement(
                            rememberSharedContentState(key = avatarKey),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .clip(MaterialShapes.Sunny.toShape())
                }
            } else {
                Modifier
                    .size(48.dp)
                    .clip(MaterialShapes.Sunny.toShape())
            },
            contentAlignment = Alignment.Center
        ) {
            if (!artist.coverPath.isNullOrBlank()) {
                AlbumArtImage(
                    filePath = artist.coverPath,
                    albumId = artist.coverAlbumId,
                    contentDescription = null,
                    size = 48.dp,
                    modifier = Modifier.fillMaxSize(),
                    clipShape = MaterialShapes.Sunny.toShape()
                )
            } else {
                DefaultAlbumArtPlaceholder(size = 48.dp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = artistNameKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring() }
                        )
                    }
                } else Modifier
            )
            Text(
                text = stringResource(R.string.album_count, artist.albumCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.track_count, artist.trackCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistGridItem(
    artist: ArtistListItemState,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val avatarKey = createArtistAvatarSharedElementKey(artist.name)
    val artistNameKey = createArtistNameSharedElementKey(artist.name)
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
    val albumCountText = stringResource(R.string.album_count, artist.albumCount)
    val trackCountText = stringResource(R.string.track_count, artist.trackCount)
    val infoText = remember(albumCountText, trackCountText) { "$albumCountText · $trackCountText" }

    // 无封面时用角色渐变 + 首字母占位；name hash 保证同一艺术家颜色稳定（无需调用点传 index）
    val roleAccent = rememberRoleAccent(artist.name)

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = if (canUseSharedTransition) {
                with(sharedTransitionScope) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElement(
                            rememberSharedContentState(key = avatarKey),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .clickable(interactionSource = interactionSource, onClick = onClick)
                        .clip(MaterialShapes.Sunny.toShape())
                        .scaleOnPress(interactionSource, label = "artistGridItemScale")
                }
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(interactionSource = interactionSource, onClick = onClick)
                    .clip(MaterialShapes.Sunny.toShape())
                    .scaleOnPress(interactionSource, label = "artistGridItemScale")
            },
            contentAlignment = Alignment.Center
        ) {
            if (!artist.coverPath.isNullOrBlank()) {
                AlbumArtImage(
                    filePath = artist.coverPath,
                    albumId = artist.coverAlbumId,
                    contentDescription = null,
                    size = 200.dp,
                    modifier = Modifier.fillMaxSize(),
                    clipShape = MaterialShapes.Sunny.toShape()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(roleAccentGradient(roleAccent.accent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.name.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = roleAccent.onAccent
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = artistNameKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring() }
                        )
                    }
                } else Modifier
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
