package com.voxly.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.voxly.R
import com.voxly.data.local.AlbumSortOption
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.components.AnimatedBottomBarContainer
import com.voxly.presentation.components.FloatingNavBarItem
import com.voxly.presentation.components.FloatingToolbarNavigationBar
import com.voxly.presentation.components.ProvideBottomBarVisibilityController
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.screens.ReplayGainScannerScreen
import com.voxly.presentation.screens.SettingsScreen
import com.voxly.presentation.screens.album.AlbumAdaptiveScreen
import com.voxly.presentation.screens.album.AlbumDetailScreen
import com.voxly.presentation.screens.album.AlbumTabContent
import com.voxly.presentation.screens.artist.ArtistScreenContent
import com.voxly.presentation.screens.artist.ArtistDetailScreen
import com.voxly.presentation.screens.filebrowser.DirectoryContentAdaptiveScreen
import com.voxly.presentation.screens.filebrowser.FileBrowserAdaptiveScreen
import com.voxly.presentation.screens.log.LogViewerScreen
import com.voxly.presentation.screens.settings.SourceSettingsScreen
import com.voxly.presentation.screens.metadata.LyricsPosterScreen
import com.voxly.presentation.screens.metadata.LyricsSelectorScreen
import com.voxly.presentation.screens.metadata.MetadataEditorScreen
import com.voxly.presentation.screens.metadata.OnlineCoverSearchScreen
import com.voxly.presentation.screens.metadata.OnlineLyricsSearchScreen
import com.voxly.presentation.screens.metadata.OnlineMetadataScreen
import com.voxly.presentation.theme.ExpressiveAnimations
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.viewmodel.AlbumViewModel
import com.voxly.presentation.viewmodel.ArtistDetailViewModel
import com.voxly.presentation.viewmodel.ArtistViewModel
import com.voxly.presentation.viewmodel.LibraryBatchViewModel
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import com.voxly.presentation.viewmodel.LibrarySettingsViewModel
import com.voxly.presentation.viewmodel.LibraryViewModel
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import com.voxly.presentation.viewmodel.LyricsSelectorViewModel
import com.voxly.presentation.viewmodel.MetadataEditorViewModel
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.ReplayGainViewModel
import com.voxly.presentation.viewmodel.SettingsViewModel
import timber.log.Timber

/**
 * Main navigation host for the MP3 Tag Editor app using official Navigation3 APIs.
 */
@Composable
private fun containerTransformMetadata(): Map<String, Any> {
    val enter = ExpressiveAnimations.containerTransformSharedElementEnter()
    val exit = ExpressiveAnimations.containerTransformSharedElementExit()
    val popEnter = ExpressiveAnimations.containerTransformSharedElementPopEnter()
    val popExit = ExpressiveAnimations.containerTransformSharedElementPopExit()
    val predictiveBackEnter = ExpressiveAnimations.containerTransformPredictiveBackEnter()
    val predictiveBackExit = ExpressiveAnimations.containerTransformPredictiveBackExit()
    return metadata {
        put(NavDisplay.TransitionKey) { enter togetherWith exit }
        put(NavDisplay.PopTransitionKey) { popEnter togetherWith popExit }
        put(NavDisplay.PredictivePopTransitionKey) { predictiveBackEnter togetherWith predictiveBackExit }
    }
}

@Composable
private fun sharedAxisXMetadata(): Map<String, Any> {
    val enter = ExpressiveAnimations.sharedAxisXEnter()
    val exit = ExpressiveAnimations.sharedAxisXExit()
    val popEnter = ExpressiveAnimations.sharedAxisXPopEnter()
    val popExit = ExpressiveAnimations.sharedAxisXPopExit()
    val predictiveBackEnter = ExpressiveAnimations.containerTransformPredictiveBackEnter()
    val predictiveBackExit = ExpressiveAnimations.containerTransformPredictiveBackExit()
    return metadata {
        put(NavDisplay.TransitionKey) { enter togetherWith exit }
        put(NavDisplay.PopTransitionKey) { popEnter togetherWith popExit }
        put(NavDisplay.PredictivePopTransitionKey) { predictiveBackEnter togetherWith predictiveBackExit }
    }
}

