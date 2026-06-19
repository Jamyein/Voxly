package com.voxly.presentation.screens.artist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.voxly.presentation.components.LibraryRefreshBox
import com.voxly.presentation.components.LocalBottomBarVisibilityController
import com.voxly.presentation.components.chainNestedScrollConnections
import com.voxly.presentation.components.scrollbar.LazyColumnScrollbar
import com.voxly.presentation.screens.filebrowser.getLeadingCharacter
import com.voxly.presentation.viewmodel.ArtistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ArtistScreenContent(
    viewModel: ArtistViewModel,
    onArtistClick: (ArtistGroup) -> Unit,
    onShowSearchSheet: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val artistListItems by viewModel.artistListItems.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Get the bottom-bar visibility controller (provided by the host Scaffold via
    // ProvideBottomBarVisibilityController). Chained on the Scaffold modifier below so the
    // LazyColumn scroll events inside ArtistTabContent drive it.
    val bottomBarController = LocalBottomBarVisibilityController.current
    val bottomBarNestedScroll = remember(bottomBarController) {
        bottomBarController.nestedScrollConnection("Artists")
    }
    val chainedNestedScroll = remember(scrollBehavior, bottomBarNestedScroll) {
        chainNestedScrollConnections(scrollBehavior.nestedScrollConnection, bottomBarNestedScroll)
    }

    LaunchedEffect(Unit) {
        viewModel.scanError.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(Unit) {
        if (artists.isEmpty() && !isRefreshing) {
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(chainedNestedScroll),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
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
                ),
                actions = {
                    IconButton(onClick = onShowSearchSheet) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LibraryRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() }
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
                        listState = listState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArtistTabContent(
    artists: List<ArtistGroup>,
    artistListItems: List<ArtistListItemState>,
    onArtistClick: (ArtistGroup) -> Unit,
    listState: LazyListState? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val lazyListState = listState ?: rememberLazyListState()
    
    val artistMap = remember(artists) {
        artists.associateBy { it.name }
    }
    
    val bubbleFormatter: ((Int) -> String) = remember(artistListItems.size) {
        { index: Int ->
            artistListItems.getOrNull(index)?.let { getLeadingCharacter(it.name) } ?: "#"
        }
    }

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
                    val targetArtist = artistMap[listItem.name]
                    val onItemClick = remember(targetArtist) {
                        { if (targetArtist != null) onArtistClick(targetArtist) }
                    }
                    ArtistListItem(
                        artist = listItem,
                        onClick = onItemClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
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
