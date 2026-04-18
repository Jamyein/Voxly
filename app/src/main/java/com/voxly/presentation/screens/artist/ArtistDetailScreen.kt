package com.voxly.presentation.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.voxly.R
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.screens.filebrowser.AudioFileItem
import com.voxly.presentation.viewmodel.ArtistDetailViewModel

/**
 * Album information for carousel display with year.
 */
private data class AlbumInfo(
    val name: String,
    val files: List<com.voxly.domain.model.AudioFile>,
    val year: Int?
)

/**
 * Extracts 4-digit year from a string (e.g., "2023" or "2023-01-01").
 */
private fun extractYear(rawYear: String?): Int? {
    val normalized = rawYear?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return Regex("""\d{4}""").find(normalized)?.value?.toIntOrNull()
}

/**
 * Artist detail screen showing artist info and song list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    viewModel: ArtistDetailViewModel
) {
    // Load artist from cache
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artistNameState by viewModel.artistName.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val coverPath by viewModel.coverPath.collectAsStateWithLifecycle()
    val coverAlbumId by viewModel.coverAlbumId.collectAsStateWithLifecycle()
    val albumCovers by viewModel.albumCovers.collectAsStateWithLifecycle()
    val albumYears by viewModel.albumYears.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    // Pull-to-refresh callback
    val onRefresh: () -> Unit = {
        viewModel.refresh(forceRefresh = false)
    }

    // Separate singles (songs without album) and albums
    val singles = remember(files) {
        files.filter { it.metadata.album.isNullOrBlank() }
    }

    // Group albums and sort by year (newest first)
    // Use albumYears from ViewModel which includes fallback to TagLib for missing years
    val albumsSorted = remember(files, albumYears) {
        files.filter { !it.metadata.album.isNullOrBlank() }
            .groupBy { it.metadata.album!! }
            .map { (albumName, albumFiles) ->
                // Get year from ViewModel's albumYears (includes TagLib fallback)
                val yearStr = albumYears[albumName]
                val year = yearStr?.let { extractYear(it) }
                AlbumInfo(
                    name = albumName,
                    files = albumFiles,
                    year = year
                )
            }
            .sortedByDescending { it.year ?: 0 }
    }

    // Use the same AlbumArtImage component as ArtistListItem for consistency
    // This ensures the avatar displays the same way as in the artist list
    val avatarKey = createArtistAvatarSharedElementKey(artistName)
    
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
                // Header: Circle Avatar + Artist Name with shared element transition
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circle Avatar (150dp) with shared element transition
                        // Using AlbumArtImage like ArtistListItem for consistent display
                        // Pass both coverPath and coverAlbumId for fallback support
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!coverPath.isNullOrBlank() || coverAlbumId != null) {
                                AlbumArtImage(
                                    filePath = coverPath,
                                    albumId = coverAlbumId,
                                    contentDescription = stringResource(R.string.artist_cover),
                                    size = 150.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                DefaultAlbumArtPlaceholder(size = 150.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Artist Name (headlineMedium centered)
                        Text(
                            text = artistNameState,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Singles Section (Songs without album)
                if (singles.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.singles),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(singles, key = { "single_${it.path}" }) { audioFile ->
                        AudioFileItem(
                            audioFile = audioFile,
                            isSelected = false,
                            onClick = { onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path)) },
                            onLongClick = {},
                            showActions = false,
                            onEditMetadata = {},
                            onRename = {},
                            onDelete = {},
                            onFetchOnlineMetadata = {},
                            onFixMetadata = {},
                            compactMode = true
                        )
                    }
                }

                // Albums Section
                if (albumsSorted.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.albums),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Album cards in carousel
                    item {
                        val carouselState = rememberCarouselState { albumsSorted.size }

                        // 自动轮播 + 预加载：基于当前索引变化的混合策略
                        // 性能优化：仅使用 snapshotFlow 监听，避免额外的 DisposableEffect
                        val autoScrollEnabled = albumsSorted.size > 1
                        
                        LaunchedEffect(carouselState) {
                            snapshotFlow { carouselState.currentItem }
                                .collect { currentIndex ->
                                    // 预加载相邻封面
                                    viewModel.preloadAdjacentAlbumCovers(currentIndex)
                                    
                                    if (autoScrollEnabled) {
                                        // 等待 4 秒无交互后再滚动到下一项
                                        delay(4000)
                                        
                                        // 再次检查是否仍然在同一位置（无新交互）
                                        if (carouselState.currentItem == currentIndex) {
                                            // 计算下一项（循环）
                                            val nextIndex = (currentIndex + 1) % albumsSorted.size
                                            
                                            // 执行平滑滚动
                                            try {
                                                carouselState.animateScrollToItem(nextIndex)
                                            } catch (_: Exception) {
                                                // 动画可能被中断（用户交互），静默处理
                                            }
                                        }
                                    }
                                }
                        }

                        HorizontalMultiBrowseCarousel(
                            state = carouselState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(221.dp),
                            preferredItemWidth = 160.dp,
                            itemSpacing = 12.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) { page ->
                            val albumInfo = albumsSorted[page]
                            val albumArtPath = albumCovers[albumInfo.name]
                            // Get mediaStoreAlbumId from the first file in the album that has one
                            val albumId = albumInfo.files.firstOrNull { 
                                it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 
                            }?.mediaStoreAlbumId

                            AlbumCard(
                                albumName = albumInfo.name,
                                albumArtist = artistName,
                                trackCount = albumInfo.files.size,
                                albumYear = albumInfo.year,
                                albumArtPath = albumArtPath,
                                albumId = albumId,
                                onClick = { onNavigateToAlbumDetail(albumInfo.name, artistName) },
                                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
                            )
                        }
                    }

                    // Show songs grouped by album below (sorted by year)
                    albumsSorted.forEach { albumInfo ->
                        item {
                            val yearText = albumInfo.year?.let { " ($it)" } ?: ""
                            Text(
                                text = albumInfo.name + yearText,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(albumInfo.files.take(3), key = { "album_${albumInfo.name}_${it.path}" }) { audioFile ->
                            AudioFileItem(
                                audioFile = audioFile,
                                isSelected = false,
                                onClick = { onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path)) },
                                onLongClick = {},
                                showActions = false,
                                onEditMetadata = {},
                                onRename = {},
                                onDelete = {},
                                onFetchOnlineMetadata = {},
                                onFixMetadata = {},
                                compactMode = true
                            )
                        }

                        if (albumInfo.files.size > 3) {
                            item {
                                Text(
                                    text = "+${albumInfo.files.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }

                // If no files
                if (files.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_tracks_for_artist),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 轮播封面专用图片组件。
 * 简化为 AlbumArtImage 的包装，使用统一的图片加载逻辑和缓存策略。
 */
@Composable
fun CarouselAlbumArtImage(
    filePath: String?,
    albumId: Long? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = { DefaultAlbumArtPlaceholder(size = 160.dp) }
) {
    AlbumArtImage(
        filePath = filePath,
        albumId = albumId,
        contentDescription = contentDescription,
        modifier = modifier,
        size = 160.dp,
        contentScale = ContentScale.Crop,
        crossfade = false,
        placeholder = placeholder
    )
}

/**
 * Album card for carousel with responsive sizing and spring animation.
 * Supports Container Transform transition to album detail screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumCard(
    albumName: String,
    albumArtist: String?,
    trackCount: Int,
    albumYear: Int?,
    albumArtPath: String?,
    albumId: Long? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(205.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Album art with Container Transform shared element transition
            val albumCoverKey = createAlbumCoverSharedElementKey(albumName, albumArtist)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                CarouselAlbumArtImage(
                    filePath = albumArtPath,
                    albumId = albumId,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Album year and name
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Year above album name
                    if (albumYear != null) {
                        Text(
                            text = albumYear.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Album name
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
