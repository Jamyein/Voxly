# Album/Artist Screen Refresh Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix continuous refresh bug in Album/Artist screens by unifying scan entry point through LibraryViewModel and removing repeatOnLifecycle abuse.

**Architecture:** Rename FileBrowserViewModel to LibraryViewModel, expose scanState/refresh, update AlbumViewModel/ArtistViewModel to delegate to shared LibraryViewModel. Album/Artist screens observe scanState instead of triggering refresh via repeatOnLifecycle.

**Tech Stack:** Kotlin, Hilt DI, Jetpack Navigation3, Jetpack Compose, StateFlow

---

## Chunk 1: Rename FileBrowserViewModel → LibraryViewModel

**Files:**
- Rename: `app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt` → `LibraryViewModel.kt`

**Steps:**

- [ ] **Step 1: Rename file FileBrowserViewModel.kt to LibraryViewModel.kt**

Run: `mv app/src/main/java/com/voxly/presentation/viewmodel/FileBrowserViewModel.kt app/src/main/java/com/voxly/presentation/viewmodel/LibraryViewModel.kt`

- [ ] **Step 2: Update class name inside file**

Modify line 55: `class FileBrowserViewModel` → `class LibraryViewModel`

- [ ] **Step 3: Update all imports in MP3TagNavHost.kt**

File: `app/src/main/java/com/voxly/presentation/navigation/MP3TagNavHost.kt`

Change line 49:
```kotlin
import com.voxly.presentation.viewmodel.FileBrowserViewModel
```
→
```kotlin
import com.voxly.presentation.viewmodel.LibraryViewModel
```

Change line 66:
```kotlin
val fileBrowserViewModel: FileBrowserViewModel = hiltViewModel()
```
→
```kotlin
val libraryViewModel: LibraryViewModel = hiltViewModel()
```

Change all references in the file from `fileBrowserViewModel` to `libraryViewModel`:
- Line 121: `viewModel = fileBrowserViewModel,` → `viewModel = libraryViewModel,`
- Line 211: `viewModel = fileBrowserViewModel,` → `viewModel = libraryViewModel,`

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation with no errors related to rename

---

## Chunk 2: Expose scanState and refresh() in LibraryViewModel

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/LibraryViewModel.kt`

**Current state analysis:**
- `unifiedScanManager.scanState` already exists but is only collected internally
- `refresh(forceRefresh: Boolean = false, isIncremental: Boolean = false)` already exists as `loadAudioFiles()`

**Steps:**

- [ ] **Step 1: Verify scanState is accessible**

Read lines 55-75 of LibraryViewModel.kt to confirm `unifiedScanManager` is injected and `scanState` is available.

- [ ] **Step 2: Expose scanState as public property**

Add after line ~70 (after `isRefreshing` property):
```kotlin
/**
 * Unified scan state exposed for all screens to observe loading/progress.
 */
val scanState: StateFlow<ScanState> = unifiedScanManager.scanState
```

- [ ] **Step 3: Add refresh() wrapper that delegates to loadAudioFiles()**

Add method after `loadAudioFiles()` definition (~line 290):
```kotlin
/**
 * Unified refresh entry point for all screens.
 * forceRefresh=true: full rescan
 * forceRefresh=false: incremental scan (new/modified files only)
 */
fun refresh(forceRefresh: Boolean = false) {
    loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
}
```

Note: `loadAudioFiles()` signature is `loadAudioFiles(forceRefresh: Boolean = false, isIncremental: Boolean = false)`. For pull-to-refresh (incremental), call `refresh(forceRefresh = false)`. For full refresh, call `refresh(forceRefresh = true)`.

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation

---

## Chunk 3: Update AlbumViewModel to delegate to LibraryViewModel

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/AlbumViewModel.kt`

**Current state:**
```kotlin
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {
    val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = true)
            } catch (e: Exception) {
                Timber.e(e, "Album refresh failed")
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
```

**Steps:**

- [ ] **Step 1: Rewrite AlbumViewModel to delegate to LibraryViewModel**

