package com.voxly.presentation.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.sharedBoundsIfAvailable
import com.voxly.presentation.components.createAlbumArtSharedElementKey

/**
 * Reusable search bottom sheet component for searching audio files.
 *
 * @param sheetState The state of the bottom sheet
 * @param onDismiss Callback when the sheet is dismissed
 * @param allFiles The list of all audio files to search through
 * @param onFileClick Callback when a search result is clicked
 * @param modifier Modifier for the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    allFiles: List<AudioFile>,
    onFileClick: (AudioFile) -> Unit,
    modifier: Modifier = Modifier
) {
    // Internal search state - independent from main list
    var localSearchQuery by remember { mutableStateOf("") }

    // Internal search logic - independent from main list
    // Minimum 2 characters to avoid excessive filtering on large libraries
    val searchResults = remember(localSearchQuery, allFiles) {
        if (localSearchQuery.length < 2) {
            emptyList()
        } else {
            val query = localSearchQuery.lowercase()
            allFiles.filter { audioFile ->
                audioFile.name.lowercase().contains(query) ||
                audioFile.metadata.title?.lowercase()?.contains(query) == true ||
                audioFile.metadata.artist?.lowercase()?.contains(query) == true ||
                audioFile.metadata.album?.lowercase()?.contains(query) == true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.search_files),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            )

            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = { localSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.file_search_hint)) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { localSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search results list
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(searchResults, key = { it.path }) { audioFile ->
                        SearchResultItem(
                            audioFile = audioFile,
                            onClick = { onFileClick(audioFile) }
                        )
                    }
                }
            } else if (localSearchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.file_search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_tip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Search result item composable for displaying audio file in search results.
 * Supports Container Transform transition to metadata editor.
 *
 * @param audioFile The audio file to display
 * @param onClick Callback when the item is clicked
 * @param modifier Modifier for the item
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchResultItem(
    audioFile: AudioFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumArtKey = createAlbumArtSharedElementKey(audioFile.path)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album artwork with shared container transition
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .sharedBoundsIfAvailable(key = albumArtKey)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    filePath = audioFile.path,
                    albumId = audioFile.mediaStoreAlbumId,
                    contentDescription = stringResource(R.string.cd_album_art),
                    size = 48.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOfNotNull(
                    audioFile.metadata.artist,
                    audioFile.metadata.album
                ).joinToString(" - ")

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
