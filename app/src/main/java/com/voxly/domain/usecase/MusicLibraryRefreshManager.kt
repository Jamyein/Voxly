package com.voxly.domain.usecase

import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.LibraryRefreshState
import com.voxly.domain.repository.AudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified manager for music library refresh operations.
 *
 * Responsibilities:
 * - Centralized trigger for all scan operations
 * - Listening to settings changes to auto-trigger refresh
 * - Exposing refresh state Flow for UI subscription
 * - Managing scan cancellation
 */
@Singleton
class MusicLibraryRefreshManager @Inject constructor(
    private val audioRepository: AudioRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LibraryRefreshManager"
    }

    private val _refreshState = MutableStateFlow<LibraryRefreshState>(LibraryRefreshState.Idle)
    val refreshState: StateFlow<LibraryRefreshState> = _refreshState.asStateFlow()

    // Track previous settings to detect changes
    private var lastMinDurationFilterEnabled = false
    private var lastWhitelistEnabled = false
    private var lastBlacklistEnabled = false

    // Current scan job for cancellation support
    private var currentScanJob: Job? = null

    /**
     * Starts watching settings changes and auto-triggers refresh when relevant settings change.
     * Should be called once at app startup.
     */
    fun startWatchingSettings() {
        val minDurationFilterEnabled = settingsDataStore.minDurationFilterEnabled
            .stateIn(scope, SharingStarted.Eagerly, false)
        val whitelistEnabled = settingsDataStore.whitelistEnabled
            .stateIn(scope, SharingStarted.Eagerly, false)
        val blacklistEnabled = settingsDataStore.blacklistEnabled
            .stateIn(scope, SharingStarted.Eagerly, false)

        scope.launch {
            combine(
                minDurationFilterEnabled,
                whitelistEnabled,
                blacklistEnabled
            ) { minDuration, whitelist, blacklist ->
                Triple(minDuration, whitelist, blacklist)
            }.collect { (minDuration, whitelist, blacklist) ->
                // Check and trigger refresh for each setting that changed
                if (lastMinDurationFilterEnabled != minDuration) {
                    lastMinDurationFilterEnabled = minDuration
                    Timber.d(TAG, "Min duration filter changed, triggering refresh")
                    refresh(force = true)
                }
                if (lastWhitelistEnabled != whitelist) {
                    lastWhitelistEnabled = whitelist
                    Timber.d(TAG, "Whitelist enabled changed, triggering refresh")
                    refresh(force = true)
                }
                if (lastBlacklistEnabled != blacklist) {
                    lastBlacklistEnabled = blacklist
                    Timber.d(TAG, "Blacklist enabled changed, triggering refresh")
                    refresh(force = true)
                }
            }
        }
    }

    /**
     * Triggers a library refresh.
     * @param force If true, forces a full rescan ignoring cache
     * @return List of scanned audio files
     */
    suspend fun refresh(force: Boolean = false): List<AudioFile> {
        // Cancel any existing scan
        cancel()

        _refreshState.value = LibraryRefreshState.Scanning(null)

        return try {
            val files = audioRepository.scanAudioFiles(forceRefresh = force).first()
            _refreshState.value = LibraryRefreshState.Success(files.size)
            Timber.d(TAG, "Library refresh completed: ${files.size} files")
            files
        } catch (e: CancellationException) {
            _refreshState.value = LibraryRefreshState.Idle
            Timber.d(TAG, "Library refresh cancelled")
            emptyList()
        } catch (e: Exception) {
            _refreshState.value = LibraryRefreshState.Error(e.message ?: "Unknown error")
            Timber.tag(TAG).e(e, "Library refresh failed")
            emptyList()
        }
    }

    /**
     * Triggers a library refresh using coroutines (non-blocking).
     * Use this from ViewModel contexts.
     * @param force If true, forces a full rescan ignoring cache
     * @param onComplete Optional callback when refresh completes
     */
    fun refreshAsync(
        force: Boolean = false,
        onComplete: ((List<AudioFile>) -> Unit)? = null
    ) {
        currentScanJob?.cancel()
        currentScanJob = scope.launch {
            val files = refresh(force = force)
            onComplete?.invoke(files)
        }
    }

    /**
     * Cancels any ongoing scan operation.
     */
    fun cancel() {
        currentScanJob?.cancel()
        currentScanJob = null
    }

    /**
     * Resets the state to Idle.
     * Call this when the UI has consumed the refresh state.
     */
    fun resetState() {
        if (_refreshState.value !is LibraryRefreshState.Idle) {
            _refreshState.value = LibraryRefreshState.Idle
        }
    }
}
