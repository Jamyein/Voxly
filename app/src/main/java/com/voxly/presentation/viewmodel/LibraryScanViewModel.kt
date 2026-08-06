package com.voxly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.SortUtil
import com.voxly.domain.repository.LibraryChangeEvent
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.UiStateDataStore
import com.voxly.data.local.DirFileSortOption
import com.voxly.data.local.FileSortOption
import com.voxly.data.local.toFileSortOption
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
import com.voxly.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * ViewModel for library scanning, paging, and SAF directory management.
 */
@HiltViewModel
class LibraryScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: com.voxly.data.local.AudioFileScanner,
    private val musicLibraryCache: com.voxly.data.local.MusicLibraryCache,
    private val mediaStoreVersionCache: com.voxly.data.local.MediaStoreVersionCache,
    private val settingsDataStore: SettingsDataStore,
    private val uiStateDataStore: UiStateDataStore,
    private val libraryRepository: LibraryRepository,
    private val safWriteAccessService: SafWriteAccessService,
    private val audioRepository: AudioRepository,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryScanViewModel"
        private const val STATE_FLOW_TIMEOUT_MS = 5000L
        private const val RESUME_REFRESH_THROTTLE_MS = 3_000L
        private const val EVENT_MERGE_WINDOW_MS = 400L
    }

    // allAudios reads the shared filtered library (filteredAllAudios) so the
    // Files page respects whitelist/blacklist/min-duration settings exactly like
    // Albums/Artists. Files without album/artist metadata remain visible here —
    // they are only grouped out of the album-artist aggregator.
    val allAudios: StateFlow<List<AudioFile>> = audioFileScanner.filteredAllAudios

    /** True once the library's initial build finished (data rendered or empty). */
    val libraryInitialized: StateFlow<Boolean> = audioFileScanner.libraryInitialized

    /**
     * Unified library search — the ONLY search path in the app (Files tab,
     * directory contents, Albums, Artists, Home all go through this).
     *
     * Filters [allAudios] — the same filtered library flow the UI renders — so
     * search always sees exactly what the user sees (whitelist/blacklist/
     * min-duration settings respected by construction, no SQL duplication).
     * Matching is a case-insensitive substring check over filename, title,
     * artist and album; works for every script (CJK included) and every
     * substring position. Filtering runs on [Dispatchers.Default], never on the
     * main thread; the sheet debounces keystrokes by 250ms before calling.
     *
     * History: this used to be a Room FTS4 `MATCH` query, which silently
     * returned nothing for non-ASCII metadata (the default `simple` tokenizer
     * only emits ASCII tokens) and ignored the library filters — see lesson #36.
     */
    fun searchFiles(query: String): Flow<List<AudioFile>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return flowOf(emptyList())
        return allAudios.map { files ->
            files.filter { it.matchesLibrarySearch(q) }
        }.flowOn(Dispatchers.Default)
    }

    val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val hasWhitelistDirectories: StateFlow<Boolean> = settingsDataStore.selectedDirectoryUris
        .map { uris -> uris.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    val currentFileSortOption: StateFlow<FileSortOption> = uiStateDataStore.fileBrowserSortOption
        .map { toFileSortOption(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = FileSortOption.NAME_ASC
        )

    val currentDirectorySortOption: StateFlow<DirFileSortOption> = uiStateDataStore.directoryFileSortOption
        .map { it.toDirFileSortOption() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = DirFileSortOption.NAME_ASC
        )

    // Sorted by the scanner's app-scope Eagerly projection (hot before
    // navigation — same unified pattern as sortedAlbums/artistListItems).
    val sortedAllAudios: StateFlow<List<AudioFile>> = audioFileScanner.sortedAllAudios

    private val _selectedDirectories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val selectedDirectories: StateFlow<List<SelectedDirectory>> = _selectedDirectories.asStateFlow()

    // Directory-scoped files derived from the filtered library flow, grouped by
    // selected directory. Stays in sync automatically whenever the filtered
    // library (allAudios) or the selection changes; no manual bookkeeping on
    // scan. Every selected dir appears (possibly empty list).
    val directoryFiles: StateFlow<Map<String, List<AudioFile>>> = combine(
        allAudios,
        selectedDirectories
    ) { audios, dirs ->
        groupFilesBySelectedDirectory(audios, dirs)
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = emptyMap()
    )

    val sortedDirectoryFiles: StateFlow<Map<String, List<AudioFile>>> = combine(
        directoryFiles,
        currentDirectorySortOption
    ) { filesByDirectory, sortOption ->
        filesByDirectory to sortOption
    }.map { (filesByDirectory, sortOption) ->
        filesByDirectory.mapValues { (_, files) ->
            sortAudioFiles(files, sortOption)
        }
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyMap()
        )

    private val _directoryLoadingState = MutableStateFlow<Set<String>>(emptySet())
    val directoryLoadingState: StateFlow<Set<String>> = _directoryLoadingState.asStateFlow()

    private val _openedDirectoryUri = MutableStateFlow<String?>(null)
    val openedDirectoryUri: StateFlow<String?> = _openedDirectoryUri.asStateFlow()

    private val _isInitialLoad = MutableStateFlow(true)

    /**
     * `isRefreshing` proxies [LibraryRepository.isRefreshing] — the global
     * scan-activity refcount (1+ while any scan is in flight). Each scan
     * lifetime here pairs [LibraryDataHolder.beginScan] with
     * [LibraryDataHolder.endScan] in a finally block; concurrent scans
     * correctly accumulate. External consumers (Files page, DirectoryContent,
     * MainActivity) observe this StateFlow directly.
     */
    val isRefreshing: StateFlow<Boolean> = libraryRepository.isRefreshing

    data class FileBrowserUiState(
        val allAudios: List<AudioFile> = emptyList(),
        val selectedDirectories: List<SelectedDirectory> = emptyList(),
        val directoryFiles: Map<String, List<AudioFile>> = emptyMap(),
        val isRefreshing: Boolean = false,
        val hasWhitelistDirectories: Boolean = false,
        val isInitialLoad: Boolean = true
    )

    val fileBrowserUiState: StateFlow<FileBrowserUiState> = combine(
        allAudios,
        selectedDirectories,
        directoryFiles,
        isRefreshing,
        hasWhitelistDirectories,
        _isInitialLoad
    ) { values: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        FileBrowserUiState(
            allAudios = values[0] as List<AudioFile>,
            selectedDirectories = values[1] as List<SelectedDirectory>,
            directoryFiles = values[2] as Map<String, List<AudioFile>>,
            isRefreshing = values[3] as Boolean,
            hasWhitelistDirectories = values[4] as Boolean,
            isInitialLoad = values[5] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = FileBrowserUiState()
    )

    private var scanJob: Job? = null

    /**
     * Timestamp of the last [refreshOnResume] dispatch. Throttles ON_RESUME
     * refresh requests so rapid resume/pause cycles (notification shade,
     * permission dialogs, system dialogs) don't spam scans. A meaningful
     * backgrounding (returning from the system file manager, switching apps)
     * exceeds [RESUME_REFRESH_THROTTLE_MS] and triggers a real scan.
     */
    private var lastResumeRefreshAt = 0L

    /**
     * True until the first ON_RESUME fires. The first resume happens during
     * onCreate, while the cold-start SnapshotCheck (checkDirectorySnapshotsOnStart)
     * already requests the identical INCREMENTAL scan — emitting a second one
     * only delays the merge flush. Skipping it leaves the SnapshotCheck as the
     * single cold-start trigger.
     */
    private var isFirstResume = true

    // Separate Job coordinator for directory-scoped scans. This guards every
    // entry point that calls scanSelectedDirectories() so concurrent
    // checkDirectorySnapshotsOnStart, refreshDirectoryIncremental, and addDirectory
    // calls cannot race.
    private var directoryScanJob: Job? = null

    // ─── Unified change-event bus merge state ───────────────────────────
    // Incoming LibraryChangeEvents are coalesced here before a scan runs:
    // a pending Global supersedes all pending Directory events (it covers
    // them), and Directory events for different URIs merge into one scan.
    private var pendingGlobal: LibraryChangeEvent.Global? = null
    private val pendingDirectories = LinkedHashMap<String, LibraryChangeEvent.Directory>()
    private var flushJob: Job? = null

    /**
     * Merges a [LibraryChangeEvent] into the pending state.
     *  - [LibraryChangeEvent.Directory] coalesces per directory URI.
     *  - [LibraryChangeEvent.Global] supersede all pending directory work and
     *    schedule one merged scan. Merge rule: the strongest pending strategy
     *    wins — a later LAZY request (e.g. ON_RESUME after the folder picker
     *    closes) must NOT downgrade a pending INCREMENTAL/FORCE scan, or the
     *    whitelist-add scan gets replaced by a LAZY no-op and never runs.
     *  - [LibraryChangeEvent.SnapshotCheck] schedules the cold-start incremental
     *    scan (see [checkDirectorySnapshotsOnStart]).
     */
    private fun handleChangeEvent(event: LibraryChangeEvent) {
        when (event) {
            is LibraryChangeEvent.Directory -> {
                if (pendingGlobal != null) return
                pendingDirectories[event.directoryUri] = event
                scheduleFlush()
            }
            is LibraryChangeEvent.Global -> {
                val existing = pendingGlobal
                // Strategy strengths by enum ordinal: LAZY(0) < INCREMENTAL(1) < FORCE(2).
                if (existing == null || event.strategy.ordinal >= existing.strategy.ordinal) {
                    pendingGlobal = event
                    pendingDirectories.clear()
                    scheduleFlush()
                }
            }
            is LibraryChangeEvent.SnapshotCheck -> {
                if (pendingGlobal == null) {
                    // Cold-start snapshot check. With cached data the aggregator
                    // already rendered the library, but this incremental scan is
                    // the guaranteed purge point for external deletions: SAF
                    // trees have no change notification and the MediaStore version
                    // never changes for SAF-only deletions (lesson #14).
                    // INCREMENTAL is a diff scan, NOT a full rescan — unchanged
                    // files are retained from the cache, only changed files are
                    // re-read. loadAudioFiles releases the skeleton as soon as
                    // cache is confirmed, so this does not flash the loading state.
                    pendingGlobal = LibraryChangeEvent.Global(RefreshStrategy.INCREMENTAL)
                }
                scheduleFlush()
            }
        }
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = viewModelScope.launch {
            delay(EVENT_MERGE_WINDOW_MS)
            flushPendingEvents()
        }
    }

    private suspend fun flushPendingEvents() {
        val global = pendingGlobal
        pendingGlobal = null
        if (global != null) {
            loadAudioFiles(global.strategy)
            return
        }

        val dirs = pendingDirectories.values.toList()
        pendingDirectories.clear()
        if (dirs.isEmpty()) return

        val uris = dirs.map { it.directoryUri }.toSet()
        val selected = _selectedDirectories.value.filter { it.uri in uris }
        if (selected.isEmpty()) return

        launchDirectoryScan(
            directories = selected,
            isIncremental = true,
            forceRefresh = dirs.any { it.forceRefresh }
        )
    }

    /**
     * Runs a directory-scoped scan with proper cancellation of any prior run.
     * All call sites of [scanSelectedDirectories] should funnel through here.
     */
    private fun launchDirectoryScan(
        directories: List<SelectedDirectory>,
        isIncremental: Boolean,
        forceRefresh: Boolean
    ) {
        directoryScanJob?.cancel()
        directoryScanJob = viewModelScope.launch {
            libraryDataHolder.beginScan()
            try {
                scanSelectedDirectories(directories, isIncremental, forceRefresh)
            } finally {
                libraryDataHolder.endScan()
            }
        }
    }

    init {
        // Cache confirmed → release the initial-load skeleton IMMEDIATELY, not
        // when loadAudioFiles finally runs. The scan pipeline (event-bus merge
        // window + VM init delay + hasCachedData probes) can take >1s to reach
        // loadAudioFiles, during which the Directory/All overview would flash
        // the skeleton even though the aggregator already rendered the cached
        // library (snapshot hydration / filteredAllAudios). Display is fully
        // independent of the scan: the cold-start INCREMENTAL reconciles SAF
        // dirs in the background while cached data shows instantly.
        viewModelScope.launch {
            audioFileScanner.libraryInitialized.first { it }
            if (audioFileScanner.hasCachedData()) {
                _isInitialLoad.update { false }
            }
        }

        // Unified change-event bus consumer. Every refresh trigger
        // (MediaStore observer, SAF watcher, pull-to-refresh, batch edits,
        // metadata save, on-resume, cold start) emits a LibraryChangeEvent via
        // LibraryDataHolder. Events are merged here: Directory events coalesce
        // per directory, and a Global request supersedes all pending directory
        // scans — so a burst of triggers collapses into a single merged scan.
        viewModelScope.launch {
            libraryDataHolder.changeEvents()
                .collect { event -> handleChangeEvent(event) }
        }

        // React to changes in the selected directory URIs (written by
        // DirectoryManagementViewModel via the settings screen). This keeps
        // _selectedDirectories in sync without requiring the settings flow
        // to call into LibraryScanViewModel directly.
        viewModelScope.launch {
            settingsDataStore.selectedDirectoryUris
                .map { uris -> uris.toSet() }
                .distinctUntilChanged()
                .collect { uris ->
                    val currentUris = _selectedDirectories.value.map { it.uri }.toSet()
                    if (uris != currentUris) {
                        syncSelectedDirectoriesFromStorage()
                    }
                }
        }

        viewModelScope.launch {
            delay(300L)
            libraryRepository.startWatchingSettings()
            checkDirectorySnapshotsOnStart()
        }
    }

    private suspend fun checkDirectorySnapshotsOnStart() {
        syncSelectedDirectoriesFromStorage()

        // Unconditionally request an incremental scan on cold start. The
        // previous snapshot-count vs _directoryFiles-size gate was unreliable
        // for external deletions: both values reflected the pre-deletion
        // state, so they matched and no scan ran — files deleted via the
        // system file manager never disappeared from the cache. Also, SAF-only
        // deletions never change the MediaStore version, so no watcher fires.
        //
        // SnapshotCheck maps to Global(INCREMENTAL) in handleChangeEvent — a
        // diff scan (mtime-based, unchanged files retained), not a full rescan,
        // and the skeleton is released by loadAudioFiles as soon as cache is
        // confirmed so cold start stays instant. The merge window coalesces
        // this with any concurrent refresh request so no double scan runs.
        libraryDataHolder.requestSnapshotCheck()
    }

    /**
     * Entry point for app initialization during the splash screen.
     * Restores selected directories and performs the initial scan.
     */
    fun initializeApp() {
        restoreSelectedDirectories()
    }

    /**
     * Loads all audio files from device storage.
     *
     * At startup: prefer cache, no incremental scan by default.
     * User manual refresh (pull-to-refresh) triggers incremental scan.
     * Force refresh triggers full rescan.
     *
     * @param strategy LAZY skips the scan when cache exists; INCREMENTAL always
     *   runs the diff scan (mtime-based, unchanged files retained); FORCE does a
     *   full rescan. Cold-start snapshot checks and resume callbacks use
     *   INCREMENTAL so external deletions in SAF whitelist dirs are purged
     *   (lesson #14).
     */
    fun loadAudioFiles(strategy: RefreshStrategy = RefreshStrategy.LAZY) {
        // A LAZY no-op must never cancel an in-flight scan. loadAudioFiles used
        // to cancel scanJob unconditionally, so a resume/periodic LAZY event
        // arriving mid-scan killed the whitelist incremental before it wrote to
        // the cache — logs showed the scan, the UI never updated. LAZY means
        // "nothing to do if cache exists", so an in-flight INCREMENTAL is always
        // more valuable; let it finish.
        if (strategy == RefreshStrategy.LAZY && scanJob?.isActive == true) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val forceRefresh = strategy == RefreshStrategy.FORCE
            val isIncremental = strategy != RefreshStrategy.FORCE
            val hasCacheNow = audioFileScanner.hasCachedData()
            Timber.tag("Voxly").i("DIAG loadAudioFiles strategy=$strategy hasCache=$hasCacheNow force=$forceRefresh")
            // LAZY + cached data = nothing to scan: the aggregator already
            // rendered the cache. Short-circuit BEFORE beginScan so the
            // skeleton / pull-to-refresh never flashes on cold start.
            if (strategy == RefreshStrategy.LAZY && hasCacheNow) {
                _isInitialLoad.update { false }
                return@launch
            }
            // MediaStore version short-circuit: when the collection is
            // provably unchanged since the last successful scan, the diff
            // inside the incremental scan would find nothing anyway — skip
            // the two full MediaStore queries + purge entirely. Skipped when
            // FORCE (user asked for a real rescan) and when SAF whitelist
            // dirs are configured (their deletions never bump the MediaStore
            // version, so they are the guaranteed purge point — lesson #14).
            // The MediaStore observer / SAF watcher fire only on real
            // changes, so a changed version always lets this pass.
            if (isIncremental && hasCacheNow && shouldSkipScanForUnchangedMediaStore()) {
                Timber.tag("Voxly").i("DIAG MediaStore version unchanged, skipping cold-start/resume scan")
                _isInitialLoad.update { false }
                return@launch
            }
            // Cache is already rendered by the aggregator's kickOffInitialBuild —
            // release the skeleton immediately so the cold-start INCREMENTAL scan
            // runs silently in the background instead of flashing the skeleton /
            // spinner. A first install (no cache) keeps the skeleton until the
            // full scan finishes.
            if (hasCacheNow) {
                _isInitialLoad.update { false }
            }
            val shouldShowRefresh = forceRefresh || _isInitialLoad.value
            if (shouldShowRefresh) {
                libraryDataHolder.beginScan()
            }
            try {
                if (_selectedDirectories.value.isNotEmpty()) {
                    val hasCache = audioFileScanner.hasCachedData()
                    if (forceRefresh || !hasCache || strategy == RefreshStrategy.INCREMENTAL) {
                        val useIncremental = isIncremental && hasCache
                        scanSelectedDirectories(_selectedDirectories.value, useIncremental, forceRefresh)
                    }
                    _isInitialLoad.update { false }
                    return@launch
                }

                if (forceRefresh || !audioFileScanner.hasCachedData()) {
                    audioFileScanner.scan(
                        directoryPaths = emptyList(),
                        incremental = isIncremental,
                        forceRefresh = forceRefresh
                    )
                } else if (isIncremental) {
                    // Diff-based incremental scan (MediaStore DATE_MODIFIED >
                    // last scan time): re-reads only changed files, retains the
                    // rest from cache, purges files deleted from MediaStore.
                    // Runs whenever the user, a system trigger, or a cold-start
                    // snapshot check asks for one.
                    audioFileScanner.scan(
                        directoryPaths = emptyList(),
                        incremental = true,
                        forceRefresh = false,
                    )
                }
                _isInitialLoad.update { false }

                // Persist the current MediaStore version AFTER a successful
                // scan so subsequent calls can short-circuit. This is safe
                // even on the cache-served path: the cache IS the current
                // MediaStore state at the recorded version.
                if (!forceRefresh) {
                    val v = mediaStoreVersionCache.current()
                    if (v.isNotEmpty()) settingsDataStore.setLastKnownMediaStoreVersion(v)
                }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("Audio files load cancelled")
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load audio files")
                val msg = e.message ?: "Unknown scan error"
                libraryDataHolder.emitScanError(msg)
            } finally {
                if (shouldShowRefresh) {
                    libraryDataHolder.endScan()
                }
            }
        }
    }

    /**
     * True when the MediaStore audio collection is provably unchanged since
     * the last successful scan: cache exists, a version was recorded, and the
     * current [MediaStore.getVersion] matches it.
     *
     * Never returns true when SAF whitelist directories are configured — their
     * external deletions do not bump the MediaStore version, so the per-dir
     * incremental scan on resume/start stays the guaranteed purge point
     * (lesson #14).
     */
    private suspend fun shouldSkipScanForUnchangedMediaStore(): Boolean {
        // SAF-picked dirs bypass the version check entirely.
        if (_selectedDirectories.value.isNotEmpty()) return false
        val lastVersion = settingsDataStore.lastKnownMediaStoreVersion.first()
        if (lastVersion.isEmpty()) return false
        return runCatching { mediaStoreVersionCache.current() }.getOrDefault("") == lastVersion
    }

    /**
     * Unified refresh entry point for all screens.
     * - forceRefresh=true: Full rescan, ignores cache
     * - forceRefresh=false: Incremental scan, detects new/modified files
     */
    fun refresh(forceRefresh: Boolean = false) {
        Timber.tag("Voxly").i("LibraryScanViewModel scan triggered: incremental=${!forceRefresh}")
        libraryRepository.refresh(
            if (forceRefresh) RefreshStrategy.FORCE else RefreshStrategy.INCREMENTAL
        )
    }

    /**
     * Triggered from MainActivity's ON_RESUME lifecycle event.
     *
     * SAF-picked whitelist directories have no filesystem change notification
     * (no inotify for content:// URIs), so the MediaStore observer in
     * [MediaStoreChangeWatcher] never fires for them. This covers the common
     * "delete a song in the system file manager → switch back to Voxly" flow
     * by requesting an incremental scan on resume. Throttled to coalesce
     * sub-[RESUME_REFRESH_THROTTLE_MS] resume/pause bursts (notification
     * shade, permission dialogs) while still firing for any meaningful
     * backgrounding (>3s).
     *
     * Uses [RefreshStrategy.INCREMENTAL] (not LAZY): LAZY would skip the scan
     * whenever cache exists, silently breaking external-deletion sync — SAF
     * trees have no change notification and SAF-only deletions never change
     * the MediaStore version, so resume is the guaranteed purge point
     * (lesson #14). The whitelist path flows to
     * [com.voxly.data.local.scanner.DirectoryScanStrategy.scanDirectoriesIncremental], which
     * queries the current filesystem paths and purges cached rows whose paths
     * are gone; the global (no-whitelist) path flows to
     * [com.voxly.data.local.scanner.IncrementalScanStrategy], which calls
     * [com.voxly.data.local.MusicLibraryCache.cleanupDeletedFiles].
     *
     * The event-bus merge window in [handleChangeEvent] coalesces this with
     * any concurrent refresh request (e.g. the cold-start request from
     * [checkDirectorySnapshotsOnStart]) so no double scan runs.
     */
    fun refreshOnResume() {
        // The first ON_RESUME fires during onCreate; the cold-start SnapshotCheck
        // already requests the identical INCREMENTAL scan, so skip the duplicate
        // and let the merge window coalesce the real events.
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastResumeRefreshAt < RESUME_REFRESH_THROTTLE_MS) return
        lastResumeRefreshAt = now
        // INCREMENTAL (not LAZY): SAF-picked whitelist directories have no
        // filesystem change notification and SAF-only deletions never change
        // the MediaStore version, so resume is the guaranteed purge/diff point
        // (lesson #14). LAZY would skip the scan whenever cache exists, silently
        // breaking external-deletion sync. Still throttled above to coalesce
        // resume/pause bursts.
        libraryRepository.refresh(RefreshStrategy.INCREMENTAL)
    }

    fun refreshDirectoryIncremental(directoryUri: String) {
        val dir = _selectedDirectories.value.firstOrNull { it.uri == directoryUri } ?: return
        libraryDataHolder.requestDirectoryRefresh(
            directoryUri = dir.uri,
            directoryPath = dir.path,
            forceRefresh = false
        )
    }

    fun syncAndScanDirectoriesIncremental() {
        viewModelScope.launch {
            syncSelectedDirectoriesFromStorage()
            if (_selectedDirectories.value.isNotEmpty()) {
                launchDirectoryScan(
                    _selectedDirectories.value,
                    isIncremental = true,
                    forceRefresh = false
                )
            }
        }
    }

    /**
     * Scans audio files from a specific directory.
     * Only triggers scan if directory is not already loaded.
     * If already loaded, uses cached data.
     */
    fun loadFromDirectory(directoryUri: Uri) {
        val uriString = directoryUri.toString()
        if (_selectedDirectories.value.any { it.uri == uriString }) {
            return
        }
        addDirectory(directoryUri)
    }

    /**
     * Adds one directory to the selected directory set and reloads.
     * If directory is already loaded, triggers incremental scan for new/deleted files.
     * If directory is new, triggers full scan.
     */
    fun addDirectory(directoryUri: Uri) {
        val filePath = getPathFromUri(directoryUri)
        if (filePath.isBlank()) {
            return
        }

        val uriString = directoryUri.toString()

        if (uriString in _directoryLoadingState.value) {
            return
        }

        val alreadySelected = _selectedDirectories.value.any { it.uri == uriString }
        val alreadyLoaded = directoryFiles.value[uriString]?.isNotEmpty() == true

        if (alreadySelected) {
            // Route through the unified bus: a loaded dir gets an incremental
            // scan, a selected-but-empty dir gets a full rescan.
            val force = !alreadyLoaded
            libraryDataHolder.requestDirectoryRefresh(
                directoryUri = uriString,
                directoryPath = filePath,
                forceRefresh = force
            )
            return
        }

        val updatedDirectories = (_selectedDirectories.value + SelectedDirectory(
            uri = uriString,
            path = filePath
        )).distinctBy { it.uri }

        _selectedDirectories.update { updatedDirectories }
        persistSelectedDirectories(updatedDirectories)
        libraryDataHolder.requestDirectoryRefresh(
            directoryUri = uriString,
            directoryPath = filePath,
            forceRefresh = true
        )
    }

    /**
     * Removes one selected directory and reloads.
     */
    fun removeDirectory(directoryUri: String) {
        val updatedDirectories = _selectedDirectories.value.filterNot { it.uri == directoryUri }
        _selectedDirectories.update { updatedDirectories }
        if (_openedDirectoryUri.value == directoryUri) {
            _openedDirectoryUri.update { null }
        }
        persistSelectedDirectories(updatedDirectories)
        libraryRepository.refresh(RefreshStrategy.INCREMENTAL)
    }

    /**
     * Clears selected directories and falls back to global scan.
     */
    fun clearDirectories() {
        _selectedDirectories.update { emptyList() }
        _openedDirectoryUri.update { null }
        persistSelectedDirectories(emptyList())
        libraryRepository.refresh(RefreshStrategy.INCREMENTAL)
    }

    fun openDirectory(directoryUri: String) {
        _openedDirectoryUri.update { directoryUri }
    }

    fun closeOpenedDirectory() {
        _openedDirectoryUri.update { null }
    }

    private suspend fun scanSelectedDirectories(
        directories: List<SelectedDirectory>,
        isIncremental: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        Timber.d(TAG, "Scanning ${directories.size} directories (incremental=$isIncremental, force=$forceRefresh)")
        Timber.tag("Voxly").i("DIAG scanSelectedDirectories incremental=$isIncremental force=$forceRefresh")

        val dirUris = directories.map { it.uri }.toSet()
        _directoryLoadingState.update { it + dirUris }

        try {
            val paths = directories.map { it.path }.filter { it.isNotBlank() }
            val filesByDir = if (paths.isEmpty()) {
                emptyMap<String, List<AudioFile>>()
            } else {
                val files = audioFileScanner.scan(
                    directoryPaths = paths,
                    incremental = isIncremental,
                    forceRefresh = forceRefresh
                )

                withContext(Dispatchers.Default) {
                    groupFilesBySelectedDirectory(files, directories)
                }
            }

            // directoryFiles is derived from allAudios (Room cache), which the
            // scan above has already updated — nothing to write here.

            directories.forEach { dir ->
                    val fileCount = filesByDir[dir.uri]?.size ?: 0
                    musicLibraryCache.saveDirectorySnapshot(dir.uri, fileCount)
                }

            if (_openedDirectoryUri.value != null && _openedDirectoryUri.value !in filesByDir.keys) {
                _openedDirectoryUri.update { null }
            }
        } catch (e: CancellationException) {
            Timber.tag(TAG).d("Directory scan cancelled")
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Directory scan failed for ${directories.joinToString { it.path }}", e)
            val msg = e.message ?: "Directory scan failed"
            libraryDataHolder.emitScanError(msg)
        } finally {
            _directoryLoadingState.update { it - dirUris }
        }
    }

    private fun groupFilesBySelectedDirectory(
        files: List<AudioFile>,
        directories: List<SelectedDirectory>
    ): Map<String, List<AudioFile>> {
        val normalizedDirectories = directories.map {
            NormalizedDirectory(
                uri = it.uri,
                path = it.path.trimEnd('/', '\\')
            )
        }.sortedByDescending { it.path.length }

        val groupedFiles = LinkedHashMap<String, MutableList<AudioFile>>(normalizedDirectories.size)
        normalizedDirectories.forEach { groupedFiles[it.uri] = mutableListOf() }

        files.forEach { file ->
            val normalizedFilePath = file.path.trimEnd('/', '\\')
            val matchedDirectory = normalizedDirectories.firstOrNull { directory ->
                normalizedFilePath == directory.path ||
                    normalizedFilePath.startsWith("${directory.path}/") ||
                    normalizedFilePath.startsWith("${directory.path}\\")
            }

            if (matchedDirectory != null) {
                groupedFiles.getValue(matchedDirectory.uri).add(file)
            }
        }

        return groupedFiles.mapValues { (_, grouped) -> grouped.toList() }
    }

    private data class NormalizedDirectory(
        val uri: String,
        val path: String
    )

    private fun sortAudioFiles(
        files: List<AudioFile>,
        sortOption: DirFileSortOption
    ): List<AudioFile> {
        return when (sortOption) {
            DirFileSortOption.NAME_ASC -> files.sortedBy {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            DirFileSortOption.NAME_DESC -> files.sortedByDescending {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            DirFileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            DirFileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
        }
    }

    private fun String.toDirFileSortOption(): DirFileSortOption {
        return try {
            DirFileSortOption.valueOf(this)
        } catch (_: IllegalArgumentException) {
            DirFileSortOption.NAME_ASC
        }
    }

    private suspend fun syncSelectedDirectoriesFromStorage() {
        val uris = settingsDataStore.selectedDirectoryUris.first()
        val restored = uris.mapNotNull { uriString ->
            val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
            val path = getPathFromUri(parsed)
            if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
        }
        if (restored.map { it.uri } != _selectedDirectories.value.map { it.uri }) {
            _selectedDirectories.update { restored }
            if (_openedDirectoryUri.value != null && restored.none { it.uri == _openedDirectoryUri.value }) {
                _openedDirectoryUri.update { null }
            }
        }
    }

    private fun restoreSelectedDirectories() {
        viewModelScope.launch {
            val uris = settingsDataStore.selectedDirectoryUris.first()
            val restored = uris.mapNotNull { uriString ->
                val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
                val path = getPathFromUri(parsed)
                if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
            }
            _selectedDirectories.update { restored }
            // Cold-start scan through the unified bus (merges with the
            // SnapshotCheck emitted by checkDirectorySnapshotsOnStart).
            libraryDataHolder.requestSnapshotCheck()
        }
    }

    private fun persistSelectedDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setSelectedDirectoryUris(directories.map { it.uri })
        }
    }

    /**
     * Converts a content URI to a file path.
     * Delegates to the canonical implementation in PathUtils.
     */
    fun getPathFromUri(uri: Uri): String = com.voxly.core.util.PathUtils.getPathFromUri(uri)

    fun renameSingleFile(
        filePath: String,
        targetName: String,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)
                    var processedName = targetName
                    if (targetName.contains(Regex("\\{.*\\}"))) {
                        val metadata = audioRepository.readMetadata(filePath).getOrNull()
                        processedName = targetName
                            .replace("{title}", metadata?.title ?: file.nameWithoutExtension)
                            .replace("{artist}", metadata?.artist ?: "Unknown")
                            .replace("{album}", metadata?.album ?: "Unknown")
                            .replace("{track}", (metadata?.trackNumber ?: 1).toString().padStart(2, '0'))
                            .replace("{track00}", (metadata?.trackNumber ?: 1).toString().padStart(2, '0'))
                            .replace("{track000}", (metadata?.trackNumber ?: 1).toString().padStart(3, '0'))
                    }

                    val sanitizedName = processedName.trim()
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (sanitizedName.isBlank()) {
                        return@runCatching false to "File name cannot be empty"
                    }

                    val extension = file.extension
                    val finalName = if (
                        extension.isNotBlank() &&
                        !sanitizedName.endsWith(".$extension", ignoreCase = true)
                    ) {
                        "$sanitizedName.$extension"
                    } else {
                        sanitizedName
                    }

                    if (file.name.equals(finalName, ignoreCase = true)) {
                        return@runCatching true to null
                    }

                    val targetFile = File(file.parentFile, finalName)
                    if (targetFile.absolutePath.equals(file.absolutePath, ignoreCase = true)) {
                        return@runCatching true to null
                    }
                    if (targetFile.exists()) {
                        return@runCatching false to "Target file already exists"
                    }

                    if (file.exists() && file.renameTo(targetFile)) {
                        return@runCatching true to null
                    }

                    val safResult = renameFileViaSaf(filePath, finalName)
                    safResult.fold(
                        onSuccess = { true to null },
                        onFailure = { false to (it.message ?: "Failed to rename file") }
                    )
                }.getOrElse { false to (it.message ?: "Failed to rename file") }
            }

            onComplete(result.first, result.second)
            if (result.first) {
                // Incremental: remove the old path from cache so the next scan
                // does a complete re-index of the renamed file, but skip the
                // expensive full-rescan path.
                viewModelScope.launch {
                    musicLibraryCache.removeFromCache(filePath)
                }
                libraryRepository.refresh(RefreshStrategy.INCREMENTAL)
            }
        }
    }

    fun deleteSingleFile(
        filePath: String,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)
                    if (file.exists() && file.delete()) {
                        return@runCatching true to null
                    }
                    val safResult = deleteFileViaSaf(filePath)
                    safResult.fold(
                        onSuccess = { true to null },
                        onFailure = { false to (it.message ?: "Failed to delete file") }
                    )
                }.getOrElse { false to (it.message ?: "Failed to delete file") }
            }

            onComplete(result.first, result.second)
            if (result.first) {
                viewModelScope.launch {
                    musicLibraryCache.removeFromCache(filePath)
                }
                libraryRepository.refresh(RefreshStrategy.INCREMENTAL)
            }
        }
    }

    private fun renameFileViaSaf(filePath: String, targetDisplayName: String): Result<Unit> {
        val targetDocUri = safWriteAccessService.resolveWritableDocumentUri(filePath)
            ?: return Result.failure(IllegalStateException("No SAF write permission for this file"))

        return runCatching {
            val renamed = DocumentsContract.renameDocument(
                context.contentResolver,
                targetDocUri,
                targetDisplayName
            )
            if (renamed == null) {
                throw IllegalStateException("Failed to rename file")
            }
        }
    }

    private fun deleteFileViaSaf(filePath: String): Result<Unit> {
        val targetDocUri = safWriteAccessService.resolveWritableDocumentUri(filePath)
            ?: return Result.failure(IllegalStateException("No SAF write permission for this file"))

        return runCatching {
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, targetDocUri)
            if (!deleted) {
                throw IllegalStateException("Failed to delete file")
            }
        }
    }
}

/**
 * Case-insensitive substring match used by the unified library search.
 * [query] must already be trimmed + lowercased. Compares the filename, title,
 * artist and album; null metadata fields simply don't match.
 */
private fun AudioFile.matchesLibrarySearch(query: String): Boolean {
    if (name.lowercase().contains(query)) return true
    val metadata = metadata
    return metadata.title?.lowercase()?.contains(query) == true ||
        metadata.artist?.lowercase()?.contains(query) == true ||
        metadata.album?.lowercase()?.contains(query) == true
}
