package com.voxly.data.repository

import com.voxly.core.util.Constants
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.MediaStoreVersionCache
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.model.IncrementalList
import com.voxly.domain.repository.ChangeSource
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.ScanResult
import com.voxly.domain.repository.ScanState
import com.voxly.domain.repository.ScanTarget
import com.voxly.domain.repository.WhitelistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Real implementation of [LibraryRepository].
 *
 * Wires together [LibraryDataHolder] (event bus + refresh counter),
 * [AudioFileScanner] (data flows + scan implementation), and the unified
 * scan coordination (formerly UnifiedScanManagerImpl) behind a single
 * interface. ViewModels that inject this class depend on one abstraction
 * instead of multiple data-layer classes.
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
class LibraryRepositoryImpl @Inject constructor(
    private val libraryDataHolder: LibraryDataHolder,
    private val audioFileScanner: AudioFileScanner,
    private val musicLibraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val mediaStoreVersionCache: MediaStoreVersionCache,
    private val whitelistRepository: WhitelistRepository,
    @Named("ApplicationScope") private val scope: CoroutineScope,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
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

    override val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing
    override val scanError: SharedFlow<String> = libraryDataHolder.scanError

    // Data flows delegate to the scanner.
    // allAudios now reads the raw Room-backed StateFlow so callers see EVERY
    // cached audio file (including those without an album key). Album /
    // artist flows continue to come from the aggregator.
    override val allAudios: StateFlow<List<AudioFile>> = audioFileScanner.cachedAudioFilesStateFlow
    override val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
    override val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
    override val albumDiff: SharedFlow<IncrementalList<AlbumGroup>> = audioFileScanner.albumDiff
    override val artistDiff: SharedFlow<IncrementalList<ArtistGroup>> = audioFileScanner.artistDiff

    override fun refresh(
        forceRefresh: Boolean,
        bypassVersionCache: Boolean,
        source: ChangeSource,
    ) {
        scope.launch {
            // MediaStore version short-circuit (moved from LibraryScanViewModel):
            // if the audio collection version is unchanged since the last
            // successful scan and we already have cached data, the mtime diff
            // inside the incremental scan would be a no-op anyway. Skip the
            // whole refresh request. Skipped on force-refresh (full rescan)
            // AND on user-initiated refreshes (bypassVersionCache=true) so the
            // spinner always corresponds to a real scan attempt.
            if (!forceRefresh && !bypassVersionCache && audioFileScanner.hasCachedData()) {
                val currentVersion = mediaStoreVersionCache.current()
                val lastVersion = settingsDataStore.lastKnownMediaStoreVersion.first()
                if (lastVersion.isNotEmpty() && currentVersion == lastVersion) {
                    Timber.d(TAG, "MediaStore version unchanged ($currentVersion), skipping refresh")
                    return@launch
                }
            }
            libraryDataHolder.requestGlobalRefresh(
                forceRefresh = forceRefresh,
                bypassVersionCache = bypassVersionCache,
                source = source
            )
        }
    }

    private suspend fun scan(
        target: ScanTarget,
        force: Boolean
    ): ScanResult {
        Timber.tag("Voxly").i("LibraryRepository scan: target=$target force=$force")

        _scanState.value = ScanState.Scanning(target, 0f)
        libraryDataHolder.beginScan()

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
        } finally {
            libraryDataHolder.endScan()
        }
    }

    private fun scanAsync(
        target: ScanTarget,
        force: Boolean,
        onComplete: ((ScanResult) -> Unit)? = null
    ) {
        currentScanJob?.cancel()
        currentScanJob = scope.launch {
            val result = scan(target, force)
            onComplete?.invoke(result)
        }
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
                path = filePath,
                name = filePath.substringAfterLast('/'),
                size = fileSize,
                duration = audioInfo?.durationMs ?: existingEntity?.duration ?: 0L,
                format = AudioFormat.fromExtension(filePath.substringAfterLast('.', "")),
                bitrate = audioInfo?.bitrate?.let { it / Constants.BPS_TO_KBPS } ?: existingEntity?.bitrate ?: 0,
                sampleRate = audioInfo?.sampleRate ?: existingEntity?.sampleRate ?: 0,
                channels = audioInfo?.channels ?: existingEntity?.channels ?: 0,
                mediaStoreAlbumId = existingEntity?.albumId,
                mediaStoreArtistId = existingEntity?.artistId,
                dateAdded = existingEntity?.dateAdded ?: System.currentTimeMillis() / 1000,
                metadata = metadata
            )

            withContext(NonCancellable) {
                audioFileScanner.syncFileToCache(audioFile)
            }

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

        var isFirstEmission = true
        scope.launch {
            combine(
                minDurationFilterEnabled,
                whitelistEnabled,
                blacklistEnabled
            ) { minDuration, whitelist, blacklist ->
                Triple(minDuration, whitelist, blacklist)
            }.collect { (minDuration, whitelist, blacklist) ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    lastMinDurationFilterEnabled = minDuration
                    lastWhitelistEnabled = whitelist
                    lastBlacklistEnabled = blacklist
                    Timber.d(TAG, "Settings initial values received, skipping initial scan")
                    return@collect
                }
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

    override fun syncDirectories() {
        scope.launch {
            syncSelectedDirectoriesFromStorage()
        }
    }

    /**
     * Syncs selected directories from whitelist repository and performs incremental scan.
     * Uses filesystem paths (not URIs) from WhitelistRepository's in-memory state.
     */
    private suspend fun syncSelectedDirectoriesFromStorage() {
        val paths = whitelistRepository.getValidWhitelistPathsOnce()
        if (paths.isNotEmpty()) {
            scan(ScanTarget.Directories(paths), force = false)
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
            path = filePath,
            name = filePath.substringAfterLast('/'),
            size = 0,
            duration = 0,
            format = AudioFormat.fromExtension(filePath.substringAfterLast('.', "")),
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
