package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.AlphabetIndexer
import com.voxly.presentation.components.AudioFileStandardRow
import com.voxly.presentation.components.AudioFileStandardRowCompact
import com.voxly.presentation.components.AudioFileAction
import com.voxly.presentation.components.AudioFileStandardRowWithMenu
import com.voxly.presentation.components.getFirstLetter
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier
) {
    if (compactMode) {
        AudioFileStandardRowCompact(
            audioFile = audioFile,
            isSelected = isSelected,
            onClick = onClick,
            modifier = modifier
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
            modifier = modifier
        )
    }
}

@Composable
internal fun FileActionsMenu(
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = appIconPainter(AppIcon.MoreVert),
                contentDescription = stringResource(R.string.file_item_actions)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Edit),
                        contentDescription = stringResource(R.string.cd_edit_file)
                    )
                },
                onClick = {
                    expanded = false
                    onEditMetadata()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fetch_online_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.CloudDownload),
                        contentDescription = stringResource(R.string.cd_online_metadata)
                    )
                },
                onClick = {
                    expanded = false
                    onFetchOnlineMetadata()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fix_metadata)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.AutoFix),
                        contentDescription = stringResource(R.string.cd_batch_fix)
                    )
                },
                onClick = {
                    expanded = false
                    onFixMetadata()
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename_file)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Rename),
                        contentDescription = stringResource(R.string.cd_batch_rename)
                    )
                },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_viewer_delete)) },
                leadingIcon = {
                    Icon(
                        painter = appIconPainter(AppIcon.Close),
                        contentDescription = stringResource(R.string.cd_delete_file),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

private fun FileSortOption.labelResId(): Int = when (this) {
    FileSortOption.NAME_ASC -> R.string.file_sort_name_asc
    FileSortOption.NAME_DESC -> R.string.file_sort_name_desc
    FileSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    FileSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
}

@Composable
internal fun SortMenu(
    isExpanded: Boolean,
    currentSortOption: FileSortOption,
    onSortOptionChange: (FileSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            FileSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelResId())) },
                    leadingIcon = if (option == currentSortOption) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onSortOptionChange(option)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioFileList(
    files: List<AudioFile>,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
    val coroutineScope = rememberCoroutineScope()

    // Create letter to index mapping for fast navigation
    val letterToIndex = remember(files) {
        files.mapIndexed { index, file ->
            getFirstLetter(file.metadata.getDisplayTitle(file.name)) to index
        }.distinctBy { it.first }.toMap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            val configuration = LocalConfiguration.current
            val availableHeight = configuration.screenHeightDp.dp - 160.dp

            AlphabetIndexer(
                groupedFiles = letterToIndex.mapValues { listOf() },
                onLetterSelected = { letter ->
                    letterToIndex[letter]?.let { targetIndex ->
                        coroutineScope.launch {
                            // Use scrollToItem (instant) instead of animateScrollToItem for better performance
                            listState.scrollToItem(targetIndex)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 80.dp, bottom = 80.dp, end = 4.dp),
                availableHeight = availableHeight
            )
        }
    }
}
