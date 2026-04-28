package com.voxly.domain.usecase

import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.scanner.FileProcessor
import com.voxly.data.local.scanner.FilterEngine
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.WhitelistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RebuildDatabaseManagerImpl @Inject constructor(
    private val musicLibraryCache: MusicLibraryCache,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val filterEngine: FilterEngine,
    private val fileProcessor: FileProcessor,
    private val settingsDataStore: SettingsDataStore,
    private val whitelistRepository: WhitelistRepository,
    @Named("io") private val ioDispatcher: CoroutineDispatcher
) : RebuildDatabaseManager {

    private val _state = MutableStateFlow<RebuildDatabaseState>(RebuildDatabaseState.Idle)
    override val rebuildState: StateFlow<RebuildDatabaseState> = _state.asStateFlow()

    companion object {
        private const val TAG = "RebuildDatabaseManager"
        private const val PROGRESS_UPDATE_THRESHOLD = 10
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }

    override suspend fun rebuild() {
        Timber.tag("Voxly").i("RebuildDatabaseManager: rebuild started")

        val startTime = System.currentTimeMillis()

        try {
            _state.value = RebuildDatabaseState.InProgress(0f, null, 0)

            musicLibraryCache.clearCache()
            Timber.d(TAG, "Cache cleared")

            val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
            val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

            val files = mediaStoreDataSource.queryAll(minDurationEnabled, minDurationMs)
            Timber.d(TAG, "Found ${files.size} audio files from MediaStore")

            val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
            val whitelistPaths = if (whitelistEnabled) {
                whitelistRepository.getValidWhitelistPathsOnce()
            } else emptyList()

            val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
            val blacklistPaths = if (blacklistEnabled) {
                whitelistRepository.getValidBlacklistPathsOnce()
            } else emptyList()

            val filteredFiles = filterEngine.applyFilters(
                files,
                FilterEngine.FilterSettings(
                    whitelistEnabled = whitelistEnabled && whitelistPaths.isNotEmpty(),
                    blacklistEnabled = blacklistEnabled && blacklistPaths.isNotEmpty(),
                    minDurationEnabled = minDurationEnabled,
                    whitelistUris = whitelistPaths,
                    blacklistUris = blacklistPaths,
                    minDurationMs = minDurationMs
                )
            )
            Timber.d(TAG, "After filtering: ${filteredFiles.size} files")

            val totalCount = filteredFiles.size
            var processedCount = 0
            var lastProgressUpdate = 0L

            filteredFiles.forEachIndexed { index, audioFile ->
                try {
                    val enrichedFile = fileProcessor.createAudioFileFromPath(audioFile.path)
                    musicLibraryCache.syncFileToCache(enrichedFile)

                    processedCount++
                    val now = System.currentTimeMillis()
                    if (processedCount == totalCount ||
                        index % PROGRESS_UPDATE_THRESHOLD == 0 ||
                        now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {

                        lastProgressUpdate = now
                        _state.value = RebuildDatabaseState.InProgress(
                            progress = processedCount.toFloat() / totalCount,
                            currentFile = audioFile.path,
                            scannedCount = processedCount
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(TAG, "Failed to process file: ${audioFile.path}", e)
                }
            }

            val duration = System.currentTimeMillis() - startTime
            _state.value = RebuildDatabaseState.Completed(totalCount, duration)
            Timber.d(TAG, "Rebuild completed: $totalCount files in ${duration}ms")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Rebuild failed")
            _state.value = RebuildDatabaseState.Error(e.message ?: "Unknown error")
        }
    }
}