/**
 * Transition for top-level tab switches (Files / Albums / Artists / Settings): a non-directional
 * fade + scale, since tabs have no horizontal navigation semantics. Replaces NavDisplay's default
 * 700ms tween fade so tab switches use the same spring language as every other page transition.
 */
@Composable
private fun fadePageMetadata(): Map<String, Any> {
    val enter = ExpressiveAnimations.fadePageEnter()
    val exit = ExpressiveAnimations.fadePageExit()
    val predictiveBackEnter = ExpressiveAnimations.containerTransformPredictiveBackEnter()
    val predictiveBackExit = ExpressiveAnimations.containerTransformPredictiveBackExit()
    return metadata {
        put(NavDisplay.TransitionKey) { enter togetherWith exit }
        put(NavDisplay.PopTransitionKey) { enter togetherWith exit }
        put(NavDisplay.PredictivePopTransitionKey) { predictiveBackEnter togetherWith predictiveBackExit }
    }
}


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun <T : Any> rememberListDetailSceneStrategy(): androidx.navigation3.scene.SceneStrategy<T> {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    return rememberListDetailSceneStrategy<T>(directive = directive)
}

/**
 * Blocks pointer input on NavEntries whose scene is animating out, so taps can't land on a page
 * that's visually gone. NavDisplay keeps the outgoing scene composed (and hit-testable) for the
 * whole transition and Compose hit-testing ignores alpha/scale, so without this, taps during a push
 * fall through the new screen's empty areas onto the list beneath, and during a pop the invisible
 * outgoing screen (rendered on top) swallows every tap. AnimatedContent gives each entering/exiting
 * content its own AnimatedVisibilityScope; the exiting content's EnterExitState targets PostExit
 * while the entering/settled content targets Visible, so we consume every pointer event (Initial
 * pass) on the wrapper only while it is exiting — the incoming screen stays interactive the moment
 * it appears.
 */
