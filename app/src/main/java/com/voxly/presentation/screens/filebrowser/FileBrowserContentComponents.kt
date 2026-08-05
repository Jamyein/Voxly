package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.toShape
import com.voxly.presentation.components.LibraryRefreshBox
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.components.RoleGradientBadge
import com.voxly.presentation.components.navBarsBottomInset
import com.voxly.presentation.components.rememberRoleAccentAt
import com.voxly.presentation.theme.scaleOnPress
import com.voxly.presentation.viewmodel.SelectedDirectory

/**
 * Top app bar for selection mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onNavigateToReplayGain: () -> Unit,
    visibleFilesCount: Int = -1
) {
    val isAllSelected = visibleFilesCount > 0 && selectedCount >= visibleFilesCount

    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_clear_selection)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        actions = {
            TextButton(onClick = {
                if (isAllSelected) {
                    onClearSelection()
                } else {
                    onSelectAll()
                }
            }) {
                Text(
                    if (isAllSelected) {
                        stringResource(R.string.deselect_all)
                    } else {
                        stringResource(R.string.select_all)
                    }
                )
            }

            FilledTonalButton(
                onClick = onNavigateToReplayGain,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(painter = appIconPainter(AppIcon.Equalizer), contentDescription = stringResource(R.string.cd_scan_replay_gain))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.replay_gain))
            }
        }
    )
}

/**
 * Content for directory overview.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectoryOverviewContent(
    directories: List<SelectedDirectory>,
    directoryFiles: Map<String, List<AudioFile>>,
    onOpenDirectory: (String, String) -> Unit,
    isRefreshing: Boolean,
    isInitialLoad: Boolean = false,
    onRefresh: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    listState: LazyGridState? = null,
    bottomPadding: Dp = 0.dp
) {
    val gridState = listState ?: rememberLazyGridState()
    LibraryRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        scrollBehavior = scrollBehavior,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isInitialLoad -> {
                com.voxly.presentation.components.SkeletonListScreen(modifier = Modifier.fillMaxSize())
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = appIconPainter(AppIcon.FolderOpen),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.selected_directories_count, directories.size),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 8.dp + bottomPadding + navBarsBottomInset()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(directories, key = { _, it -> it.uri }) { index, directory ->
                            val dirName = directory.path.substringAfterLast("/").substringAfterLast(":")
                            val files = directoryFiles[directory.uri].orEmpty()
                            Box(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                )
                            ) {
                                DirectoryItem(
                                    directory = directory,
                                    fileCount = files.size,
                                    index = index,
                                    onClick = { onOpenDirectory(directory.uri, dirName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Loading content placeholder.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.loading_audio_files))
        }
    }
}

/**
 * Empty content placeholder.
 */
@Composable
fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = appIconPainter(AppIcon.MusicNote),
                contentDescription = stringResource(R.string.cd_no_files),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_audio_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_audio_files_or_select_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Error content placeholder.
 */
@Composable
fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = appIconPainter(AppIcon.Error),
                contentDescription = stringResource(R.string.cd_error),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.error_loading_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Directory item hero card.
 *
 * 每个文件夹一张 Hero 瓦片：Cookie9Sided 渐变徽章 + 大号目录名 + 计数 pill，
 * 底色按索引轮换主/次/第三色，让网格有呼吸感。按压 0.97 缩放 + 弹簧回弹。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectoryItem(
    directory: SelectedDirectory,
    fileCount: Int,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val roleAccent = rememberRoleAccentAt(index)

    Card(
        modifier = modifier.scaleOnPress(interactionSource, pressedScale = 0.97f, label = "directoryItemScale"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = roleAccent.container),
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            RoleGradientBadge(
                painter = appIconPainter(AppIcon.FolderOpen),
                contentDescription = stringResource(R.string.cd_directory),
                accent = roleAccent.accent,
                onAccent = roleAccent.onAccent,
                badgeSize = 64.dp,
                iconSize = 30.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                style = MaterialTheme.typography.titleLarge,
                color = roleAccent.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = directory.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = roleAccent.onContainer.copy(alpha = 0.10f),
                contentColor = roleAccent.onContainer
            ) {
                Text(
                    text = stringResource(R.string.file_count, fileCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
