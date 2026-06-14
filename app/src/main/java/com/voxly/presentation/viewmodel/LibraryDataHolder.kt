package com.voxly.presentation.viewmodel

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton holder for library-wide shared scan coordination.
 * Provides:
 *  - a conflated [refreshTriggers] flow that [LibraryScanViewModel] collects
 *    (composed with `collectLatest`) to know when to scan;
 *  - a global [isRefreshing] flag mirroring the current scan state so any
 *    per-screen ViewModel — even one created mid-scan — reflects the
 *    spinner state correctly without waiting for the next trigger emit.
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

    private val _refreshTrigger = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Composable trigger flow — see class kdoc for conflation policy. */
    fun refreshTriggers(): Flow<Boolean> = _refreshTrigger

    /**
     * Global "is a library scan currently in flight" flag. Updated by the
     * single [LibraryScanViewModel.loadAudioFiles] entry/exit, which is the
     * only place that actually performs scans.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Convenience method to trigger refresh from any ViewModel.
     * Uses tryEmit but with the buffer-overflow policy we never lose requests
     * here -- the buffer is sized to keep at least one pending value.
     */
    fun requestRefresh(forceRefresh: Boolean = false) {
        _refreshTrigger.tryEmit(forceRefresh)
    }

    /**
     * Set the global "refreshing" flag. Called by [LibraryScanViewModel] at
     * scan entry/exit — the single producer of this state.
     */
    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }
}
