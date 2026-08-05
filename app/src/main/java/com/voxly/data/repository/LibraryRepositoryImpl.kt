package com.voxly.data.repository

import com.voxly.core.util.Constants
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharedFlow
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
    @Named("ApplicationScope") private val scope: CoroutineScope,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
    }

    // Flag to prevent duplicate settings watching
    private var isWatchingSettings = false

    // Settings change tracking (null = baseline not yet established)
    private var lastMinDurationFilterEnabled: Boolean? = null
    private var lastWhitelistEnabled: Boolean? = null
    private var lastBlacklistEnabled: Boolean? = null

    override val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing
    override val scanError: SharedFlow<String> = libraryDataHolder.scanError

    // Data flows delegate to the scanner.
    // allAudios reads the shared filtered library (filteredAllAudios) so callers
    // see exactly what the library displays — whitelist/blacklist/min-duration
    // applied, including files without an album key. Album / artist flows
    // continue to come from the aggregator.
    override val allAudios: StateFlow<List<AudioFile>> = audioFileScanner.filteredAllAudios
    override val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
    override val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists

    override fun refresh(strategy: RefreshStrategy) {
        Timber.tag("Voxly").i("DIAG refresh($strategy) from=${Thread.currentThread().stackTrace.getOrNull(4)?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }}")
        scope.launch {
            // LAZY: skip the scan entirely when cached data exists. This covers
            // cold start (no recorded MediaStore version yet → version compare
            // can't short-circuit) and resume when nothing changed. The cache is
            // already rendered by the aggregator's direct cache read; external
            // changes are caught by the MediaStore observer / SAF watcher, which
            // request INCREMENTAL.
            if (strategy == RefreshStrategy.LAZY && audioFileScanner.hasCachedData()) {
                Timber.d(TAG, "LAZY refresh with cached data, skipping scan")
                return@launch
            }
            libraryDataHolder.requestGlobalRefresh(strategy)
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

        // Use the raw DataStore flows (first emission IS the stored value, no
        // stateIn `false` sentinel). Baseline = first combined emission; only
        // changes after that baseline trigger a FORCE refresh.
        val minDurationFilterEnabled = settingsDataStore.minDurationFilterEnabled
        val whitelistEnabled = settingsDataStore.whitelistEnabled
        val blacklistEnabled = settingsDataStore.blacklistEnabled

        scope.launch {
            combine(
                minDurationFilterEnabled,
                whitelistEnabled,
                blacklistEnabled
            ) { minDuration, whitelist, blacklist ->
                Triple(minDuration, whitelist, blacklist)
            }.collect { (minDuration, whitelist, blacklist) ->
                if (lastWhitelistEnabled == null) {
                    // First combined emission = baseline.
                    lastMinDurationFilterEnabled = minDuration
                    lastWhitelistEnabled = whitelist
                    lastBlacklistEnabled = blacklist
                    return@collect
                }
                // Check and trigger refresh for each setting that changed.
                // Routed through refresh() so settings changes take the same
                // event-bus path as every other trigger (merge window, version
                // short-circuit) instead of a parallel scan.
                if (lastMinDurationFilterEnabled != minDuration) {
                    lastMinDurationFilterEnabled = minDuration
                    Timber.d(TAG, "Min duration filter changed, triggering refresh")
                    refresh(RefreshStrategy.FORCE)
                }
                if (lastWhitelistEnabled != whitelist) {
                    lastWhitelistEnabled = whitelist
                    Timber.d(TAG, "Whitelist enabled changed, triggering refresh")
                    refresh(RefreshStrategy.FORCE)
                }
                if (lastBlacklistEnabled != blacklist) {
                    lastBlacklistEnabled = blacklist
                    Timber.d(TAG, "Blacklist enabled changed, triggering refresh")
                    refresh(RefreshStrategy.FORCE)
                }
            }
        }
    }
}
