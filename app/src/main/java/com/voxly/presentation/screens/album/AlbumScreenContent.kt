package com.voxly.presentation.screens.album

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.data.local.AlbumSortOption
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.scrollbar.LazyVerticalGridScrollbar
import com.voxly.presentation.screens.filebrowser.AlbumGridItem
import com.voxly.presentation.screens.filebrowser.getLeadingCharacter
import com.voxly.presentation.viewmodel.AlbumViewModel

/**
 * Album screen content (list/grid) without navigation.
 * Used as the list pane in dual-pane layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumScreenContent(
    viewModel: AlbumViewModel,
    onAlbumClick: (AlbumGroup) -> Unit,
    modifier: Modifier = Modifier
) {
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
                key(scrollToTopTrigger) {
                    AlbumTabContent(
                        albums = sortedAlbums,
                        onAlbumClick = onAlbumClick,
                        scrollToTopTrigger = scrollToTopTrigger,
                        sortOption = currentSortOption
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlbumTabContent(
    albums: List<AlbumGroup>,
    onAlbumClick: (AlbumGroup) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    scrollToTopTrigger: Int = 0,
    sortOption: AlbumSortOption? = null
) {
    key(scrollToTopTrigger) {
        val isYearSort = sortOption == AlbumSortOption.YEAR_DESC
        val gridState = rememberLazyGridState()

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
                        isDescending = true
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
                            AlbumGridItem(
                                album = album,
                                onClick = { onAlbumClick(album) }
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
                    bubbleFormatter = { index ->
                        albums.getOrNull(index)?.let { getLeadingCharacter(it.name) } ?: "#"
                    }
                )
            }
        }
    }
}
