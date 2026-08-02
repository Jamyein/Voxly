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
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    @Named("ApplicationScope") private val scope: CoroutineScope,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
    }

    // Flag to prevent duplicate settings watching
    private var isWatchingSettings = false

    // Settings change tracking
    private var lastMinDurationFilterEnabled = false
    private var lastWhitelistEnabled = false
    private var lastBlacklistEnabled = false

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
                bypassVersionCache = bypassVersionCache
            )
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
                // Check and trigger refresh for each setting that changed.
                // Routed through refresh() so settings changes take the same
                // event-bus path as every other trigger (merge window, version
                // short-circuit) instead of a parallel scan.
                if (lastMinDurationFilterEnabled != minDuration) {
                    lastMinDurationFilterEnabled = minDuration
                    Timber.d(TAG, "Min duration filter changed, triggering refresh")
                    refresh(forceRefresh = true, bypassVersionCache = true)
                }
                if (lastWhitelistEnabled != whitelist) {
                    lastWhitelistEnabled = whitelist
                    Timber.d(TAG, "Whitelist enabled changed, triggering refresh")
                    refresh(forceRefresh = true, bypassVersionCache = true)
                }
                if (lastBlacklistEnabled != blacklist) {
                    lastBlacklistEnabled = blacklist
                    Timber.d(TAG, "Blacklist enabled changed, triggering refresh")
                    refresh(forceRefresh = true, bypassVersionCache = true)
                }
            }
        }
    }
}