Replace entire file content with:
```kotlin
package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.usecase.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin ViewModel layer for AlbumScreen.
 * Delegates all data and scan coordination to shared LibraryViewModel.
 */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val libraryViewModel: LibraryViewModel
) : ViewModel() {

    // Delegated to shared LibraryViewModel
    val albums: StateFlow<List<AlbumGroup>> = libraryViewModel.albums

    // Unified scan state for all screens
    val scanState: StateFlow<ScanState> = libraryViewModel.scanState

    // Refresh state derived from LibraryViewModel
    val isRefreshing: StateFlow<Boolean> = libraryViewModel.isRefreshing

    // Unified refresh entry point
    fun refresh(forceRefresh: Boolean = false) {
        libraryViewModel.refresh(forceRefresh)
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation. If error "LibraryViewModel is abstract" - need to check LibraryViewModel class signature.

---

## Chunk 4: Update ArtistViewModel to delegate to LibraryViewModel

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/ArtistViewModel.kt`

**Steps:**

- [ ] **Step 1: Rewrite ArtistViewModel to delegate to LibraryViewModel**

Replace entire file content with:
```kotlin
package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.usecase.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin ViewModel layer for ArtistScreen.
 * Delegates all data and scan coordination to shared LibraryViewModel.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val libraryViewModel: LibraryViewModel
) : ViewModel() {

    // Delegated to shared LibraryViewModel
    val artists: StateFlow<List<ArtistGroup>> = libraryViewModel.artists

    // Unified scan state for all screens
    val scanState: StateFlow<ScanState> = libraryViewModel.scanState

    // Refresh state derived from LibraryViewModel
    val isRefreshing: StateFlow<Boolean> = libraryViewModel.isRefreshing

    // Unified refresh entry point
    fun refresh(forceRefresh: Boolean = false) {
        libraryViewModel.refresh(forceRefresh)
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation

---

## Chunk 5: Modify AlbumScreen - Remove repeatOnLifecycle

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/album/AlbumScreen.kt`

**Current problematic code (lines 70-85):**
```kotlin
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

**Steps:**

- [ ] **Step 1: Replace permission+refresh LaunchedEffect with passive permission check**

Replace lines 70-85 with:
```kotlin
// Passive permission check - no active refresh trigger
// Data is collected from shared LibraryViewModel via albums StateFlow
LaunchedEffect(hasReadPermission) {
    if (!hasReadPermission) {
        readPermissionLauncher.launch(readPermission)
    }
}
```

- [ ] **Step 2: Add scanState observation for loading indicator**

Add after line ~25 (after imports):
```kotlin
import com.voxly.domain.usecase.ScanState
```

Add inside composable function, after `val albums by viewModel.albums.collectAsState()` (~line 48):
```kotlin
val scanState by viewModel.scanState.collectAsState()
```

- [ ] **Step 3: Update AlbumTabContent call to use scanState for isRefreshing**

Find the `AlbumTabContent` call and change:
```kotlin
isRefreshing = isRefreshing,
```
→
```kotlin
isRefreshing = scanState is ScanState.Scanning,
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation

---

## Chunk 6: Modify ArtistScreen - Remove repeatOnLifecycle

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/artist/ArtistScreen.kt`

**Current problematic code (lines 71-86):**
```kotlin
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

**Steps:**

- [ ] **Step 1: Replace permission+refresh LaunchedEffect with passive permission check**

Replace lines 71-86 with:
```kotlin
// Passive permission check - no active refresh trigger
// Data is collected from shared LibraryViewModel via artists StateFlow
LaunchedEffect(hasReadPermission) {
    if (!hasReadPermission) {
        readPermissionLauncher.launch(readPermission)
    }
}
```

- [ ] **Step 2: Add scanState observation for loading indicator**

Add after line ~27 (after imports):
```kotlin
import com.voxly.domain.usecase.ScanState
```

Add inside composable function, after `val artists by viewModel.artists.collectAsState()` (~line 49):
```kotlin
val scanState by viewModel.scanState.collectAsState()
```

- [ ] **Step 3: Update ArtistTabContent call to use scanState for isRefreshing**

