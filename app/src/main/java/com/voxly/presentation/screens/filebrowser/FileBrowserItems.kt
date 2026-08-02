package com.voxly.presentation.screens.filebrowser

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.rememberRoleAccent
import com.voxly.presentation.components.roleAccentGradient
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.createAlbumTitleSharedElementKey
import com.voxly.presentation.components.createAlbumArtistTextSharedElementKey
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.screens.album.getAlbumDisplayYearString
import com.voxly.presentation.theme.MaterialShapes
import com.voxly.presentation.theme.scaleOnPress
import timber.log.Timber

@Composable
internal fun BatchMenuItem(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        content = { Text(label) },
        leadingContent = {
            Icon(
                painter = appIconPainter(icon),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumListItem(
    album: AlbumGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                val coverFile = album.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ?: album.files.firstOrNull()
                if (coverFile != null) {
                    AlbumArtImage(
                        filePath = coverFile.path,
                        albumId = coverFile.mediaStoreAlbumId,
                        contentDescription = null,
                        size = 48.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DefaultAlbumArtPlaceholder(size = 48.dp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.albumArtist ?: stringResource(R.string.unknown_album_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.track_count, album.files.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumGridItem(
    album: AlbumGroup,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val coverFile = remember(album) {
        album.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
            ?: album.files.firstOrNull()
    }
    val albumYear = remember(album) { getAlbumDisplayYearString(album) }
    val trackCountText = stringResource(R.string.track_count, album.files.size)
    val albumCoverKey = createAlbumCoverSharedElementKey(album.name, album.albumArtist)
    val albumTitleKey = createAlbumTitleSharedElementKey(album.name, album.albumArtist)
    val albumArtistKey = album.albumArtist?.let { createAlbumArtistTextSharedElementKey(album.name, album.albumArtist) }
    val infoRestText = remember(album, albumYear, trackCountText) {
        buildString {
            append(trackCountText)
            if (albumYear != null) {
                append(" · ")
                append(albumYear)
            }
        }
    }
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null

    // 无封面时用角色渐变 + 首字母占位；name hash 保证同一专辑颜色稳定（无需调用点传 index）
    val roleAccent = rememberRoleAccent(album.name)

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
                            rememberSharedContentState(key = albumCoverKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring() }
                        )
                        .clickable(interactionSource = interactionSource, onClick = onClick)
                        .clip(MaterialTheme.shapes.medium)
                        .scaleOnPress(interactionSource, label = "albumGridItemScale")
                }
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(interactionSource = interactionSource, onClick = onClick)
                    .clip(MaterialTheme.shapes.medium)
                    .scaleOnPress(interactionSource, label = "albumGridItemScale")
            },
            contentAlignment = Alignment.Center
        ) {
            if (coverFile != null) {
                AlbumArtImage(
                    filePath = coverFile.path,
                    albumId = coverFile.mediaStoreAlbumId,
                    contentDescription = null,
                    size = 200.dp,
                    modifier = Modifier.fillMaxSize(),
                    clipShape = MaterialTheme.shapes.medium
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(roleAccentGradient(roleAccent.accent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = album.name.trim().firstOrNull()?.uppercase() ?: "?",
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
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = albumTitleKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> spring() }
                        )
                    }
                } else Modifier
            )
            Text(
                text = album.albumArtist ?: stringResource(R.string.unknown_album_artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canUseSharedTransition && albumArtistKey != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = albumArtistKey),
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
                    text = infoRestText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistListItem(
    artist: ArtistGroup,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val artistAvatarKey = createArtistAvatarSharedElementKey(artist.name)
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier
                            .size(48.dp)
                            .sharedElement(
                                rememberSharedContentState(key = artistAvatarKey),
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
                        contentDescription = null,
                        size = 48.dp,
                        modifier = Modifier.fillMaxSize()
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
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.album_count, artist.albums.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.track_count, artist.files.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

