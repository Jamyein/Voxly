package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.createAlbumArtSharedElementKey

internal fun getLeadingCharacter(text: String): String {
    val firstChar = text.trimStart().firstOrNull() ?: return "#"
    return when {
        firstChar.isDigit() -> "#"
        firstChar in 'a'..'z' -> firstChar.uppercaseChar().toString()
        firstChar in 'A'..'Z' -> firstChar.toString()
        else -> firstChar.toString()
    }
}

@Composable
internal fun AllAudiosTabContent(
    audios: List<AudioFile>,
    selectedFiles: Set<String>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    isRefreshing: Boolean,
    isInitialLoad: Boolean = false,
    onRefresh: () -> Unit,
    listState: LazyListState? = null
) {
    val lazyListState = listState ?: rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (isInitialLoad) {
                com.voxly.presentation.components.SkeletonListScreen(modifier = Modifier.fillMaxSize())
            } else if (audios.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_audio_files),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(audios, key = { it.path }) { audioFile ->
                        val isSelected = audioFile.path in selectedFiles
                        AudioFileItem(
                            audioFile = audioFile,
                            isSelected = isSelected,
                            onClick = { onFileClick(audioFile) },
                            onLongClick = { onFileLongClick(audioFile) },
                            showActions = false,
                            onEditMetadata = {},
                            onRename = {},
                            onDelete = {},
                            onFetchOnlineMetadata = {},
                            onFixMetadata = {},
                            compactMode = true,
                            sharedElementKey = createAlbumArtSharedElementKey(audioFile.path)
                        )
                    }
                }
            }
        }
        
        if (audios.isNotEmpty()) {
            LazyColumnScrollbar(
                state = lazyListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                bubbleFormatter = { index ->
                    audios.getOrNull(index)?.let { audio ->
                        getLeadingCharacter(audio.metadata.getDisplayTitle(audio.name))
                    } ?: "#"
                }
            )
        }
    }
}