Find the `ArtistTabContent` call and change:
```kotlin
isRefreshing = isRefreshing,
```
→
```kotlin
isRefreshing = scanState is ScanState.Scanning,
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation

---

## Chunk 7: Update Navigation to pass shared LibraryViewModel to Album/Artist screens

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/navigation/MP3TagNavHost.kt`

**Current code (lines 150-166):**
```kotlin
entry<Albums> {
    AlbumScreen(
        outerPadding = outerPadding,
        onNavigateToAlbumDetail = { albumName, albumArtist ->
            backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
        }
    )
}

entry<Artists> {
    ArtistScreen(
        outerPadding = outerPadding,
        onNavigateToArtistDetail = { artistName ->
            backStack.add(ArtistDetail(artistName))
        }
    )
}
```

**Steps:**

- [ ] **Step 1: Update AlbumScreen navigation entry to pass LibraryViewModel**

Replace the `entry<Albums>` block with:
```kotlin
entry<Albums> {
    val albumViewModel: AlbumViewModel = hiltViewModel(
        viewModelStoreOwner = navController.getViewModelStoreOwner(navController.currentBackStackEntry!!)
    )
    AlbumScreen(
        outerPadding = outerPadding,
        viewModel = albumViewModel,
        onNavigateToAlbumDetail = { albumName, albumArtist ->
            backStack.add(AlbumDetail(albumName, albumArtist ?: ""))
        }
    )
}
```

Note: Since AlbumViewModel now internally uses LibraryViewModel (injected as singleton), creating a new AlbumViewModel per nav entry will use the same LibraryViewModel instance.

- [ ] **Step 2: Update ArtistScreen navigation entry to pass LibraryViewModel**

Replace the `entry<Artists>` block with:
```kotlin
entry<Artists> {
    val artistViewModel: ArtistViewModel = hiltViewModel(
        viewModelStoreOwner = navController.getViewModelStoreOwner(navController.currentBackStackEntry!!)
    )
    ArtistScreen(
        outerPadding = outerPadding,
        viewModel = artistViewModel,
        onNavigateToArtistDetail = { artistName ->
            backStack.add(ArtistDetail(artistName))
        }
    )
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -30`

Expected: Successful compilation

---

## Chunk 8: Final verification and commit

**Steps:**

- [ ] **Step 1: Full clean build**

Run: `./gradlew :app:clean :app:assembleDebug 2>&1 | tail -50`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Review changed files**

Run: `git diff --stat HEAD`

Expected: Modified files match design doc:
- FileBrowserViewModel.kt → LibraryViewModel.kt (renamed)
- AlbumViewModel.kt
- ArtistViewModel.kt
- AlbumScreen.kt
- ArtistScreen.kt
- MP3TagNavHost.kt

- [ ] **Step 3: Commit all changes**

Run:
```bash
git add -A
git commit -m "$(cat <<'EOF'
fix(refresh): unify scan entry point and remove repeatOnLifecycle abuse

- Rename FileBrowserViewModel to LibraryViewModel for clarity
- Expose scanState and refresh() for unified scan coordination
- AlbumViewModel/ArtistViewModel now delegate to shared LibraryViewModel
- AlbumScreen/ArtistScreen remove repeatOnLifecycle refresh triggers
- All screens now observe scanState instead of triggering own scans
- Pull-to-refresh uses unified refresh() entry point

Fixes continuous refresh spinner on Album/Artist screens caused by
repeatOnLifecycle executing refresh() on every RESUMED event.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

Expected: Commit created successfully

---

## Verification Checklist

After implementation, verify:

1. [ ] AlbumScreen shows album list without continuous refresh spinner
2. [ ] ArtistScreen shows artist list without continuous refresh spinner
3. [ ] Pull-to-refresh on Album triggers unified scan
4. [ ] Pull-to-refresh on Artist triggers unified scan
5. [ ] Scan results update all screens simultaneously via shared StateFlow
6. [ ] Navigation between screens preserves data state
7. [ ] No repeatOnLifecycle causing repeated refresh triggers
