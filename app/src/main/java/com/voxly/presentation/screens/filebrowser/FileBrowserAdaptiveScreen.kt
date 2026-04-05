package com.voxly.presentation.screens.filebrowser

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.core.util.MediaPermission
import com.voxly.data.local.FileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.RootTab
import com.voxly.presentation.components.SearchBottomSheet
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory
import kotlinx.coroutines.launch

/**
 * Adaptive FileBrowser screen using Material3 ListDetailPaneScaffold.
 *
 * This is true adaptive design - Material3 automatically manages:
 * - Small screens: Single pane with full-screen navigation
 * - Medium screens: Dual pane with adjustable ratio
 * - Large screens: Dual pane with 40:60 split
 *
 * No conditional logic needed - Material3 handles all screen sizes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserAdaptiveScreen(
    onNavigateToDirectory: (String, String) -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = remember { MediaPermission.audioReadPermission(Build.VERSION.SDK_INT) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.refresh(forceRefresh = true)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.permission_storage_message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(hasAudioPermission, permissionRequested) {
        if (!hasAudioPermission && !permissionRequested) {
            permissionRequested = true
            requestAudioPermission.launch(audioPermission)
        }
    }

    // Material3 Adaptive Navigator - automatically handles all screen sizes
    val navigator = rememberListDetailPaneScaffoldNavigator<AudioFile>()

    // State collections - use correct property names
    val allAudios by viewModel.allAudios.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val selectedDirectories by viewModel.selectedDirectories.collectAsState()
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Root tab state (Directories / All)
    // When user has whitelist directories: default to DIRECTORIES mode, but remember user's choice
    // When user has no whitelist directories: force ALL mode and hide toggle button
    val hasWhitelistDirectories by viewModel.hasWhitelistDirectories.collectAsState()
    val rootTabString by viewModel.fileBrowserRootTab.collectAsState(initial = RootTab.DIRECTORIES.name)
    
    // Determine effective root tab based on whitelist state
    val effectiveRootTab = if (hasWhitelistDirectories) {
        // User has whitelist: respect their saved preference
        try {
            RootTab.valueOf(rootTabString)
        } catch (e: IllegalArgumentException) {
            RootTab.DIRECTORIES
        }
    } else {
        // No whitelist: force ALL mode
        RootTab.ALL
    }

    // Search and sort
    var showSearchSheet by remember { mutableStateOf(false) }
    var isSortExpanded by remember { mutableStateOf(false) }
    val sortOption by viewModel.fileBrowserSortOption.collectAsState(initial = FileSortOption.NAME_ASC.name)
    val currentSortOption = remember(sortOption) {
        try {
            FileSortOption.valueOf(sortOption)
        } catch (e: IllegalArgumentException) {
            FileSortOption.NAME_ASC
        }
    }

    // Sort audio files (search handled by SearchBottomSheet)
    val displayedFiles = remember(allAudios, currentSortOption) {
        applyFileSort(allAudios, currentSortOption)
    }

    // List pane state
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isSelectionMode = selectedFiles.isNotEmpty()
    val canScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    
    // Track file switch counter for proper ViewModel recreation
    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    
    // Determine if we're in single-pane mode (small screens)
    val isSinglePane = navigator.scaffoldValue.primary == PaneAdaptedValue.Hidden

    // Material3 ListDetailPaneScaffold - handles all screen sizes automatically
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // List pane TopAppBar
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.nav_file_browser),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            IconButton(onClick = { showSearchSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                            // Toggle between Directories and All modes (only show when whitelist directories exist)
                            if (hasWhitelistDirectories) {
                                IconButton(
                                    onClick = {
                                        val newTab = if (effectiveRootTab == RootTab.DIRECTORIES)
                                            RootTab.ALL.name
                                        else
                                            RootTab.DIRECTORIES.name
                                        viewModel.setFileBrowserRootTab(newTab)
                                    }
                                ) {
                                    Icon(
                                        painter = appIconPainter(
                                            if (effectiveRootTab == RootTab.DIRECTORIES)
                                                AppIcon.MusicNote
                                            else
                                                AppIcon.Folder
                                        ),
                                        contentDescription = stringResource(
                                            if (effectiveRootTab == RootTab.DIRECTORIES)
                                                R.string.switch_to_all_audios
                                            else
                                                R.string.switch_to_directories
                                        )
                                    )
                                }
                            }
                            // Only show sort button in All mode
                            if (effectiveRootTab == RootTab.ALL) {
                                SortMenuButton(
                                    expanded = isSortExpanded,
                                    onExpandedChange = { isSortExpanded = it },
                                    currentSortOption = currentSortOption,
                                    options = FileSortOption.entries,
                                    optionLabelResId = { it.labelResId() },
                                    contentDescription = "Sort",
                                    onSortOptionChange = { viewModel.setFileBrowserSortOption(it.name) }
                                )
                            }
                            IconButton(onClick = {
                                if (hasAudioPermission) {
                                    viewModel.refresh()
                                } else {
                                    requestAudioPermission.launch(audioPermission)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh_files)
                                )
                            }
                        }
                    )

                    // Content based on selected tab
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (effectiveRootTab == RootTab.DIRECTORIES) {
                                // Show directory list
                                DirectoryOverviewContent(
                                    directories = selectedDirectories,
                                    directoryFiles = directoryFiles,
                                    onOpenDirectory = { directoryUri, directoryName ->
                                        onNavigateToDirectory(directoryUri, directoryName)
                                    },
                                    isRefreshing = isRefreshing,
                                    onRefresh = {
                                        if (hasAudioPermission) {
                                            viewModel.refresh()
                                        } else {
                                            requestAudioPermission.launch(audioPermission)
                                        }
                                    },
                                    listState = listState,
                                    bottomPadding = 16.dp
                                )
                            } else {
                                // Show all files
                                AllAudiosTabContent(
                                    audios = displayedFiles,
                                    selectedFiles = selectedFiles,
                                onFileClick = { audioFile ->
                                    if (isSelectionMode) {
                                        viewModel.toggleFileSelection(audioFile.path)
                                    } else if (isSinglePane) {
                                        // Small screen: navigate to independent MetadataEditor
                                        onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path))
                                    } else {
                                        // Multi-pane: show in detail pane
                                        coroutineScope.launch {
                                            fileSwitchCounter++
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                                        }
                                    }
                                },
                                    onFileLongClick = { audioFile ->
                                        viewModel.toggleFileSelection(audioFile.path)
                                    },
                                    isRefreshing = isRefreshing,
                                    onRefresh = {
                                        if (hasAudioPermission) {
                                            viewModel.refresh()
                                        } else {
                                            requestAudioPermission.launch(audioPermission)
                                        }
                                    },
                                    listState = listState
                                )
                            }

                            // Back to top FAB
                            val showFab = canScrollToTop && 
                                if (effectiveRootTab == RootTab.DIRECTORIES) {
                                    selectedDirectories.isNotEmpty()
                                } else {
                                    displayedFiles.isNotEmpty()
                                }
                            if (showFab) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Back to top"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val currentFile = navigator.currentDestination?.contentKey
                if (currentFile != null) {
                    key(currentFile.path, fileSwitchCounter) {
                        // Create navKey for MetadataEditor
                        val navKey = MetadataEditor(
                            filePath = currentFile.path,
                            coverTag = createAlbumArtSharedElementKey(currentFile.path)
                        )
                        // Create ViewModel with proper factory and unique key
                        val metadataViewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
                            key = "${currentFile.path}_$fileSwitchCounter",
                            creationCallback = { factory -> factory.create(navKey) }
                        )
                        AdaptiveMetadataEditorContainer(
                            filePath = currentFile.path,
                            viewModel = metadataViewModel,
                            coverTag = createAlbumArtSharedElementKey(currentFile.path),
                            sharedElementKey = createAlbumArtSharedElementKey(currentFile.path),
                            onNavigateBack = {
                                // Use coroutine for suspend function
                                coroutineScope.launch {
                                    fileSwitchCounter++
                                    navigator.navigateBack()
                                }
                            },
                            onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                            onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                            onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
                            onNavigateToLyricsSelector = onNavigateToLyricsSelector
                        )
                    }
                } else {
                    EmptyDetailPane()
                }
            }
        },
        modifier = modifier
    )

    if (showSearchSheet) {
        SearchBottomSheet(
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(),
            onDismiss = { showSearchSheet = false },
            allFiles = allAudios,
            onFileClick = { audioFile ->
                showSearchSheet = false
                if (isSinglePane) {
                    onNavigateToMetadata(audioFile.path, createAlbumArtSharedElementKey(audioFile.path))
                } else {
                    coroutineScope.launch {
                        fileSwitchCounter++
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                    }
                }
            }
        )
    }
}

/**
 * Applies sorting to audio files.
 */
private fun applyFileSort(files: List<AudioFile>, sortOption: FileSortOption): List<AudioFile> {
    return when (sortOption) {
        FileSortOption.NAME_ASC -> files.sortedBy { it.metadata.getDisplayTitle(it.name) }
        FileSortOption.NAME_DESC -> files.sortedByDescending { it.metadata.getDisplayTitle(it.name) }
        FileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
        FileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
    }
}


