package com.voxly.domain.usecase

import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.scanner.FileProcessor
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RebuildDatabaseManagerImpl @Inject constructor(
    private val musicLibraryCache: MusicLibraryCache,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val fileProcessor: FileProcessor,
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

            // Rebuild the raw cache (all in-scope audio). Whitelist/blacklist/
            // min-duration are applied by the read-stage filteredAllAudios flow,
            // so the cache stays a complete superset for instant filter toggles.
            val files = mediaStoreDataSource.queryAll()
            Timber.d(TAG, "Found ${files.size} audio files from MediaStore")

            val totalCount = files.size
            if (totalCount == 0) {
                _state.value = RebuildDatabaseState.Completed(0, 0)
                return
            }

            val enriched = mutableListOf<AudioFile>()
            val batchSize = 500
            var processedCount = 0
            var lastProgressUpdate = 0L

            for ((index, audioFile) in files.withIndex()) {
                try {
                    val enrichedFile = fileProcessor.createAudioFileFromPath(audioFile.path)
                    enriched.add(enrichedFile)
                    processedCount++

                    // Batch write every batchSize files, or at the end
                    if (enriched.size >= batchSize || index == totalCount - 1) {
                        musicLibraryCache.updateCache(enriched.toList())
                        enriched.clear()
                    }

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