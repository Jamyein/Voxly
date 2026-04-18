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
import com.voxly.presentation.components.AudioFileStandardRowCompact
import com.voxly.presentation.components.AudioFileAction
import com.voxly.presentation.components.AudioFileStandardRowWithMenu
import com.voxly.core.util.getFirstLetter
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.LazyListCoverPreloader

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
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null
) {
    val computedSharedElementKey = remember(audioFile.path) {
        sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
    }
    if (compactMode) {
        AudioFileStandardRowCompact(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            modifier = modifier,
            sharedElementKey = computedSharedElementKey
        )
    } else if (showActions) {
        AudioFileStandardRowWithMenu(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
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
            sharedElementKey = computedSharedElementKey,
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
    val filePaths = remember(files) { files.map { it.path } }

    LazyListCoverPreloader(listState = listState, filePaths = filePaths)

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
            AudioFileItem(
                audioFile = audioFile,
                isSelected = audioFile.path in selectedFiles,
                onClick = { onFileClick(audioFile) },
                onLongClick = { onFileLongClick(audioFile) },
                showActions = !isSelectionMode,
                onEditMetadata = { onEditFileMetadata(audioFile) },
                onRename = { onRenameFile(audioFile) },
                onDelete = { onDeleteFile(audioFile) },
                onFetchOnlineMetadata = { onFetchOnlineMetadata(audioFile) },
                onFixMetadata = { onFixMetadata(audioFile) }
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
