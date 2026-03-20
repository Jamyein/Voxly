# Album/Artist Screen Refresh Architecture Refactor

## Status

Approved for implementation.

## Problem Statement

AlbumScreen and ArtistScreen use `repeatOnLifecycle` combined with `viewModel.refresh()`:

```kotlin
// AlbumScreen.kt (buggy)
LaunchedEffect(hasReadPermission, lifecycleOwner) {
    if (!hasReadPermission) {
        readPermissionLauncher.launch(readPermission)
        return@LaunchedEffect
    }
    viewModel.refresh()
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.refresh()
    }
}
```

This causes:
1. **Continuous re-triggering**: `repeatOnLifecycle` executes `refresh()` on EVERY RESUMED event, not just once
2. **Stuck refresh state**: The `if (isRefreshing) return` guard silently drops subsequent calls while a scan is in progress, but `repeatOnLifecycle` keeps executing
3. **No unified coordination**: Album/Artist each scan independently from FileBrowser

## Design Decision

### Unified Scanning Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  UnifiedScanManager (Singleton)                │
│              scanState: StateFlow<ScanState>                  │
│                  scan(target, force)                          │
└──────────────────────────┬───────────────────────────────────┘
                           │ 统一扫描入口
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ FileBrowser     │ │ AlbumScreen     │ │ ArtistScreen    │
│ Screen          │ │                 │ │                 │
│                 │ │                 │ │                 │
│ files StateFlow │ │ albums StateFlow│ │ artists StateFlow│
│                 │ │                 │ │                 │
│ 下拉刷新 ✓      │ │ 下拉刷新 ✓      │ │ 下拉刷新 ✓      │
│ refresh()       │ │ refresh()       │ │ refresh()       │
└─────────────────┘ └─────────────────┘ └─────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │  LibraryViewModel      │
              │   (Shared, @Singleton)   │
              │                         │
              │  - albums               │
              │  - artists              │
              │  - allAudios            │
              │  - scanState            │
              │  - isRefreshing         │
              │  - refresh()            │
              └─────────────────────────┘
```

### Key Principles

1. **Single scan entry point**: `LibraryViewModel.refresh()` is the ONLY place that triggers scanning
2. **Shared ViewModel**: FileBrowser, Album, Artist screens share the same `LibraryViewModel` instance
3. **Passive screens**: Album/Artist screens do NOT trigger scans; they only collect data and observe scan state
4. **Unified refresh state**: All screens observe `scanState` to show loading/progress

## Implementation Details

### 1. Rename FileBrowserViewModel → LibraryViewModel

Rename `FileBrowserViewModel.kt` to `LibraryViewModel.kt`. Keep existing functionality.

### 2. Expose scanState and refresh() in LibraryViewModel

The `scanState` from `UnifiedScanManager` and `refresh()` method must be accessible to all screens:

```kotlin
@HiltViewModel
class LibraryViewModel @Inject constructor(
    ...
) : ViewModel() {

    // Expose scan state for all screens
    val scanState: StateFlow<ScanState> = unifiedScanManager.scanState

    // Expose isRefreshing for pull-to-refresh
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Unified refresh entry point
    fun refresh(forceRefresh: Boolean = false) {
        // forceRefresh=true: full scan
        // forceRefresh=false: incremental scan
    }
}
```

### 3. Update AlbumViewModel / ArtistViewModel

Keep as thin transformation layers, inject `LibraryViewModel`:

```kotlin
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val libraryViewModel: LibraryViewModel  // Shared instance
) : ViewModel() {

    // Delegate to shared data
    val albums: StateFlow<List<AlbumGroup>> = libraryViewModel.albums
    val isRefreshing: StateFlow<Boolean> = libraryViewModel.isRefreshing

    // Thin refresh wrapper
    fun refresh(forceRefresh: Boolean = false) {
        libraryViewModel.refresh(forceRefresh)
    }
}
```

### 4. Modify AlbumScreen

**BEFORE (buggy):**
```kotlin
LaunchedEffect(hasReadPermission, lifecycleOwner) {
    if (!hasReadPermission) {
        readPermissionLauncher.launch(readPermission)
        return@LaunchedEffect
    }
    viewModel.refresh()
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.refresh()  // PROBLEM: triggers on EVERY resume
    }
}
```

**AFTER (fixed):**
```kotlin
// Collect data passively - no active refresh trigger
LaunchedEffect(hasReadPermission) {
    if (!hasReadPermission) {
        readPermissionLauncher.launch(readPermission)
    }
}

