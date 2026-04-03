package com.voxly.presentation.screens.filebrowser

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.voxly.presentation.components.sharedBoundsIfAvailable
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.theme.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

@Composable
internal fun BatchMenuItem(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
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
    val coverKey = createAlbumCoverSharedElementKey(album.name, album.artist)
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
                    .sharedBoundsIfAvailable(key = coverKey)
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
                    text = album.artist ?: "",
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
    onClick: () -> Unit
) {
    val coverKey = createAlbumCoverSharedElementKey(album.name, album.artist)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Album cover - square aspect ratio with rounded corners, no shadow
        // Only the cover image area is clickable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sharedBoundsIfAvailable(key = coverKey)
                .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val coverFile = album.files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                ?: album.files.firstOrNull()
            if (coverFile != null) {
                AlbumArtImage(
                    filePath = coverFile.path,
                    albumId = coverFile.mediaStoreAlbumId,
                    contentDescription = null,
                    size = 200.dp,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DefaultAlbumArtPlaceholder(size = 200.dp)
            }
        }
        // Album info - transparent background
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Album name - bold
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Track count, artist, and year in the same row
            val albumYear = album.files.firstOrNull()?.metadata?.year
            val infoText = buildString {
                append(stringResource(R.string.track_count, album.files.size))
                album.artist?.let {
                    append(" ")
                    append(it)
                }
                if (albumYear != null) {
                    append(" ")
                    append(albumYear)
                }
            }
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistListItem(
    artist: ArtistGroup,
    onClick: () -> Unit
) {
    val avatarKey = createArtistAvatarSharedElementKey(artist.name)
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
                    .sharedBoundsIfAvailable(key = avatarKey)
                    .clip(MaterialShapes.Sunny.toShape()),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.coverPath.isNullOrBlank()) {
                    AlbumArtImage(
                        filePath = artist.coverPath,
                        contentDescription = null,
                        size = 48.dp,
                        modifier = Modifier
                            .fillMaxSize()
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

