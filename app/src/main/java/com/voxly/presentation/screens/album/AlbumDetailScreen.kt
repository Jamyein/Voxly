package com.voxly.presentation.screens.album

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.core.util.Constants
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.sharedBoundsIfAvailable
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.screens.album.formatBitrate
import com.voxly.presentation.screens.album.formatSampleRate

/**
 * Album detail screen showing album info and track list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    albumArtist: String?,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    viewModel: AlbumDetailViewModel
) {
    // Load album from cache
    LaunchedEffect(albumName, albumArtist) {
        viewModel.loadAlbum(albumName, albumArtist)
    }

    val albumNameState by viewModel.albumName.collectAsStateWithLifecycle()
    val albumArtistState by viewModel.albumArtist.collectAsStateWithLifecycle()
    val albumYear by viewModel.albumYear.collectAsStateWithLifecycle()
    val albumBitrate by viewModel.albumBitrate.collectAsStateWithLifecycle()
    val albumSampleRate by viewModel.albumSampleRate.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val coverPath by viewModel.coverPath.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // Pull-to-refresh callback
    val onRefresh: () -> Unit = {
        viewModel.refresh(forceRefresh = false)
    }

    // Calculate total duration
    val totalDuration = remember(files) {
        files.sumOf { it.duration }
    }
    val formattedTotalDuration = remember(totalDuration) {
        val hours = totalDuration / Constants.MS_PER_HOUR
        val minutes = (totalDuration % Constants.MS_PER_HOUR) / Constants.MS_PER_MINUTE
        val seconds = (totalDuration % Constants.MS_PER_MINUTE) / Constants.MS_PER_SECOND
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    // Sort by disc number and track number
    val sortedFiles = remember(files) {
        files.sortedWith(
            compareBy({ it.metadata.discNumber ?: 1 }, { it.metadata.trackNumber ?: 0 })
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp + innerPadding.calculateTopPadding(),
                    bottom = 12.dp + innerPadding.calculateBottomPadding(),
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card: Cover + Album Info
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Cover image using AlbumArtImage composable with shared element transition
                            // Use album-level shared element key to match AlbumScreen list
                            val firstFile = files.firstOrNull()
                            val albumCoverKey = createAlbumCoverSharedElementKey(albumNameState, albumArtistState)
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .sharedBoundsIfAvailable(key = albumCoverKey)
                                    .clip(MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumArtImage(
                                    filePath = coverPath ?: firstFile?.path,
                                    albumId = firstFile?.mediaStoreAlbumId,
                                    contentDescription = stringResource(R.string.album_cover),
                                    size = 120.dp,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(24.dp)
                                                .fillMaxSize(),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Right: Album info
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = albumNameState,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = albumArtistState ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (albumBitrate > 0) {
                                    Text(
                                        text = formatBitrate(albumBitrate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (albumSampleRate > 0) {
                                    Text(
                                        text = formatSampleRate(albumSampleRate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                // Card: Statistics
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = files.size.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.singles),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedTotalDuration,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.metadata_duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = albumYear ?: "N/A",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.metadata_year),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Song list grouped by disc
                val groupedFiles = sortedFiles.groupBy { it.metadata.discNumber ?: 1 }
                val sortedDiscNumbers = groupedFiles.keys.sorted()

                items(sortedDiscNumbers.size, key = { sortedDiscNumbers[it] }) { discIndex ->
                    val discNumber = sortedDiscNumbers[discIndex]
                    val discFiles = groupedFiles[discNumber] ?: return@items

                    // Disc 标题
                    Text(
                        text = "Disc $discNumber",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 歌曲列表 - 使用 SegmentedListItem 实现分段效果
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        discFiles.forEachIndexed { index, audioFile ->
                            SegmentedListItem(
                                onClick = { onNavigateToMetadata(audioFile.path, createAlbumCoverSharedElementKey(albumNameState, albumArtistState)) },
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = discFiles.size
                                ),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                leadingContent = {
                                    // 只显示 track number，封面使用专辑详情页的封面
                                    Text(
                                        text = audioFile.metadata.trackNumber?.toString() ?: "-",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = audioFile.metadata.getDisplayTitle(audioFile.name),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${audioFile.format.uppercase()} • ${audioFile.getFormattedDuration()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                content = {}
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
