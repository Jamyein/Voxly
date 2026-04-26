package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.data.local.FileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.AudioFileStandardRow
import com.voxly.presentation.components.LazyListCoverPreloader
import com.voxly.presentation.components.AudioFileStandardRowCompact
import com.voxly.presentation.components.AudioFileAction
import com.voxly.presentation.components.AudioFileStandardRowWithMenu
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.core.util.getFirstLetter


@Composable
internal fun AudioFileItem(
    audioFile: AudioFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showActions: Boolean,
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit,
    compactMode: Boolean = false,
    sharedElementKey: String? = null,
    modifier: Modifier = Modifier
) {
    if (compactMode) {
        AudioFileStandardRowCompact(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            sharedElementKey = sharedElementKey,
            modifier = modifier
        )
    } else if (showActions) {
        AudioFileStandardRowWithMenu(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            sharedElementKey = sharedElementKey,
            onAction = { action ->
                when (action) {
                    is AudioFileAction.EditMetadata -> onEditMetadata()
                    is AudioFileAction.Rename -> onRename()
                    is AudioFileAction.Delete -> onDelete()
                    is AudioFileAction.FetchOnlineMetadata -> onFetchOnlineMetadata()
                    is AudioFileAction.FixMetadata -> onFixMetadata()
                }
            },
            modifier = modifier
        )
    } else {
        AudioFileStandardRow(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            sharedElementKey = sharedElementKey,
            modifier = modifier
        )
    }
}

internal fun FileSortOption.labelResId(): Int = when (this) {
    FileSortOption.NAME_ASC -> R.string.file_sort_name_asc
    FileSortOption.NAME_DESC -> R.string.file_sort_name_desc
    FileSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    FileSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFileList(
    files: List<AudioFile>,
    listState: LazyListState,
    selectedFiles: Set<String>,
    modifier: Modifier = Modifier,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onEditFileMetadata: (AudioFile) -> Unit,
    onRenameFile: (AudioFile) -> Unit,
    onDeleteFile: (AudioFile) -> Unit,
    onFetchOnlineMetadata: (AudioFile) -> Unit,
    onFixMetadata: (AudioFile) -> Unit,
    bottomPadding: Dp = 0.dp
) {
    val isSelectionMode = selectedFiles.isNotEmpty()

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 4.dp,
            bottom = 4.dp + bottomPadding
        )
    ) {
        items(files, key = { it.path }) { audioFile ->
            val onFileClickState by rememberUpdatedState(onFileClick)
            val onFileLongClickState by rememberUpdatedState(onFileLongClick)

            val onClickCallback = remember(audioFile.path) { { onFileClickState(audioFile) } }
            val onLongClickCallback = remember(audioFile.path) { { onFileLongClickState(audioFile) } }

            AudioFileItem(
                audioFile = audioFile,
                isSelected = audioFile.path in selectedFiles,
                onClick = onClickCallback,
                onLongClick = onLongClickCallback,
                showActions = !isSelectionMode,
                onEditMetadata = { onEditFileMetadata(audioFile) },
                onRename = { onRenameFile(audioFile) },
                onDelete = { onDeleteFile(audioFile) },
                onFetchOnlineMetadata = { onFetchOnlineMetadata(audioFile) },
                onFixMetadata = { onFixMetadata(audioFile) },
                sharedElementKey = createAlbumArtSharedElementKey(audioFile.path)
            )
        }
    }
}

@Composable
internal fun AudioFileListWithIndexer(
    files: List<AudioFile>,
    listState: LazyListState,
    selectedFiles: Set<String>,
    modifier: Modifier = Modifier,
    showIndexer: Boolean = true,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    onEditFileMetadata: (AudioFile) -> Unit,
    onRenameFile: (AudioFile) -> Unit,
    onDeleteFile: (AudioFile) -> Unit,
    onFetchOnlineMetadata: (AudioFile) -> Unit,
    onFixMetadata: (AudioFile) -> Unit,
    bottomPadding: Dp = 0.dp
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AudioFileList(
            files = files,
            listState = listState,
            selectedFiles = selectedFiles,
            modifier = modifier,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
            onEditFileMetadata = onEditFileMetadata,
            onRenameFile = onRenameFile,
            onDeleteFile = onDeleteFile,
            onFetchOnlineMetadata = onFetchOnlineMetadata,
            onFixMetadata = onFixMetadata,
            bottomPadding = bottomPadding
        )

        LazyListCoverPreloader(
            listState = listState,
            filePaths = files.map { it.path }
        )

        if (showIndexer) {
            LazyColumnScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                bubbleFormatter = { index ->
                    files.getOrNull(index)?.let { audio ->
                        getLeadingCharacter(audio.metadata.getDisplayTitle(audio.name))
                    } ?: "#"
                }
            )
        }
    }
}
