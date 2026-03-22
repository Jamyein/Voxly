package com.voxly.presentation.screens.album

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.core.util.SortUtil
import com.voxly.domain.model.AlbumGroup
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.screens.filebrowser.AlbumTabContent
import com.voxly.presentation.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var scrollToTopTrigger by remember { mutableIntStateOf(0) }
    var sortOption by rememberSaveable { mutableStateOf(AlbumSortOption.NAME_ASC.name) }
    var isSortExpanded by rememberSaveable { mutableStateOf(false) }

    val sortedAlbums = remember(albums, sortOption) {
        applyAlbumSort(albums, AlbumSortOption.valueOf(sortOption))
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
                        currentSortOption = AlbumSortOption.valueOf(sortOption),
                        options = AlbumSortOption.entries,
                        optionLabelResId = { it.labelResId() },
                        contentDescription = stringResource(R.string.album_sort_label),
                        onSortOptionChange = { sortOption = it.name }
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
                    sortOption = AlbumSortOption.valueOf(sortOption)
                )
            }
        }
    }
}

internal enum class AlbumSortOption {
    NAME_ASC,
    TRACK_COUNT_DESC,
    YEAR_DESC
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
