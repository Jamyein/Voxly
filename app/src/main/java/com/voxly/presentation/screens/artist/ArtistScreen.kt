package com.voxly.presentation.screens.artist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.core.util.SortUtil
import com.voxly.presentation.components.SortDropdownMenu
import com.voxly.presentation.screens.filebrowser.ArtistTabContent
import com.voxly.presentation.viewmodel.ArtistViewModel

private enum class ArtistSortOption {
    NAME_ASC,
    NAME_DESC,
    SONG_COUNT_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToArtistDetail: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val artists by viewModel.artists.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberLazyListState()

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Sort state
    var sortOption by remember { mutableStateOf(ArtistSortOption.NAME_ASC) }
    var isSortExpanded by remember { mutableStateOf(false) }

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

    // Passive permission check - no active refresh trigger
    // Data is collected from AudioFileScanner via artists StateFlow
    LaunchedEffect(hasReadPermission) {
        if (!hasReadPermission) {
            readPermissionLauncher.launch(readPermission)
        }
    }

    // Filter and sort artists
    val displayedArtists = remember(artists, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) {
            artists
        } else {
            val query = searchQuery.lowercase()
            artists.filter { artist ->
                artist.name.lowercase().contains(query)
            }
        }
        when (sortOption) {
            ArtistSortOption.NAME_ASC -> filtered.sortedWith(compareBy { SortUtil.toSortablePinyin(it.name) })
            ArtistSortOption.NAME_DESC -> filtered.sortedWith(compareByDescending { SortUtil.toSortablePinyin(it.name) })
            ArtistSortOption.SONG_COUNT_DESC -> filtered.sortedByDescending { it.files.size }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                placeholder = { Text(stringResource(R.string.file_search_hint)) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                        }
                                    }
                                }
                            )
                        } else {
                            Text(stringResource(R.string.tab_artists))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = {
                            if (isSearchActive && searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            }
                            isSearchActive = !isSearchActive
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = if (isSearchActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        Box {
                            IconButton(onClick = { isSortExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.artist_sort_label),
                                    tint = if (isSortExpanded) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            SortDropdownMenu(
                                isExpanded = isSortExpanded,
                                currentSortOption = sortOption,
                                sortOptions = listOf(
                                    ArtistSortOption.NAME_ASC to R.string.artist_sort_name_asc,
                                    ArtistSortOption.NAME_DESC to R.string.artist_sort_name_desc,
                                    ArtistSortOption.SONG_COUNT_DESC to R.string.artist_sort_song_count_desc
                                ),
                                onSortOptionChange = { sortOption = it },
                                onDismiss = { isSortExpanded = false }
                            )
                        }
                    }
                )
            }
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
            if (displayedArtists.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            stringResource(R.string.file_search_empty)
                        } else {
                            stringResource(R.string.no_artists_found)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ArtistTabContent(
                    artists = displayedArtists,
                    onArtistClick = { artist ->
                        onNavigateToArtistDetail(artist.name)
                    },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    listState = listState
                )
            }
        }
    }
}
