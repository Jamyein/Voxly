package com.voxly.presentation.screens.album

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.core.util.SortUtil
import com.voxly.data.local.AlbumSortOption
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.sharedElementIfAvailable
import com.voxly.presentation.screens.filebrowser.AlbumGridItem
import com.voxly.presentation.screens.filebrowser.getLeadingCharacter
import com.voxly.presentation.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState(initial = AlbumSortOption.NAME_ASC.name)
    var scrollToTopTrigger by remember { mutableIntStateOf(0) }
    var isSortExpanded by remember { mutableStateOf(false) }

    val currentSortOption = remember(sortOption) {
        try {
            AlbumSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            AlbumSortOption.NAME_ASC
        }
    }

    val sortedAlbums = remember(albums, currentSortOption) {
        applyAlbumSort(albums, currentSortOption)
    }

    val readPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPermission = granted
    }

    LaunchedEffect(hasReadPermission) {
        if (!hasReadPermission) {
            readPermissionLauncher.launch(readPermission)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                )
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
                    albums = sortedAlbums,
                    onAlbumClick = { album ->
                        onNavigateToAlbumDetail(album.name, album.artist)
                    },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    scrollToTopTrigger = scrollToTopTrigger,
                    sortOption = currentSortOption
                )
            }
        }
    }
}

@Composable
private fun AlbumTabContent(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState? = null,
    scrollToTopTrigger: Int = 0,
    sortOption: AlbumSortOption? = null
) {
    key(scrollToTopTrigger) {
        val isYearSort = sortOption == AlbumSortOption.YEAR_DESC
        val gridState = rememberLazyGridState()
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    val pullToRefreshState = rememberPullToRefreshState()
                    LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                    )
                }
            ) {
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
                            isDescending = true
                        )
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                count = albums.size,
                                key = { index -> albumStableKey(albums[index]) }
                            ) { index ->
                                val album = albums[index]
                                AlbumGridItem(
                                    album = album,
                                    onClick = { onAlbumClick(album) }
                                )
                            }
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
                    bubbleFormatter = { index ->
                        albums.getOrNull(index)?.let { getLeadingCharacter(it.name) } ?: "#"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumYearGroupedContent(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
    isDescending: Boolean = false
) {
    val listState = rememberLazyListState()
    val albumsByYear = remember(albums, isDescending) {
        albums.groupBy { album ->
            albumDisplayYearInt(album) ?: 0
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
                    SegmentedListItem(
                        onClick = { onAlbumClick(album) },
                        shapes = ListItemDefaults.segmentedShapes(
                            index = albumIndex,
                            count = yearGroup.albums.size
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        leadingContent = {
                            val albumCoverKey = createAlbumCoverSharedElementKey(album.name, album.artist)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .sharedElementIfAvailable(key = albumCoverKey),
                                contentAlignment = Alignment.Center
                            ) {
                                val coverFile = album.files.firstOrNull {
                                    it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
                                } ?: album.files.firstOrNull()
                                AlbumArtImage(
                                    filePath = coverFile?.path,
                                    mediaStoreAlbumId = coverFile?.mediaStoreAlbumId,
                                    contentDescription = null,
                                    size = 40.dp,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.artist ?: "",
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
            bubbleFormatter = { index ->
                yearGroups.getOrNull(index)?.let { group ->
                    if (group.year == 0) "N/A" else group.year.toString()
                } ?: "#"
            }
        )
    }
}

private data class YearGroup(
    val year: Int,
    val albums: List<AlbumGroup>
)

private fun albumDisplayYearInt(album: AlbumGroup): Int? {
    return album.files
        .mapNotNull { audioFile -> extractYear(audioFile.metadata.year) }
        .maxOrNull()
}

private fun albumStableKey(album: AlbumGroup): String {
    val representativePath = album.files.firstOrNull()?.path.orEmpty()
    return "${album.name}|${album.artist.orEmpty()}|$representativePath"
}

private fun extractYear(rawYear: String?): Int? {
    val normalized = rawYear?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return Regex("""\d{4}""").find(normalized)?.value?.toIntOrNull()
}

private fun AlbumSortOption.labelResId(): Int = when (this) {
    AlbumSortOption.NAME_ASC -> R.string.album_sort_name_asc
    AlbumSortOption.TRACK_COUNT_DESC -> R.string.album_sort_track_count_desc
    AlbumSortOption.YEAR_DESC -> R.string.album_sort_year_desc
}

private fun applyAlbumSort(
    albums: List<AlbumGroup>,
    sortOption: AlbumSortOption
): List<AlbumGroup> {
    return when (sortOption) {
        AlbumSortOption.NAME_ASC -> albums.sortedWith(
            compareBy { SortUtil.toSortablePinyin(it.name) }
        )
        AlbumSortOption.TRACK_COUNT_DESC -> albums.sortedByDescending { it.files.size }
        AlbumSortOption.YEAR_DESC -> albums.sortedByDescending { album ->
            album.files.mapNotNull { audioFile ->
                audioFile.metadata.year
                    ?.let { Regex("""\d{4}""").find(it)?.value }
                    ?.toIntOrNull()
            }.maxOrNull() ?: Int.MIN_VALUE
        }
    }
}