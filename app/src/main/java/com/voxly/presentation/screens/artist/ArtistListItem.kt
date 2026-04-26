package com.voxly.presentation.screens.artist

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.ArtistListItemState
import com.voxly.presentation.components.AlbumArtImage

import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.components.LocalSharedTransitionScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistListItem(
    artist: ArtistListItemState,
    onClick: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    val avatarKey = createArtistAvatarSharedElementKey(artist.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialShapes.Sunny.toShape()),
            contentAlignment = Alignment.Center
        ) {
            val innerModifier = with(sharedTransitionScope!!) {
                Modifier
                    .matchParentSize()
                    .sharedElement(
                        rememberSharedContentState(key = avatarKey),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            }
            Box(modifier = innerModifier) {
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
