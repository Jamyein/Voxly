package com.voxly.presentation.screens.album

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.AlbumSortOption
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.StandardBoundsTransform
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.screens.filebrowser.AlbumGridItem
import com.voxly.presentation.screens.filebrowser.getLeadingCharacter
import com.voxly.presentation.viewmodel.AlbumViewModel
import com.voxly.presentation.viewmodel.ScrollPosition

/**
 * Extracts the best cover file path for an album.
 * Prefers files with MediaStore album ID, falls back to first file.
 */
private fun AlbumGroup.coverFilePath(): String? {
    return coverFile()?.path
}

/**
 * Extracts the best cover file for an album.
 * Prefers files with MediaStore album ID, falls back to first file.
 */
private fun AlbumGroup.coverFile(): com.voxly.domain.model.AudioFile? {
    return files.firstOrNull {
        it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
    } ?: files.firstOrNull()
}

/**
 * Album art image with shared element support.
 * Uses composition locals to access shared transition scope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlbumArtWithSharedElement(
    filePath: String?,
    albumId: Long?,
    sharedElementKey: String,
    size: androidx.compose.ui.unit.Dp
) {
    val sharedTransitionScope = com.voxly.presentation.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = androidx.navigation3.ui.LocalNavAnimatedContentScope.current
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = with(sharedTransitionScope!!) {
            Modifier
                .size(size)
                .sharedElement(
                    rememberSharedContentState(key = sharedElementKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = StandardBoundsTransform
                )
                .clip(shape)
        }
    ) {
        AlbumArtImage(
            filePath = filePath,
            albumId = albumId,
            contentDescription = null,
            size = size,
            modifier = Modifier.fillMaxSize(),
            clipShape = shape
        )
    }
}

/**
 * Album screen content (list/grid) without navigation.
 * Used as the list pane in dual-pane layout.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlbumScreenContent(
    viewModel: AlbumViewModel,
    onAlbumClick: (AlbumGroup) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    
    val albums by viewModel.sortedAlbums.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle(initialValue = AlbumSortOption.NAME_ASC.name)
    var scrollToTopTrigger by remember { mutableIntStateOf(0) }
    var isSortExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(albums, isRefreshing) {
        if (albums.isEmpty() && !isRefreshing) {
            viewModel.refresh()
        }
    }

    val currentSortOption = remember(sortOption) {
        try {
            AlbumSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            AlbumSortOption.NAME_ASC
        }
    }

    // Get saved scroll position
    val savedScrollPosition = remember(currentSortOption) {
        viewModel.getScrollPosition("album_list_${currentSortOption.name}")
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { scrollToTopTrigger++ })
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.nav_albums),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    SortMenuButton(
                        expanded = isSortExpanded,
                        onExpandedChange = { isSortExpanded = it },
                        currentSortOption = currentSortOption,
                        options = AlbumSortOption.entries,
                        optionLabelResId = { it.labelResId() },
                        contentDescription = stringResource(R.string.album_sort_label),
                        onSortOptionChange = { viewModel.setSortOption(it.name) }
                    )
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (albums.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_albums_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AlbumTabContent(
                    albums = albums,
                    onAlbumClick = onAlbumClick,
                    scrollToTopTrigger = scrollToTopTrigger,
                    sortOption = currentSortOption,
                    savedScrollPosition = savedScrollPosition,
                    onSaveScrollPosition = { index, offset ->
                        viewModel.saveScrollPosition("album_list_${currentSortOption.name}", index, offset)
                    }
                )
            }
        }
    }
}

@Composable
internal fun AlbumTabContent(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
    listState: LazyListState? = null,
    scrollToTopTrigger: Int = 0,
    sortOption: AlbumSortOption? = null,
    savedScrollPosition: com.voxly.presentation.viewmodel.ScrollPosition? = null,
    onSaveScrollPosition: ((Int, Int) -> Unit)? = null
) {
    val isYearSort = sortOption == AlbumSortOption.YEAR_DESC

    // Restore scroll position for grid
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollPosition?.index ?: 0,
        initialFirstVisibleItemScrollOffset = savedScrollPosition?.offset ?: 0
    )

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.scrollToItem(0)
        }
    }

    // Save scroll position when leaving
    DisposableEffect(sortOption) {
        onDispose {
            onSaveScrollPosition?.invoke(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_albums_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (isYearSort) {
                AlbumYearGroupedContent(
                    albums = albums,
                    onAlbumClick = onAlbumClick,
                    isDescending = true,
                    scrollToTopTrigger = scrollToTopTrigger,
                    savedScrollPosition = savedScrollPosition,
                    onSaveScrollPosition = onSaveScrollPosition
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = albums.size,
                        key = { index -> albumStableKey(albums[index]) }
                    ) { index ->
                        val album = albums[index]
                        val onItemClick = remember(album) { { onAlbumClick(album) } }
                        AlbumGridItem(
                            album = album,
                            onClick = onItemClick
                        )
                    }
                }
            }
        }

        if (!isYearSort && albums.isNotEmpty()) {
            LazyVerticalGridScrollbar(
                state = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                showBubble = true,
                bubbleFormatter = { index ->
                    albums.getOrNull(index)?.let { getLeadingCharacter(it.name) } ?: "#"
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AlbumYearGroupedContent(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
    isDescending: Boolean = false,
    scrollToTopTrigger: Int = 0,
    savedScrollPosition: com.voxly.presentation.viewmodel.ScrollPosition? = null,
    onSaveScrollPosition: ((Int, Int) -> Unit)? = null
) {
    // Restore scroll position
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollPosition?.index ?: 0,
        initialFirstVisibleItemScrollOffset = savedScrollPosition?.offset ?: 0
    )

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.scrollToItem(0)
        }
    }

    // Save scroll position when leaving
    DisposableEffect(albums.size) {
        onDispose {
            onSaveScrollPosition?.invoke(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    
    val albumsByYear = remember(albums, isDescending) {
        albums.groupBy { album ->
            getAlbumDisplayYear(album) ?: 0
        }.toSortedMap(if (isDescending) compareByDescending { it } else compareBy { it })
    }

    val yearGroups = remember(albumsByYear) {
        albumsByYear.map { (year, yearAlbums) ->
            YearGroup(year, yearAlbums)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            yearGroups.forEach { yearGroup ->
                item(key = "header_${yearGroup.year}") {
                    Text(
                        text = if (yearGroup.year == 0) "N/A" else yearGroup.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                items(
                    count = yearGroup.albums.size,
                    key = { index -> "album_${yearGroup.year}_${albumStableKey(yearGroup.albums[index])}" }
                ) { albumIndex ->
                    val album = yearGroup.albums[albumIndex]
                    val onItemClick = remember(album) { { onAlbumClick(album) } }
                    SegmentedListItem(
                        onClick = onItemClick,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = albumIndex,
                            count = yearGroup.albums.size
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        leadingContent = {
                            val coverFile = album.coverFile()
                            val albumCoverKey = createAlbumCoverSharedElementKey(album.name, album.albumArtist)
                            AlbumArtWithSharedElement(
                                filePath = coverFile?.path,
                                albumId = coverFile?.mediaStoreAlbumId,
                                sharedElementKey = albumCoverKey,
                                size = 40.dp
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val yearStr = getAlbumDisplayYearString(album)
                                Text(
                                    text = if (yearStr != null) "${album.albumArtist ?: ""} · $yearStr" else (album.albumArtist ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.track_count, album.files.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        content = {}
                    )
                }

                item(key = "spacer_${yearGroup.year}") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        LazyColumnScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            showBubble = false
        )
    }
}
