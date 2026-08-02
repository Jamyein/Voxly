package com.voxly.presentation.screens.filebrowser

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.voxly.core.util.Constants
import com.voxly.data.local.FileSortOption
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.RootTab
import com.voxly.presentation.components.LocalBottomBarVisibilityController
import com.voxly.presentation.components.chainNestedScrollConnections
import com.voxly.presentation.components.SearchBottomSheet
import com.voxly.presentation.components.SortMenuButton
import com.voxly.presentation.components.adaptive.EmptyDetailPane
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.openMetadataFor
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.screens.metadata.AdaptiveMetadataEditorContainer
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import com.voxly.presentation.viewmodel.LibrarySettingsViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Adaptive FileBrowser screen using Material3 ListDetailPaneScaffold.
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
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    scanViewModel: LibraryScanViewModel = hiltViewModel(),
    settingsViewModel: LibrarySettingsViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isExpandedWidth = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )
    val audioPermission = remember { Constants.mediaReadPermission(Build.VERSION.SDK_INT) }
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
            // First-time permission grant runs a full rescan via the shared
            // refresh path so Files / Albums / Artists all observe it. The
            // version-cache bypass is not strictly needed here because we
            // also pass forceRefresh=true, but we route through the
            // scanViewModel for consistency.
            scanViewModel.refresh(forceRefresh = true)
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

    val navigator = rememberListDetailPaneScaffoldNavigator<AudioFile>()
    val scanUiState by scanViewModel.fileBrowserUiState.collectAsStateWithLifecycle()
    val allAudios = scanUiState.allAudios
    val displayedFiles by scanViewModel.sortedAllAudios.collectAsStateWithLifecycle()
    val selectedDirectories = scanUiState.selectedDirectories
    val directoryFiles = scanUiState.directoryFiles
    val isRefreshing = scanUiState.isRefreshing
    val isInitialLoad = scanUiState.isInitialLoad
    val hasWhitelistDirectories = scanUiState.hasWhitelistDirectories
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val rootTabString by settingsViewModel.fileBrowserRootTab.collectAsStateWithLifecycle(initialValue = RootTab.DIRECTORIES.name)
    val currentSortOption by scanViewModel.currentFileSortOption.collectAsStateWithLifecycle()
    val effectiveRootTab = if (hasWhitelistDirectories) {
        try {
            RootTab.valueOf(rootTabString)
        } catch (e: IllegalArgumentException) {
            RootTab.DIRECTORIES
        }
    } else {
        RootTab.ALL
    }

    var showSearchSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isSelectionMode = selectedFiles.isNotEmpty()
    val canScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Get the bottom-bar visibility controller (provided by the host Scaffold via
    // ProvideBottomBarVisibilityController). We chain a SECOND NestedScrollConnection on
    // top of the collapsing top-app-bar connection so both the top bar collapse and the
    // bottom bar hide/show can be driven by the same scroll gesture.
    val bottomBarController = LocalBottomBarVisibilityController.current
    val bottomBarScreenId = "FileBrowser"
    val bottomBarNestedScroll = remember(bottomBarController, bottomBarScreenId) {
        bottomBarController.nestedScrollConnection(bottomBarScreenId)
    }
    // Chain the collapsing top-app-bar connection with the bottom-bar hide/show connection
    // so a single scroll gesture drives both. Compose's Modifier.nestedScroll only takes one
    // connection, so we wrap them.
    val chainedNestedScroll = remember(scrollBehavior, bottomBarNestedScroll) {
        chainNestedScrollConnections(scrollBehavior.nestedScrollConnection, bottomBarNestedScroll)
    }

    // Pin the bar visible when the user is at the top of the list — they came to the top
    // intentionally (FAB tap, system back, etc.) and we should not hide the bar on the next
    // down-flick because that direction will read as "downward scroll" once content moves.
    LaunchedEffect(canScrollToTop) {
        bottomBarController.setForcedVisible(!canScrollToTop)
    }

    var fileSwitchCounter by remember { mutableIntStateOf(0) }
    val isSinglePane = navigator.scaffoldValue.primary == PaneAdaptedValue.Hidden
    val canCloseDetailPane = !isSinglePane && navigator.currentDestination != null

    val detailPaneState = remember { SeekableTransitionState(initialState = false) }

    PredictiveBackHandler(enabled = isSelectionMode || canCloseDetailPane) { progress ->
        try {
            progress.collect { backEvent ->
                detailPaneState.seekTo(fraction = backEvent.progress)
            }
            when {
                isSelectionMode -> {
                    detailPaneState.animateTo(targetState = true)
                    viewModel.clearSelection()
                }
                canCloseDetailPane -> {
                    detailPaneState.animateTo(targetState = true)
                    coroutineScope.launch {
                        navigator.navigateBack()
                    }
                }
            }
        } catch (e: CancellationException) {
            detailPaneState.snapTo(targetState = false)
        }
    }

    val listPaneContent: @Composable () -> Unit = {
        FileBrowserListPane(
            effectiveRootTab = effectiveRootTab,
            hasWhitelistDirectories = hasWhitelistDirectories,
            displayedFiles = displayedFiles,
            selectedDirectories = selectedDirectories,
            directoryFiles = directoryFiles,
            isRefreshing = isRefreshing,
            isInitialLoad = isInitialLoad,
            hasAudioPermission = hasAudioPermission,
            onRequestAudioPermission = { requestAudioPermission.launch(audioPermission) },
            onRefresh = { scanViewModel.refresh() },
            onToggleRootTab = {
                val newTab = if (effectiveRootTab == RootTab.DIRECTORIES)
                    RootTab.ALL.name
                else
                    RootTab.DIRECTORIES.name
                settingsViewModel.setFileBrowserRootTab(newTab)
            },
            onNavigateToDirectory = onNavigateToDirectory,
            onNavigateToSettings = onNavigateToSettings,
            isSinglePane = isSinglePane || isExpandedWidth,
            isSelectionMode = isSelectionMode,
            selectedFiles = selectedFiles,
            onFileClick = remember(viewModel, isSelectionMode, isSinglePane, isExpandedWidth, coroutineScope, navigator, onNavigateToMetadata) {
                { audioFile ->
                    if (isSelectionMode) {
                        viewModel.toggleFileSelection(audioFile.path)
                    } else if (isSinglePane || isExpandedWidth) {
                        openMetadataFor(onNavigateToMetadata, audioFile)
                    } else {
                        coroutineScope.launch {
                            fileSwitchCounter++
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, audioFile)
                        }
                    }
                }
            },
            onFileLongClick = remember(viewModel) {
                { audioFile ->
                    viewModel.toggleFileSelection(audioFile.path)
                }
            },
            listState = listState,
            currentSortOption = currentSortOption,
            onSortOptionChange = { settingsViewModel.setFileBrowserSortOption(it.name) },
            onShowSearchSheet = { showSearchSheet = true },
            canScrollToTop = canScrollToTop,
            scrollBehavior = scrollBehavior,
            modifier = Modifier.fillMaxSize(),
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }

    if (isExpandedWidth) {
        Box(modifier = modifier.nestedScroll(chainedNestedScroll)) {
            listPaneContent()
        }
    } else {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = { AnimatedPane { listPaneContent() } },
            detailPane = {
                AnimatedPane {
                    FileBrowserDetailPane(
                        currentFile = navigator.currentDestination?.contentKey,
                        fileSwitchCounter = fileSwitchCounter,
                        onFileSwitch = { fileSwitchCounter++ },
                        onNavigateBack = {
                            coroutineScope.launch {
                                fileSwitchCounter++
                                navigator.navigateBack()
                            }
                        },
                        onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                        onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                        onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
                        onNavigateToLyricsSelector = onNavigateToLyricsSelector,
                        modifier = Modifier.fillMaxSize(),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            },
            modifier = modifier.nestedScroll(chainedNestedScroll)
        )
    }

    if (showSearchSheet) {
        SearchBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(
                    SheetValue.Hidden,
                    SheetValue.Expanded
                )
            ),
            onDismiss = { showSearchSheet = false },
            allFiles = allAudios,
            onFileClick = { audioFile ->
                showSearchSheet = false
                if (isSinglePane || isExpandedWidth) {
                    openMetadataFor(onNavigateToMetadata, audioFile)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileBrowserListPane(
    effectiveRootTab: RootTab,
    hasWhitelistDirectories: Boolean,
    displayedFiles: List<AudioFile>,
    selectedDirectories: List<com.voxly.presentation.viewmodel.SelectedDirectory>,
    directoryFiles: Map<String, List<AudioFile>>,
    isRefreshing: Boolean,
    isInitialLoad: Boolean,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRootTab: () -> Unit,
    onNavigateToDirectory: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    isSinglePane: Boolean,
    isSelectionMode: Boolean,
    selectedFiles: Set<String>,
    onFileClick: (AudioFile) -> Unit,
    onFileLongClick: (AudioFile) -> Unit,
    listState: LazyGridState,
    currentSortOption: FileSortOption,
    onSortOptionChange: (FileSortOption) -> Unit,
    onShowSearchSheet: () -> Unit,
    canScrollToTop: Boolean,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var isSortExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        MediumTopAppBar(
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
                IconButton(onClick = onShowSearchSheet) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
                if (hasWhitelistDirectories) {
                    IconButton(onClick = onToggleRootTab) {
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
                if (effectiveRootTab == RootTab.ALL) {
                    SortMenuButton(
                        expanded = isSortExpanded,
                        onExpandedChange = { isSortExpanded = it },
                        currentSortOption = currentSortOption,
                        options = FileSortOption.entries,
                        optionLabelResId = { it.labelResId() },
                        contentDescription = "Sort",
                        onSortOptionChange = onSortOptionChange
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = AppIcon.Settings.vector,
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                }
            }
        )

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (effectiveRootTab == RootTab.DIRECTORIES) {
                    DirectoryOverviewContent(
                        directories = selectedDirectories,
                        directoryFiles = directoryFiles,
                        onOpenDirectory = onNavigateToDirectory,
                        isRefreshing = isRefreshing,
                        isInitialLoad = isInitialLoad,
                        onRefresh = {
                            if (hasAudioPermission) {
                                onRefresh()
                            } else {
                                onRequestAudioPermission()
                            }
                        },
                        listState = listState,
                        bottomPadding = 16.dp
                    )
                } else {
                    AllAudiosTabContent(
                        audios = displayedFiles,
                        selectedFiles = selectedFiles,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        isRefreshing = isRefreshing,
                        isInitialLoad = isInitialLoad,
                        onRefresh = {
                            if (hasAudioPermission) {
                                onRefresh()
                            } else {
                                onRequestAudioPermission()
                            }
                        },
                        listState = listState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }

                val showFab by remember {
                    derivedStateOf {
                        canScrollToTop &&
                            if (effectiveRootTab == RootTab.DIRECTORIES) {
                                selectedDirectories.isNotEmpty()
                            } else {
                                displayedFiles.isNotEmpty()
                            }
                    }
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

@Composable
private fun FileBrowserDetailPane(
    currentFile: AudioFile?,
    fileSwitchCounter: Int,
    onFileSwitch: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToOnlineMetadata: () -> Unit,
    onNavigateToOnlineLyricsSearch: () -> Unit,
    onNavigateToOnlineCoverSearch: () -> Unit,
    onNavigateToLyricsSelector: (String, String, String, String, ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    if (currentFile != null) {
        key(currentFile.path, fileSwitchCounter) {
            val navKey = MetadataEditor(
                filePath = currentFile.path,
                coverTag = createAlbumArtSharedElementKey(currentFile.path)
            )
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
                    onFileSwitch()
                    onNavigateBack()
                },
                onNavigateToOnlineMetadata = onNavigateToOnlineMetadata,
                onNavigateToOnlineLyricsSearch = onNavigateToOnlineLyricsSearch,
                onNavigateToOnlineCoverSearch = onNavigateToOnlineCoverSearch,
                onNavigateToLyricsSelector = onNavigateToLyricsSelector,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        EmptyDetailPane()
    }
}

