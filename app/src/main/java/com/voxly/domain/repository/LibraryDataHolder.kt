package com.voxly.domain.repository

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Origin of a [LibraryChangeEvent]. Used for merge-window tuning and logging.
 */
enum class ChangeSource {
    MEDIA_STORE,
    SAF_TREE,
    PULL_TO_REFRESH,
    ON_RESUME,
    PERIODIC_WORKER,
    SETTINGS,
    FILE_EDIT,
    DIRECTORY_MANAGEMENT,
    BATCH_EDIT,
    COLD_START,
    APP
}

/**
 * Typed change events pushed by every library refresh trigger — MediaStore
 * observer, SAF tree watcher, pull-to-refresh, on-resume, batch edits,
 * metadata save, directory management, periodic worker — onto the unified
 * change-event bus in [LibraryDataHolder.changeEvents].
 *
 * The single consumer ([com.voxly.presentation.viewmodel.LibraryScanViewModel])
 * debounces and merges these per target:
 *  - [Global] scans the whole library (full or incremental);
 *  - [Directory] scans a single SAF/whitelist directory incrementally and is
 *    coalesced with other [Directory] events before one merged scan runs;
 *  - [SingleFile] hot-syncs one file to the cache immediately (metadata edits);
 *  - [SnapshotCheck] requests an on-demand incremental scan (cold start,
 *    directory snapshot verification).
 */
sealed class LibraryChangeEvent {
    data class Global(
        val forceRefresh: Boolean = false,
        val bypassVersionCache: Boolean = false,
        val source: ChangeSource = ChangeSource.APP
    ) : LibraryChangeEvent()

    data class Directory(
        val directoryUri: String,
        val directoryPath: String,
        val forceRefresh: Boolean = false,
        val source: ChangeSource = ChangeSource.APP
    ) : LibraryChangeEvent()

    data class SingleFile(
        val filePath: String,
        val source: ChangeSource = ChangeSource.FILE_EDIT
    ) : LibraryChangeEvent()

    data object SnapshotCheck : LibraryChangeEvent()
}

/**
 * Singleton holder for the library-wide unified change-event bus.
 * Provides:
 *  - a conflated [changeEvents] flow that [LibraryScanViewModel] collects to
 *    know when to scan (per-target merge window collapses bursts);
 *  - a refcounted [isRefreshing] flag: the spinner is on while any active
 *    scan lifetime has called [beginScan] without a matching [endScan].
 *    Multiple concurrent scans accumulate; cancellation-safe (decrement is
 *    clamped at 0).
 *
 * Since @HiltViewModel cannot be injected into other @HiltViewModels,
 * we use this singleton to coordinate between them.
 *
 * Conflation policy on the event flow: `extraBufferCapacity = 4` +
 * `DROP_OLDEST` means that when a new value arrives while the buffer is full,
 * the older buffered value is dropped (the new one is preserved). Combined
 * with `replay = 0`, only the latest value reaches a slow collector.
 */
@Singleton
class LibraryDataHolder @Inject constructor() {

    private val _changeEvents = MutableSharedFlow<LibraryChangeEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Composable unified change-event bus — see class kdoc for conflation policy. */
    fun changeEvents(): Flow<LibraryChangeEvent> = _changeEvents

    /**
     * Active-scan refcount. Spinner is on while > 0. Each scan lifetime
     * (e.g. `loadAudioFiles`, `launchDirectoryScan`) must pair exactly one
     * [beginScan] with one [endScan] in a finally block — concurrent scans
     * correctly accumulate so the spinner stays on until all are done.
     */
    private val activeScans = AtomicInteger(0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Increment scan refcount; spinner turns on at 1. */
    fun beginScan() {
        if (activeScans.incrementAndGet() == 1) {
            _isRefreshing.value = true
        }
    }

    /** Decrement scan refcount; spinner turns off at 0. Clamped at 0 (defensive). */
    fun endScan() {
        if (maxOf(0, activeScans.decrementAndGet()) == 0) {
            _isRefreshing.value = false
        }
    }

    /**
     * Legacy convenience — emits a [LibraryChangeEvent.Global].
     *
     * @param forceRefresh Full rescan, ignores cache.
     * @param bypassVersionCache Skip the MediaStore version short-circuit.
     *   Pass `true` from user-initiated pull-to-refresh so the spinner always
     *   corresponds to a real scan attempt. System-driven refreshes
     *   (MediaStore observer, periodic worker, SAF walker) keep the default
     *   `false` to stay cheap when MediaStore has not changed.
     */
    fun requestRefresh(
        forceRefresh: Boolean = false,
        bypassVersionCache: Boolean = false,
    ) {
        _changeEvents.tryEmit(LibraryChangeEvent.Global(forceRefresh, bypassVersionCache, ChangeSource.APP))
    }

    /** Request a whole-library refresh (full when [forceRefresh], else incremental). */
    fun requestGlobalRefresh(
        forceRefresh: Boolean = false,
        bypassVersionCache: Boolean = false,
        source: ChangeSource = ChangeSource.APP
    ) {
        _changeEvents.tryEmit(LibraryChangeEvent.Global(forceRefresh, bypassVersionCache, source))
    }

    /** Request an incremental scan of a single directory (merged by the consumer). */
    fun requestDirectoryRefresh(
        directoryUri: String,
        directoryPath: String,
        forceRefresh: Boolean = false,
        source: ChangeSource = ChangeSource.APP
    ) {
        _changeEvents.tryEmit(LibraryChangeEvent.Directory(directoryUri, directoryPath, forceRefresh, source))
    }

    /** Request an immediate single-file cache hot-sync (e.g. after a metadata edit). */
    fun requestSingleFileSync(filePath: String, source: ChangeSource = ChangeSource.FILE_EDIT) {
        _changeEvents.tryEmit(LibraryChangeEvent.SingleFile(filePath, source))
    }

    /** Request an on-demand incremental scan (cold start / directory snapshot check). */
    fun requestSnapshotCheck() {
        _changeEvents.tryEmit(LibraryChangeEvent.SnapshotCheck)
    }

    /** Scan error events — UI layers collect and show via Snackbar. */
    private val _scanError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanError: SharedFlow<String> = _scanError.asSharedFlow()

    /** Emit a scan error message (called by [LibraryScanViewModel]). */
    fun emitScanError(message: String) {
        _scanError.tryEmit(message)
    }
}