// Observe scan state for loading indicator
val scanState by viewModel.scanState.collectAsState()

// Pull-to-refresh uses unified entry point
AlbumTabContent(
    albums = albums,
    isRefreshing = scanState is ScanState.Scanning,
    onRefresh = { viewModel.refresh() },
    ...
)
```

### 5. Modify ArtistScreen

Same pattern as AlbumScreen.

### 6. Update Navigation

Three screens share the same `LibraryViewModel` instance via navigation's `viewModel()` with shared scope:

```kotlin
NavHost(
    navController = navController,
    startDestination = "file_browser"
) {
    composable("file_browser") {
        val viewModel: LibraryViewModel = hiltViewModel()
        FileBrowserScreen(viewModel = viewModel)
    }

    composable("albums") {
        val viewModel: LibraryViewModel = hiltViewModel(
            viewModelStoreOwner = navController.getViewModelStoreOwner("file_browser")
        )
        AlbumScreen(viewModel = viewModel)
    }

    composable("artists") {
        val viewModel: LibraryViewModel = hiltViewModel(
            viewModelStoreOwner = navController.getViewModelStoreOwner("file_browser")
        )
        ArtistScreen(viewModel = viewModel)
    }
}
```

Alternatively, use a module-level `@Provides` to bind `LibraryViewModel` as a singleton that all screens inject directly.

### 7. Remove repeatOnLifecycle from Album/Artist Screens

The `repeatOnLifecycle` block that triggered `refresh()` on every RESUMED state is completely removed.

## Files to Change

| File | Change Type | Description |
|------|-------------|-------------|
| `FileBrowserViewModel.kt` | Rename | → `LibraryViewModel.kt` |
| `AlbumViewModel.kt` | Modify | Inject shared `LibraryViewModel`, remove duplicate data/logic |
| `ArtistViewModel.kt` | Modify | Inject shared `LibraryViewModel`, remove duplicate data/logic |
| `AlbumScreen.kt` | Modify | Remove `repeatOnLifecycle`, use shared data/state |
| `ArtistScreen.kt` | Modify | Remove `repeatOnLifecycle`, use shared data/state |
| `FileBrowserScreen.kt` | Modify | Adapt to renamed `LibraryViewModel` |
| Navigation graph | Modify | Share `LibraryViewModel` instance across screens |

## Files to Delete

| File | Reason |
|------|--------|
| None | AlbumViewModel/ArtistViewModel retained as thin layers |

## Behavioral Changes

| Scenario | Before | After |
|---------|--------|-------|
| Enter Album page first | Triggers scan via refresh() | Shows empty until FileBrowser scans (or show cached) |
| Enter FileBrowser first, then Album | Album shows stale data | Album shows same fresh data as FileBrowser |
| Pull-to-refresh on Album | Triggers its own scan | Delegates to LibraryViewModel.refresh() |
| Pull-to-refresh on Artist | Triggers its own scan | Delegates to LibraryViewModel.refresh() |
| FileBrowser scan completes | Album/Artist don't know | All screens update via shared StateFlow |
| Return to Album from detail | repeatOnLifecycle re-triggers | No re-trigger; data already fresh |

## Risks & Mitigations

1. **Risk**: Album/Artist pages show empty initially if FileBrowser hasn't scanned yet.
   **Mitigation**: `AudioFileScanner` has persistent Room cache. First collect gets cached data immediately.

2. **Risk**: Need to ensure `LibraryViewModel` is properly scoped as singleton.
   **Mitigation**: Use Hilt `@Singleton` annotation and verify DI module binding.

3. **Risk**: Breaking existing navigation flow.
   **Mitigation**: Test each navigation path: FileBrowser → Album → Artist → AlbumDetail → ArtistDetail.

## Success Criteria

1. [ ] AlbumScreen shows album list without continuous refresh spinner
2. [ ] ArtistScreen shows artist list without continuous refresh spinner
3. [ ] Pull-to-refresh on any screen triggers one unified scan
4. [ ] Scan results update all three screens simultaneously
5. [ ] No `repeatOnLifecycle` causing repeated refresh triggers
6. [ ] Navigation between screens preserves data state