@Composable
private fun transitionInputBlocker(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator<NavKey>(
        decorate = @Composable { entry ->
            val exiting =
                LocalNavAnimatedContentScope.current.transition.targetState != EnterExitState.Visible
            val blockModifier =
                if (exiting) {
                    // Only install the consuming pointer node while exiting — an idle pointerInput
                    // on every entry root makes the ComposeView report edge touches as handled,
                    // which can suppress the system's first predictive-back gesture.
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            Box(modifier = blockModifier) { entry.Content() }
        }
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun MP3TagNavHost() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val libraryScanViewModel: LibraryScanViewModel = hiltViewModel()
    val librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel()
    val libraryBatchViewModel: LibraryBatchViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val floatingBottomNavEnabled = settingsUiState.floatingBottomNavEnabled

    val topLevelBackStack = rememberTopLevelBackStack(FileBrowser)

    var pendingLyrics by remember { mutableStateOf<String?>(null) }
    var pendingCoverArt by remember { mutableStateOf<ByteArray?>(null) }

    val topLevelRoute = topLevelBackStack.topLevelKey
    val showNavigationBar = isMainScreenKey(topLevelBackStack.backStack.lastOrNull())
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    SharedTransitionLayout {
        val sharedTransitionScope = this@SharedTransitionLayout

        val navDisplayContent: @Composable () -> Unit = {
            // The shared BottomBarVisibilityController is provided by the outer wrapper that
            // wraps the Scaffold below (see comment at the Scaffold call). Both the bottomBar
            // slot (which reads `isVisible` to slide the bar in/out) and inner screens
            // (FileBrowser, Albums, Artists — which register a NestedScrollConnection) need to
            // resolve the controller, so it MUST sit above the Scaffold so both slots see it.
            MP3TagNavDisplay(
                topLevelBackStack = topLevelBackStack,
                sharedTransitionScope = sharedTransitionScope,
                libraryViewModel = libraryViewModel,
                libraryScanViewModel = libraryScanViewModel,
                librarySettingsViewModel = librarySettingsViewModel,
                libraryBatchViewModel = libraryBatchViewModel,
                pendingLyrics = pendingLyrics,
                onPendingLyricsConsumed = { pendingLyrics = null },
                pendingCoverArt = pendingCoverArt,
                onPendingCoverArtConsumed = { pendingCoverArt = null },
                onPendingLyricsSet = { pendingLyrics = it },
                onPendingCoverArtSet = { pendingCoverArt = it }
            )
        }

        val isFileSelected = topLevelRoute is FileBrowser
        val isAlbumsSelected = topLevelRoute is Albums
        val isArtistsSelected = topLevelRoute is Artists

        val onFileBrowserClick = dropUnlessResumed {
            if (!isFileSelected) topLevelBackStack.addTopLevel(FileBrowser)
        }
        val onAlbumsClick = dropUnlessResumed {
            if (!isAlbumsSelected) topLevelBackStack.addTopLevel(Albums)
        }
        val onArtistsClick = dropUnlessResumed {
            if (!isArtistsSelected) topLevelBackStack.addTopLevel(Artists)
        }

        // Per the Navigation 3 official "Common UI" recipe
        // (kb://android/guide/navigation/navigation-3/recipes/common-ui), the top-level shell
        // is `Scaffold(bottomBar = { NavigationBar { ... } })` + `NavDisplay`. We extend the
        // recipe for tablets: instead of NavigationSuiteScaffold's auto-rail (which mixes M3
        // convenience into a Navigation 3 host), we render ModalWideNavigationRail ourselves
        // and pass an EMPTY bottomBar on Expanded so the Scaffold reserves no bottom space —
        // this avoids the gesture-nav ghost area entirely.
        //
        // M3 1.5.0-alpha21 does not ship a "modal wide rail" layout in NavigationSuiteScaffold
        // (only WideNavigationRailCollapsed/Expanded, which are non-modal). The doc's
        // "Customize navigation types" pattern uses NavigationSuiteType.NavigationDrawer
        // (PermanentDrawerSheet); for the modal variant we follow the component sample at
        // androidx.compose.material3.samples.ModalWideNavigationRailSample.
        //
        // **Edge-to-edge note**: M3 Scaffold's source comment says
        //   "Scaffold will take the insets into account from the top/bottom only if the
        //    topBar/bottomBar are not present, as the scaffold expect topBar/bottomBar to
        //    handle insets instead."
        // With our empty topBar slot, the default `contentWindowInsets` (system bars) would
        // cause the outer Scaffold to consume the status-bar inset, pushing the inner screen
        // Scaffolds' TopAppBars down so they cannot reach the status bar. The empty bottomBar
        // on Expanded would do the same to the gesture-nav inset, leaving a non-immersive band.
        // Setting `contentWindowInsets = WindowInsets(0)` makes the outer shell claim NO insets
        // — each inner screen Scaffold (or M3 component) handles its own window insets.
        val windowSizeClass = adaptiveInfo.windowSizeClass
        val isExpandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        val railState = rememberWideNavigationRailState()
        val railScope = rememberCoroutineScope()

        // Wrap the Scaffold so BOTH the bottomBar slot (which calls
        // AnimatedBottomBarContainer and reads LocalBottomBarVisibilityController.current)
        // AND the inner content subtree (where FileBrowser/Albums/Artists bind their
        // NestedScrollConnection) can resolve the controller. The previous design wrapped
        // only navDisplayContent (a child of the content slot), which meant the bottomBar
        // slot crashed on paths that previously never rendered the bar — e.g. large-screen
        // + floating bottom bar enabled. The screenId changes when the top-level destination
        // changes, which causes the controller to reset its scroll accumulator
        // (see BottomBarVisibilityController.bind).
        ProvideBottomBarVisibilityController(screenId = topLevelBackStack.topLevelKey.toString()) {
            Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Only show the bottom NavigationBar on compact widths. On Expanded we leave
                // the slot empty (no composable) so Scaffold reserves zero bottom space.
                // 大屏 + 悬浮底栏开启 → 走底部栏路径(覆盖 NavigationRail);
                // 否则保持原行为:仅在非大屏显示底部栏,大屏留空给 rail 用。
                val showBottomBar = showNavigationBar && (
                    !isExpandedWidth || floatingBottomNavEnabled
                )
                if (showBottomBar) {
                    // AnimatedBottomBarContainer reads the visibility state from the shared
                    // BottomBarVisibilityController (provided by ProvideBottomBarVisibilityController
                    // in navDisplayContent) and animates the slot's height in/out. When the bar
                    // is hidden the Scaffold reserves no bottom space and the inner LazyColumn
                    // gets more vertical room to scroll through.
                    AnimatedBottomBarContainer {
                        if (floatingBottomNavEnabled) {
                            // M3E Floating Pill — use the custom FloatingNavBarItem (not
                            // NavigationBarItem) so we suppress the icon ripple / gray
                            // state-layer that M3 paints on selection. Each item paints its
                            // own content-sized capsule that expands from the center on
                            // selection.
                            FloatingToolbarNavigationBar {
                                FloatingNavBarItem(
                                    selected = isFileSelected,
                                    onClick = onFileBrowserClick,
                                    icon = AppIcon.FolderOutlined.vector,
                                    selectedIcon = AppIcon.Folder.vector,
                                    label = "Files"
                                )
                                FloatingNavBarItem(
                                    selected = isAlbumsSelected,
                                    onClick = onAlbumsClick,
                                    icon = AppIcon.AlbumOutlined.vector,
                                    selectedIcon = AppIcon.Album.vector,
                                    label = "Albums"
                                )
                                FloatingNavBarItem(
                                    selected = isArtistsSelected,
                                    onClick = onArtistsClick,
                                    icon = AppIcon.ArtistOutlined.vector,
                                    selectedIcon = AppIcon.Artist.vector,
                                    label = "Artists"
                                )
                            }
                        } else {
                            // Standard M3 NavigationBar — built-in icon-only indicator pill.
                            NavigationBar {
                                NavigationBarItem(
                                    selected = isFileSelected,
                                    onClick = onFileBrowserClick,
                                    icon = {
                                        Icon(
                                            imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                                            contentDescription = "Files"
                                        )
                                    },
                                    label = { Text("Files") }
                                )
                                NavigationBarItem(
                                    selected = isAlbumsSelected,
                                    onClick = onAlbumsClick,
                                    icon = {
                                        Icon(
                                            imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                                            contentDescription = "Albums"
                                        )
                                    },
                                    label = { Text("Albums") }
                                )
                                NavigationBarItem(
                                    selected = isArtistsSelected,
                                    onClick = onArtistsClick,
                                    icon = {
                                        Icon(
                                            imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                                            contentDescription = "Artists"
                                        )
                                    },
                                    label = { Text("Artists") }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Reserve the bottomBar slot ONLY for the opaque M3 NavigationBar (non-floating),
            // whose surface the list must clear. The floating pill instead OVERLAYS the content:
            // Scaffold draws the bottomBar on top of the content (BottomBarZIndex > ContentZIndex),
            // so content runs edge-to-edge behind the pill — reserving innerPadding there would
            // leave a tall band of the Scaffold's containerColor (near-black in dark theme)
            // between the list and the pill. Even with contentWindowInsets = WindowInsets(0),
            // innerPadding.bottom equals the bar's measured height whenever the bottomBar slot is
            // non-empty, and collapses to zero when the bar hides. consumeWindowInsets forwards
            // the reservation to inner screens so their own navigationBar paddings (FAB, list
            // contentPadding) don't double up.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (floatingBottomNavEnabled) {
                            // Floating pill: no reservation — content stays edge-to-edge under it.
                            Modifier
                        } else {
                            Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                        }
                    )
            ) {
            if (isExpandedWidth) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 大屏 + 悬浮底栏开启 → 不渲染 rail,把整行让给内容
                    if (showNavigationBar && !floatingBottomNavEnabled) {
                        ModalWideNavigationRail(
                            state = railState,
                            header = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(onClick = { railScope.launch { railState.toggle() } }) {
                                        Icon(
                                            imageVector = if (railState.targetValue == WideNavigationRailValue.Expanded) {
                                                Icons.AutoMirrored.Filled.MenuOpen
                                            } else {
                                                Icons.Filled.Menu
                                            },
                                            contentDescription = "Toggle navigation rail"
                                        )
                                    }
                                }
                            }
                        ) {
                            val railExpanded = railState.targetValue == WideNavigationRailValue.Expanded
                            WideNavigationRailItem(
                                selected = isFileSelected,
                                onClick = onFileBrowserClick,
                                icon = {
                                    Icon(
                                        imageVector = if (isFileSelected) AppIcon.Folder.vector else AppIcon.FolderOutlined.vector,
                                        contentDescription = "Files"
                                    )
                                },
                                label = { Text("Files") },
                                railExpanded = railExpanded
                            )
                            WideNavigationRailItem(
                                selected = isAlbumsSelected,
                                onClick = onAlbumsClick,
                                icon = {
                                    Icon(
                                        imageVector = if (isAlbumsSelected) AppIcon.Album.vector else AppIcon.AlbumOutlined.vector,
                                        contentDescription = "Albums"
                                    )
                                },
                                label = { Text("Albums") },
                                railExpanded = railExpanded
                            )
                            WideNavigationRailItem(
                                selected = isArtistsSelected,
                                onClick = onArtistsClick,
                                icon = {
                                    Icon(
                                        imageVector = if (isArtistsSelected) AppIcon.Artist.vector else AppIcon.ArtistOutlined.vector,
                                        contentDescription = "Artists"
                                    )
                                },
                                label = { Text("Artists") },
                                railExpanded = railExpanded
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        navDisplayContent()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    navDisplayContent()
                }
            }
            }   // closes the innerPadding Box
        }
        }   // closes ProvideBottomBarVisibilityController
    }       // closes SharedTransitionLayout
}           // closes MP3TagNavHost


@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class
)
@Composable
private fun MP3TagNavDisplay(
    topLevelBackStack: TopLevelBackStack<NavKey>,
    sharedTransitionScope: SharedTransitionScope,
    libraryViewModel: LibraryViewModel,
    libraryScanViewModel: LibraryScanViewModel,
    librarySettingsViewModel: LibrarySettingsViewModel,
    libraryBatchViewModel: LibraryBatchViewModel,
    pendingLyrics: String?,
    onPendingLyricsConsumed: () -> Unit,
    pendingCoverArt: ByteArray?,
    onPendingCoverArtConsumed: () -> Unit,
    onPendingLyricsSet: (String) -> Unit,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    NavDisplay(
        backStack = topLevelBackStack.backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            transitionInputBlocker()
        ),
        onBack = { topLevelBackStack.removeLast() },
        entryProvider = entryProvider<NavKey> {
            entry<FileBrowser>(metadata = fadePageMetadata()) {
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                FileBrowserAdaptiveScreen(
                    viewModel = libraryViewModel,
                    scanViewModel = libraryScanViewModel,
                    settingsViewModel = librarySettingsViewModel,
                    onNavigateToDirectory = { directoryUri, directoryName ->
                        topLevelBackStack.add(DirectoryContent(directoryUri, directoryName))
                    },
                    onNavigateToMetadata = { filePath, coverTag ->
                        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
                    },
                    onNavigateToOnlineMetadata = {},
                    onNavigateToOnlineLyricsSearch = {},
                    onNavigateToOnlineCoverSearch = {},
                    onNavigateToLyricsSelector = { _, _, _, _, _ -> },
                    onNavigateToSettings = { topLevelBackStack.add(Settings) },
                    onNavigateBack = {},
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<Albums>(metadata = fadePageMetadata()) {
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                AlbumAdaptiveScreen(
                    onNavigateToAlbumDetail = { albumGroup ->
                        topLevelBackStack.add(AlbumDetail(albumGroup.name, albumGroup.albumArtist ?: ""))
                    },
                    onNavigateToMetadata = { filePath, coverTag ->
                        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
                    },
                    onNavigateBack = {},
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            @OptIn(ExperimentalMaterial3Api::class)
            entry<Artists>(metadata = fadePageMetadata()) {
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                val artistViewModel: ArtistViewModel = hiltViewModel()
                var showSearchSheet by remember { mutableStateOf(false) }
                ArtistScreenContent(
                    viewModel = artistViewModel,
                    onArtistClick = { artistGroup ->
                        topLevelBackStack.add(ArtistDetail(artistGroup.name))
                    },
                    onShowSearchSheet = { showSearchSheet = true },
                    searchActive = showSearchSheet,
                    onSearchDismiss = { showSearchSheet = false },
                    onSearchFileClick = { audioFile ->
                        showSearchSheet = false
                        topLevelBackStack.add(
                            MetadataEditor(
                                audioFile.path,
                                createAlbumArtSharedElementKey(audioFile.path)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<Settings>(metadata = fadePageMetadata()) {
                SettingsEntry(topLevelBackStack)
            }

            entry<DirectoryContent>(
                clazzContentKey = { key -> "DirectoryContent_${key.directoryUri}" },
                metadata = sharedAxisXMetadata()
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                DirectoryContentAdaptiveScreen(
                    directoryUri = key.directoryUri,
                    directoryName = key.directoryName,
                    viewModel = libraryViewModel,
                    scanViewModel = libraryScanViewModel,
                    settingsViewModel = librarySettingsViewModel,
                    batchViewModel = libraryBatchViewModel,
                    onNavigateBack = { topLevelBackStack.removeLast() },
                    onNavigateToMetadata = { filePath, coverTag ->
                        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
                    },
                    onNavigateToReplayGain = { filePaths ->
                        topLevelBackStack.add(ReplayGainScanner(filePaths))
                    },
                    onNavigateToOnlineMetadata = {
                        topLevelBackStack.add(OnlineMetadata(key.directoryUri))
                    },
                    onNavigateToOnlineLyricsSearch = {
                        topLevelBackStack.add(OnlineLyricsSearch(key.directoryUri))
                    },
                    onNavigateToOnlineCoverSearch = {
                        topLevelBackStack.add(OnlineCoverSearch(key.directoryUri))
                    },
                    onNavigateToLyricsSelector = { filePath, title, artist, album, _ ->
                        topLevelBackStack.add(
                            LyricsSelector(
                                filePath = filePath,
                                title = title,
                                artist = artist,
                                album = album
                            )
                        )
                    },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<MetadataEditor>(
                clazzContentKey = { key -> "MetadataEditor_${key.filePath}" },
                metadata = containerTransformMetadata()
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                MetadataEditorEntry(
                    key = key,
                    topLevelBackStack = topLevelBackStack,
                    pendingLyrics = pendingLyrics,
                    onPendingLyricsConsumed = onPendingLyricsConsumed,
                    pendingCoverArt = pendingCoverArt,
                    onPendingCoverArtConsumed = onPendingCoverArtConsumed,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<ReplayGainScanner>(
                clazzContentKey = { key -> "ReplayGainScanner_${key.filePaths.hashCode()}" },
                metadata = sharedAxisXMetadata()
            ) { key ->
                ReplayGainScannerEntry(key, topLevelBackStack)
            }

            entry<OnlineMetadata>(
                clazzContentKey = { key -> "OnlineMetadata_${key.filePath}" },
                metadata = { key -> supportingPaneExtraMetadata(key.filePath) }
            ) { key ->
                OnlineMetadataEntry(key, topLevelBackStack)
            }

            entry<OnlineLyricsSearch>(
                clazzContentKey = { key -> "OnlineLyricsSearch_${key.filePath}" },
                metadata = { key -> supportingPaneExtraMetadata(key.filePath) }
            ) { key ->
                OnlineLyricsSearchEntry(key, topLevelBackStack, onPendingLyricsSet = onPendingLyricsSet)
            }

            entry<OnlineCoverSearch>(
                clazzContentKey = { key -> "OnlineCoverSearch_${key.filePath}" },
                metadata = { key -> supportingPaneExtraMetadata(key.filePath) }
            ) { key ->
                OnlineCoverSearchEntry(key, topLevelBackStack, onPendingCoverArtSet = onPendingCoverArtSet)
            }

            entry<LyricsSelector>(
                clazzContentKey = { key -> "LyricsSelector_${key.filePath}" },
                metadata = sharedAxisXMetadata()
            ) { key ->
                LyricsSelectorEntry(key, topLevelBackStack)
            }

            entry<LyricsPoster>(
                clazzContentKey = { key -> "LyricsPoster_${key.filePath}" },
                metadata = sharedAxisXMetadata()
            ) { key ->
                LyricsPosterEntry(key, topLevelBackStack)
            }

            entry<AlbumDetail>(
                clazzContentKey = { key -> "AlbumDetail_${key.albumName}_${key.albumArtist}" },
                metadata = containerTransformMetadata()
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                AlbumDetailEntry(
                    key = key,
                    topLevelBackStack = topLevelBackStack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<ArtistDetail>(
                clazzContentKey = { key -> "ArtistDetail_${key.artistName}" },
                metadata = containerTransformMetadata()
            ) { key ->
                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                ArtistDetailEntry(
                    key = key,
                    topLevelBackStack = topLevelBackStack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            entry<ScanDirectorySettings>(
                metadata = sharedAxisXMetadata()
            ) {
                com.voxly.presentation.screens.ScanDirectorySettingsScreen(
                    onNavigateBack = { topLevelBackStack.removeLast() }
                )
            }

            entry<SourceSettings>(
                metadata = sharedAxisXMetadata()
            ) {
                SourceSettingsScreen(
                    onNavigateBack = { topLevelBackStack.removeLast() }
                )
            }

            entry<LogViewer> {
                LogViewerScreen(
                    onBack = { topLevelBackStack.removeLast() }
                )
            }
        },
        sharedTransitionScope = sharedTransitionScope,
        sceneStrategies = listOf(
            rememberSupportingPaneSceneStrategy<NavKey>(),
            BottomSheetSceneStrategy()
        )
    )
}

@Composable
private fun SettingsEntry(topLevelBackStack: TopLevelBackStack<NavKey>) {
    SettingsScreen(
        onNavigateBack = { topLevelBackStack.removeLast() },
        onNavigateToSourceSettings = { topLevelBackStack.add(SourceSettings) },
        onNavigateToLogViewer = { topLevelBackStack.add(LogViewer) },
        onNavigateToScanDirectorySettings = { topLevelBackStack.add(ScanDirectorySettings) }
    )
}

@Composable
private fun MetadataEditorEntry(
    key: MetadataEditor,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    pendingLyrics: String?,
    onPendingLyricsConsumed: () -> Unit,
    pendingCoverArt: ByteArray?,
    onPendingCoverArtConsumed: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val viewModel = hiltViewModel<MetadataEditorViewModel, MetadataEditorViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    MetadataEditorScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        coverTag = key.coverTag.takeIf { it.isNotEmpty() },
        sharedElementKey = key.coverTag.takeIf { it.isNotEmpty() },
        onNavigateBack = { topLevelBackStack.removeLast() },
        onNavigateToOnlineMetadata = {
            topLevelBackStack.add(OnlineMetadata(key.filePath))
        },
        onNavigateToOnlineLyricsSearch = {
            topLevelBackStack.add(OnlineLyricsSearch(key.filePath))
        },
        onNavigateToOnlineCoverSearch = {
            topLevelBackStack.add(OnlineCoverSearch(key.filePath))
        },
        onNavigateToLyricsSelector = { _, title, artist, album, _ ->
            topLevelBackStack.add(
                LyricsSelector(
                    filePath = key.filePath,
                    title = title,
                    artist = artist,
                    album = album
                )
            )
        },
        pendingOnlineLyrics = pendingLyrics,
        onConsumePendingOnlineLyrics = onPendingLyricsConsumed,
        pendingOnlineCoverArt = pendingCoverArt,
        onConsumePendingOnlineCoverArt = onPendingCoverArtConsumed,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Composable
private fun ReplayGainScannerEntry(key: ReplayGainScanner, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<ReplayGainViewModel, ReplayGainViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    ReplayGainScannerScreen(
        filePaths = key.filePaths,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onNavigateToMetadata = { filePath, coverTag ->
            topLevelBackStack.removeLast()
            topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
        }
    )
}

@Composable
private fun OnlineMetadataEntry(key: OnlineMetadata, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<OnlineMetadataViewModel, OnlineMetadataViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineMetadataScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onApplyMetadata = { metadata ->
            Timber.d("MP3TagNavHost: onApplyMetadata called, title=${metadata.title}")
            topLevelBackStack.removeLast()
            Timber.d("MP3TagNavHost: backStack popped")
        }
    )
}

@Composable
private fun OnlineLyricsSearchEntry(
    key: OnlineLyricsSearch,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    onPendingLyricsSet: (String) -> Unit
) {
    val viewModel = hiltViewModel<OnlineLyricsSearchViewModel, OnlineLyricsSearchViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineLyricsSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onLyricsSelected = { lyricsText ->
            onPendingLyricsSet(lyricsText)
            topLevelBackStack.removeLast()
        }
    )
}

@Composable
private fun OnlineCoverSearchEntry(
    key: OnlineCoverSearch,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    onPendingCoverArtSet: (ByteArray) -> Unit
) {
    val viewModel = hiltViewModel<OnlineCoverSearchViewModel, OnlineCoverSearchViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) }
    )
    OnlineCoverSearchScreen(
        filePath = key.filePath,
        viewModel = viewModel,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onCoverSelected = { coverBytes ->
            onPendingCoverArtSet(coverBytes)
            topLevelBackStack.removeLast()
        }
    )
}

@Composable
private fun LyricsSelectorEntry(key: LyricsSelector, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<LyricsSelectorViewModel, LyricsSelectorViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    LyricsSelectorScreen(
        title = key.title,
        artist = key.artist,
        album = key.album,
        onNavigateBack = { topLevelBackStack.removeLast() },
        onDismiss = { topLevelBackStack.removeLast() },
        onNavigateToLyricsPoster = { lyricsText, selectedIndices ->
            topLevelBackStack.add(
                LyricsPoster(
                    filePath = key.filePath,
                    title = key.title,
                    artist = key.artist,
                    album = key.album,
                    lyricsText = lyricsText,
                    selectedLyricsIndices = selectedIndices
                )
            )
        },
        viewModel = viewModel
    )
}

@Composable
private fun LyricsPosterEntry(key: LyricsPoster, topLevelBackStack: TopLevelBackStack<NavKey>) {
    val viewModel = hiltViewModel<LyricsPosterViewModel, LyricsPosterViewModel.Factory>(
        key = key.filePath,
        creationCallback = { factory -> factory.create(key) }
    )
    LyricsPosterScreen(
        filePath = key.filePath,
        title = key.title,
        artist = key.artist,
        album = key.album,
        lyricsText = key.lyricsText,
        selectedLyricsIndices = key.selectedLyricsIndices,
        onNavigateBack = { topLevelBackStack.removeLast() },
        viewModel = viewModel
    )
}

@Composable
private fun AlbumDetailEntry(
    key: AlbumDetail,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    Timber.d("AlbumDetailEntry: loading $key")
    val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory>(
        key = "${key.albumName}_${key.albumArtist}",
        creationCallback = { factory -> factory.create(key) }
    )
    val albumOnNavigateBack = remember(topLevelBackStack) { { topLevelBackStack.removeLast(); Unit } }
    val albumOnNavigateToMetadata = remember(topLevelBackStack) { { filePath: String, coverTag: String? ->
        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
    } }
    AlbumDetailScreen(
        albumName = key.albumName,
        albumArtist = key.albumArtist.takeIf { it.isNotEmpty() },
        onNavigateBack = albumOnNavigateBack,
        onNavigateToMetadata = albumOnNavigateToMetadata,
        viewModel = viewModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

@Composable
private fun ArtistDetailEntry(
    key: ArtistDetail,
    topLevelBackStack: TopLevelBackStack<NavKey>,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val viewModel = hiltViewModel<ArtistDetailViewModel, ArtistDetailViewModel.Factory>(
        key = key.artistName,
        creationCallback = { factory -> factory.create(key) }
    )
    val artistOnNavigateBack = remember(topLevelBackStack) { { topLevelBackStack.removeLast(); Unit } }
    val artistOnNavigateToMetadata = remember(topLevelBackStack) { { filePath: String, coverTag: String? ->
        topLevelBackStack.add(MetadataEditor(filePath, coverTag ?: ""))
    } }
    val artistOnNavigateToAlbumDetail = remember(topLevelBackStack) { { albumName: String, albumArtist: String? ->
        topLevelBackStack.add(AlbumDetail(albumName, albumArtist ?: ""))
    } }
    ArtistDetailScreen(
        artistName = key.artistName,
        onNavigateBack = artistOnNavigateBack,
        onNavigateToMetadata = artistOnNavigateToMetadata,
        onNavigateToAlbumDetail = artistOnNavigateToAlbumDetail,
        viewModel = viewModel,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

/**
 * Marks a NavEntry as the "extra pane" of a [SupportingPaneSceneStrategy] scene, paired with
 * the previous [MetadataEditor] entry on the back stack. Same `sceneKey` ⇒ same scene group;
 * we use the audio file path because only one Online* search is open per file at a time.
 */
// Settings is a pushed page (pushed via onNavigateToSettings), not a tab — its own TopAppBar
// back arrow handles navigation back, so it gets full-screen space like the detail screens.
private fun isMainScreenKey(key: Any?): Boolean =
    key != Settings && key in BottomNavItem.entries.map { it.key }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun supportingPaneExtraMetadata(sceneKey: Any): Map<String, Any> =
    ListDetailSceneStrategy.extraPane(sceneKey)
