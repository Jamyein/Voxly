package com.voxly.domain.usecase

import com.voxly.core.util.Constants
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AudioFile
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [UnifiedScanManager]
 *
 * Provides unified scanning operations for:
 * - Global device scan
 * - Incremental scan (new/modified files)
 * - Directory-specific scan
 * - Single file sync (after metadata edit)
 *
 * Also watches settings changes for auto-refresh.
 */
@Singleton
class UnifiedScanManagerImpl @Inject constructor(
    private val audioFileScanner: AudioFileScanner,
    private val musicLibraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val scope: CoroutineScope
) : UnifiedScanManager {

    companion object {
        private const val TAG = "UnifiedScanManager"
    }

    // Flag to prevent duplicate settings watching
    private var isWatchingSettings = false

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Settings change tracking
    private var lastMinDurationFilterEnabled = false
    private var lastWhitelistEnabled = false
    private var lastBlacklistEnabled = false

    // Current scan job for cancellation
    private var currentScanJob: Job? = null

    override suspend fun scan(
        target: ScanTarget,
        force: Boolean
    ): ScanResult {
        // Cancel any existing scan
        cancel()

        _scanState.value = ScanState.Scanning(target, 0f)

        return try {
            val files = when (target) {
                is ScanTarget.Global -> {
                    performGlobalScan(force)
                }
                is ScanTarget.Incremental -> {
                    performIncrementalScan()
                }
                is ScanTarget.Directories -> {
                    performDirectoryScan(target.paths, force)
                }
                is ScanTarget.SingleFile -> {
                    // Single file doesn't return a list, handled separately
                    performSingleFileScan(target.path)
                    return ScanResult.Success(emptyList())
                }
            }
            _scanState.value = ScanState.Success(files.size, target)
            Timber.d(TAG, "Scan completed: ${files.size} files")
            ScanResult.Success(files)
        } catch (e: CancellationException) {
            _scanState.value = ScanState.Cancelled
            Timber.d(TAG, "Scan cancelled")
            ScanResult.Cancelled
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            _scanState.value = ScanState.Error(errorMessage)
            Timber.tag(TAG).e(e, "Scan failed: $errorMessage")
            ScanResult.Error(errorMessage, e)
        }
    }

    override fun scanAsync(
        target: ScanTarget,
        force: Boolean,
        onComplete: ((ScanResult) -> Unit)?
    ) {
        currentScanJob?.cancel()
        currentScanJob = scope.launch {
            val result = scan(target, force)
            onComplete?.invoke(result)
        }
    }

    override fun cancel() {
        currentScanJob?.cancel()
        currentScanJob = null
    }

    override suspend fun syncFile(filePath: String): Result<AudioFile> {
        return try {
            Timber.d(TAG, "Syncing file to cache: $filePath")
            // Get existing cached entity to preserve mediaStoreAlbumId/ArtistId and audio properties
            val existingEntity = musicLibraryCache.getCachedFileEntity(filePath)

            // Re-scan the single file to get updated metadata
            val metadata = audioFileScanner.loadDetailedMetadata(filePath, includeAlbumArt = false)
                ?: throw IllegalStateException("Failed to read metadata for: $filePath")
            val audioInfo = audioFileScanner.loadAudioProperties(filePath)
            val fileSize = File(filePath).length()

            val audioFile = AudioFile(
                id = filePath.hashCode().toString(),
                path = filePath,
                name = filePath.substringAfterLast('/'),
                size = fileSize,
                duration = audioInfo?.durationMs ?: existingEntity?.duration ?: 0L,
                format = filePath.substringAfterLast('.', "").uppercase(),
                bitrate = audioInfo?.bitrate?.let { it / Constants.BPS_TO_KBPS } ?: existingEntity?.bitrate ?: 0,
                sampleRate = audioInfo?.sampleRate ?: existingEntity?.sampleRate ?: 0,
                channels = audioInfo?.channels ?: existingEntity?.channels ?: 0,
                mediaStoreAlbumId = existingEntity?.albumId,
                mediaStoreArtistId = existingEntity?.artistId,
                dateAdded = existingEntity?.dateAdded ?: System.currentTimeMillis() / 1000,
                metadata = metadata
            )

            // Update cache
            audioFileScanner.syncFileToCache(audioFile)

            Timber.d(TAG, "File synced to cache: $filePath")
            Result.success(audioFile)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sync file: $filePath")
            Result.failure(e)
        }
    }

    override fun startWatchingSettings() {
        // Prevent duplicate watching
        if (isWatchingSettings) {
            Timber.d(TAG, "Settings watching already active, skipping duplicate start")
            return
        }
        isWatchingSettings = true

        val minDurationFilterEnabled = settingsDataStore.minDurationFilterEnabled
            .stateIn(scope, SharingStarted.WhileSubscribed(30000), false)
        val whitelistEnabled = settingsDataStore.whitelistEnabled
            .stateIn(scope, SharingStarted.WhileSubscribed(30000), false)
        val blacklistEnabled = settingsDataStore.blacklistEnabled
            .stateIn(scope, SharingStarted.WhileSubscribed(30000), false)

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
                    scanAsync(ScanTarget.Global, force = true)
                }
                if (lastWhitelistEnabled != whitelist) {
                    lastWhitelistEnabled = whitelist
                    Timber.d(TAG, "Whitelist enabled changed, triggering refresh")
                    scanAsync(ScanTarget.Global, force = true)
                }
                if (lastBlacklistEnabled != blacklist) {
                    lastBlacklistEnabled = blacklist
                    Timber.d(TAG, "Blacklist enabled changed, triggering refresh")
                    scanAsync(ScanTarget.Global, force = true)
                }
            }
        }
    }

    override fun resetState() {
        if (_scanState.value !is ScanState.Idle) {
            _scanState.value = ScanState.Idle
        }
    }

    /**
     * Performs a global scan of all audio files on the device
     */
    private suspend fun performGlobalScan(force: Boolean): List<AudioFile> {
        // Check if we have cached data and not forcing refresh
        if (!force) {
            val cachedCount = audioFileScanner.getCachedFileCount()
            if (cachedCount > 0) {
                Timber.d(TAG, "Using cache: $cachedCount files")
                return audioFileScanner.getCachedAudioFiles().first()
            }
        }

        // Perform full scan using unified API
        return audioFileScanner.scan(
            directoryPaths = emptyList(),
            incremental = false,
            forceRefresh = force
        )
    }

    /**
     * Performs an incremental scan (only new/modified files)
     */
    private suspend fun performIncrementalScan(): List<AudioFile> {
        return audioFileScanner.scan(
            directoryPaths = emptyList(),
            incremental = true,
            forceRefresh = false
        )
    }

    /**
     * Scans specific directories only
     */
    private suspend fun performDirectoryScan(paths: List<String>, force: Boolean): List<AudioFile> {
        return audioFileScanner.scan(
            directoryPaths = paths,
            incremental = false,
            forceRefresh = force
        )
    }

    /**
     * Scans and syncs a single file to cache
     */
    private suspend fun performSingleFileScan(filePath: String): AudioFile {
        // Load detailed metadata
        val metadata = audioFileScanner.loadDetailedMetadata(filePath, includeAlbumArt = false)
            ?: throw IllegalStateException("Failed to read metadata for: $filePath")

        val audioFile = AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = filePath.substringAfterLast('/'),
            size = 0,
            duration = 0,
            format = filePath.substringAfterLast('.', "").uppercase(),
            bitrate = 0,
            sampleRate = 0,
            channels = 0,
            metadata = metadata
        )

        // Sync to cache
        audioFileScanner.syncFileToCache(audioFile)

        return audioFile
    }
}
