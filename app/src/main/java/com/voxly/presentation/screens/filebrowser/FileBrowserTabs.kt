package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.LibraryRefreshBox
import com.voxly.presentation.components.navBarsBottomInset
import com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar
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
    selectedFilesState: State<Set<String>>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    isRefreshing: Boolean,
    isInitialLoad: Boolean = false,
    onRefresh: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    listState: LazyGridState? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val lazyGridState = listState ?: rememberLazyGridState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LibraryRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            scrollBehavior = scrollBehavior,
            modifier = Modifier.fillMaxSize()
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
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp + navBarsBottomInset()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(audios, key = { it.path }) { audioFile ->
                        val isSelected by remember(audioFile.path) {
                            derivedStateOf { audioFile.path in selectedFilesState.value }
                        }
                        val onFileClickState by rememberUpdatedState(onFileClick)
                        val onFileLongClickState by rememberUpdatedState(onFileLongClick)
                        AudioFileItem(
                            audioFile = audioFile,
                            isSelected = isSelected,
                            onClick = remember(audioFile.path) { { onFileClickState(audioFile) } },
                            onLongClick = remember(audioFile.path) { { onFileLongClickState(audioFile) } },
                            showActions = false,
                            onEditMetadata = {},
                            onRename = {},
                            onDelete = {},
                            onFetchOnlineMetadata = {},
                            onFixMetadata = {},
                            compactMode = true,
                            sharedElementKey = createAlbumArtSharedElementKey(audioFile.path),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
        
        if (audios.isNotEmpty()) {
            LazyVerticalGridScrollbar(
                state = lazyGridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                showBubble = true,
                bubbleFormatter = { index ->
                    audios.getOrNull(index)?.let { audio ->
                        getLeadingCharacter(audio.metadata.getDisplayTitle(audio.name))
                    } ?: "#"
                }
            )
        }
    }
}