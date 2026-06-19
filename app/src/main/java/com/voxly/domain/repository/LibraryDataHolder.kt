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
 * Refresh request payload propagated through [LibraryDataHolder.refreshTriggers].
 *
 * @property forceRefresh True to ignore cache and perform a full rescan.
 * @property bypassVersionCache True to skip the MediaStore version short-circuit
 *   that [LibraryScanViewModel] normally applies. Set true for user-initiated
 *   pull-to-refresh (the user is explicitly asking for a fresh scan); leave
 *   false for system-triggered refreshes (MediaStore observer, periodic worker,
 *   SAF tree walker) so they stay cheap when nothing changed.
 */
data class RefreshRequest(
    val forceRefresh: Boolean,
    val bypassVersionCache: Boolean,
)

/**
 * Singleton holder for library-wide shared scan coordination.
 * Provides:
 *  - a conflated [refreshTriggers] flow that [LibraryScanViewModel] collects
 *    (composed with `collectLatest`) to know when to scan;
 *  - a refcounted [isRefreshing] flag: the spinner is on while any active
 *    scan lifetime has called [beginScan] without a matching [endScan].
 *    Multiple concurrent scans accumulate; cancellation-safe (decrement is
 *    clamped at 0).
 *
 * Since @HiltViewModel cannot be injected into other @HiltViewModels,
 * we use this singleton to coordinate between them.
 *
 * Conflation policy on the trigger flow: `extraBufferCapacity = 1` +
 * `DROP_OLDEST` means that when a new value arrives while the buffer is full,
 * the older buffered value is dropped (the new one is preserved). Combined
 * with `replay = 0`, only the latest value reaches a slow collector.
 */
@Singleton
class LibraryDataHolder @Inject constructor() {

    private val _refreshTrigger = MutableSharedFlow<RefreshRequest>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Composable trigger flow — see class kdoc for conflation policy. */
    fun refreshTriggers(): Flow<RefreshRequest> = _refreshTrigger

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
     * Convenience method to trigger refresh from any ViewModel.
     * Uses tryEmit but with the buffer-overflow policy we never lose requests
     * here -- the buffer is sized to keep at least one pending value.
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
        _refreshTrigger.tryEmit(RefreshRequest(forceRefresh, bypassVersionCache))
    }

    /** Scan error events — UI layers collect and show via Snackbar. */
    private val _scanError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanError: SharedFlow<String> = _scanError.asSharedFlow()

    /** Emit a scan error message (called by [LibraryScanViewModel]). */
    fun emitScanError(message: String) {
        _scanError.tryEmit(message)
    }
}