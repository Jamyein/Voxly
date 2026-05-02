package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Online search bar component with buttons for online lyrics and metadata search.
 */
@Composable
fun OnlineSearchBar(
    hasLyrics: Boolean,
    onNavigateToLyricsSelector: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasLyrics) {
            IconButton(onClick = onNavigateToLyricsSelector) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = stringResource(R.string.select_lyrics_for_poster)
                )
            }
        }

        IconButton(onClick = onNavigateToOnlineLyricsSearch) {
            Icon(
                painter = appIconPainter(AppIcon.MusicNote),
                contentDescription = stringResource(R.string.cd_online_lyrics)
            )
        }

        IconButton(onClick = onNavigateToOnlineMetadata) {
            Icon(
                painter = appIconPainter(AppIcon.CloudDownload),
                contentDescription = stringResource(R.string.cd_online_metadata)
            )
        }
    }
}