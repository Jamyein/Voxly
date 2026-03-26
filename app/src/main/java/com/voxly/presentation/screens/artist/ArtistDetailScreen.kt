package com.voxly.presentation.screens.artist

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.screens.filebrowser.AudioFileItem
import com.voxly.presentation.theme.ExpressiveMotion
import com.voxly.presentation.ui.loadCarouselCoverArt
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.components.sharedElementIfAvailable
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
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
 * Extracts the year from album files (uses the maximum year found).
 */
private fun extractAlbumYear(files: List<com.voxly.domain.model.AudioFile>): Int? {
    return files
        .mapNotNull { file -> extractYear(file.metadata.year) }
        .maxOrNull()
}

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
@Suppress("DEPRECATION")
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    // Load artist from cache
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artistNameState by viewModel.artistName.collectAsState()
    val files by viewModel.files.collectAsState()
    val coverPath by viewModel.coverPath.collectAsState()
    val albumCovers by viewModel.albumCovers.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Pull-to-refresh callback
    val onRefresh: () -> Unit = {
        viewModel.refresh(forceRefresh = false)
    }

    // Separate singles (songs without album) and albums
    val singles = remember(files) {
        files.filter { it.metadata.album.isNullOrBlank() }
    }

    // Group albums and sort by year (newest first)
    val albumsSorted = remember(files) {
        files.filter { !it.metadata.album.isNullOrBlank() }
            .groupBy { it.metadata.album!! }
            .map { (albumName, albumFiles) ->
                val year = extractAlbumYear(albumFiles)
                AlbumInfo(
                    name = albumName,
                    files = albumFiles,
                    year = year
                )
            }
            .sortedByDescending { it.year ?: 0 }
    }

    // Create a map for easy lookup (maintains sorted order)
    val albumsGrouped = remember(albumsSorted) {
        albumsSorted.associate { it.name to it.files }
    }

    // Use cached cover path for avatar (performance optimization)
    val avatarBitmap = remember(coverPath) {
        coverPath?.let { loadLocalAlbumArt(it) }
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
                // Header: Circle Avatar + Artist Name with shared element transition
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circle Avatar (150dp) with shared element transition
                        val avatarKey = createArtistAvatarSharedElementKey(artistName)
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .sharedElementIfAvailable(key = avatarKey),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.artist_cover),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(40.dp)
                                            .fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
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
                            onClick = { onNavigateToMetadata(audioFile.path, "cover_${audioFile.path.hashCode()}") },
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
                if (albumsGrouped.isNotEmpty()) {
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
                        val albumList = albumsGrouped.keys.toList()
                        if (albumList.isNotEmpty()) {
                            val carouselState = rememberCarouselState { albumList.size }

                            // 封面数据首次填充时立即预加载（解决首屏空白问题）
                            LaunchedEffect(albumCovers) {
                                if (albumCovers.isNotEmpty()) {
                                    viewModel.preloadAdjacentAlbumCovers(0)
                                }
                            }

                            // 滚动监听：滚动停止时预加载相邻专辑封面
                            LaunchedEffect(carouselState) {
                                snapshotFlow { carouselState.currentItem }.collect { page ->
                                    viewModel.preloadAdjacentAlbumCovers(page)
                                }
                            }

                            HorizontalMultiBrowseCarousel(
                                state = carouselState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                preferredItemWidth = 140.dp,
                                itemSpacing = 12.dp,
                                contentPadding = PaddingValues(horizontal = 40.dp)
                            ) { page ->
                                val albumName = albumList[page]
                                val albumFiles = albumsGrouped[albumName] ?: emptyList()
                                val albumArtPath = albumCovers[albumName]

                                AlbumCard(
                                    albumName = albumName,
                                    albumArtist = artistName,
                                    trackCount = albumFiles.size,
                                    albumArtPath = albumArtPath,
                                    onClick = { onNavigateToAlbumDetail(albumName, artistName) },
                                    modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
                                )
                            }
                        }
                    }

                    // Show songs grouped by album below
                    albumsGrouped.forEach { (albumName, albumFiles) ->
                        item {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(albumFiles.take(3), key = { "album_${albumName}_${it.path}" }) { audioFile ->
                            AudioFileItem(
                                audioFile = audioFile,
                                isSelected = false,
                                onClick = { onNavigateToMetadata(audioFile.path, "cover_${audioFile.path.hashCode()}") },
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

                        if (albumFiles.size > 3) {
                            item {
                                Text(
                                    text = "+${albumFiles.size - 3} more",
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
 * 轮播封面专用图片组件（384px）。
 * 使用produceState在IO线程加载，避免主线程阻塞。
 * key1 = filePath确保路径变化时重新加载。
 */
@Composable
fun CarouselAlbumArtImage(
    filePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {}
) {
    val bitmap = produceState<Bitmap?>(initialValue = null, key1 = filePath) {
        value = withContext(Dispatchers.IO) {
            filePath?.let { loadCarouselCoverArt(it) }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val loadedBitmap = bitmap.value
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            placeholder()
        }
    }
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
    albumArtPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = ExpressiveMotion.DefaultSpring,
        label = "albumCardScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .height(170.dp)
            .scale(scale),
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
                    .sharedElementIfAvailable(key = albumCoverKey),
                contentAlignment = Alignment.Center
            ) {
                CarouselAlbumArtImage(
                    filePath = albumArtPath,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxSize(),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Album name and track count
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.track_count, trackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
