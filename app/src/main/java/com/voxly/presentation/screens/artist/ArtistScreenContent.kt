package com.voxly.presentation.screens.artist

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.ArtistListItemState
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.components.LazyListCoverPreloader
import com.voxly.presentation.screens.filebrowser.getLeadingCharacter
import com.voxly.presentation.viewmodel.ArtistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistScreenContent(
    viewModel: ArtistViewModel,
    onArtistClick: (ArtistGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val artists by viewModel.artists.collectAsState()
    val artistListItems by viewModel.artistListItems.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                })
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.tab_artists),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (artists.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_artists_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ArtistTabContent(
                    artists = artists,
                    artistListItems = artistListItems,
                    onArtistClick = onArtistClick,
                    listState = listState
                )
            }
        }
    }
}

@Composable
internal fun ArtistTabContent(
    artists: List<ArtistGroup>,
    artistListItems: List<ArtistListItemState>,
    onArtistClick: (ArtistGroup) -> Unit,
    listState: LazyListState? = null
) {
    val lazyListState = listState ?: rememberLazyListState()
    val artistFilePaths = remember(artistListItems) {
        artistListItems.mapNotNull { it.coverPath }
    }
    val bubbleFormatter: ((Int) -> String) = remember(artistListItems) {
        { index: Int ->
            artistListItems.getOrNull(index)?.let { getLeadingCharacter(it.name) } ?: "#"
        }
    }
    val artistMap = remember(artists) {
        artists.associateBy { it.name }
    }
    LazyListCoverPreloader(listState = lazyListState, filePaths = artistFilePaths)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (artistListItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_artists_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(artistListItems, key = { it.name }) { listItem ->
                    ArtistListItem(
                        artist = listItem,
                        onClick = {
                            artistMap[listItem.name]?.let { onArtistClick(it) }
                        }
                    )
                }
            }
        }

        if (artistListItems.isNotEmpty()) {
            LazyColumnScrollbar(
                state = lazyListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                bubbleFormatter = bubbleFormatter
            )
        }
    }
}
