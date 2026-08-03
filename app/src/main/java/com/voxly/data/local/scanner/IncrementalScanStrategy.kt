package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class IncrementalScanStrategy @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val fileProcessor: FileProcessor
) : ScanStrategy {
    companion object {
        private const val TAG = "IncrementalScanStrategy"
        private const val PROGRESS_BATCH_SIZE = 100
        private val chineseCollator = SortUtil.chineseCollator
    }

    override suspend fun scan(): List<AudioFile> = scan(onProgress = {})

    /**
     * Progressive overload: [onProgress] is called with intermediate batches
     * as files complete scanning, before the final sorted list is returned.
     * The caller ([AudioFileScanner]) drives [MusicLibraryCache.updateCache]
     * from these callbacks, so the Room cache (and therefore the UI) receives
     * data in stages rather than all at once.
     *
     * Returns raw in-scope audio files; filtering happens at read stage in
     * [AlbumArtistAggregator.filteredAllAudios].
     */
    suspend fun scan(
        onProgress: suspend (List<AudioFile>) -> Unit
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("IncrementalScanStrategy scan started")

        // 1. Determine the last scan timestamp. The Room cache stores
        //    System.currentTimeMillis(); MediaStore DATE_MODIFIED is in
        //    seconds, so divide by 1000 for the WHERE clause.
        val lastScanTime = libraryCache.getLastScanTime()
        val lastScanTimeSecs = if (lastScanTime != null && lastScanTime > 0L) {
            lastScanTime / 1000L
        } else 0L

        // 2. Query only files changed since the last scan (O(M) instead of O(N)).
        //    When lastScanTime is 0 (no cached scan), falls back to the full query.
        val currentFiles = mediaStoreDataSource.queryFilesChangedSince(lastScanTimeSecs)
        val currentPaths = currentFiles.map { it.first }.toSet()
        Timber.i(TAG, "Incremental scan: ${currentFiles.size} files changed since last scan")

        // 3. Load cached files and separate retained vs changed.
        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val retainedFiles = cachedFiles.filter { it.path !in currentPaths }

        // 4. Re-scan changed files in parallel, emitting progress batches.
        //    Each batch is written to the cache immediately (by the caller)
        //    so the aggregator rebuilds incrementally and the UI sees results
        //    in stages — "progressive scan results".
        val updatedFiles = if (currentFiles.isNotEmpty()) {
            val scanned = fileProcessor.scanFilesInParallel(currentFiles.map { it.first })
            scanned.chunked(PROGRESS_BATCH_SIZE).forEach { batch -> onProgress(batch) }
            scanned
        } else {
            emptyList()
        }

        // 5. Deletion detection: files removed from MediaStore since the
        //    last scan must be purged from both the Room cache and the
        //    in-memory result, or updateCache will re-upsert them.
        //
        //    Uses a lightweight path-only query (2 columns, ~30ms for 10k
        //    files on modern hardware) and a single DELETE WHERE NOT IN.
        val allCurrentPaths = mediaStoreDataSource.queryAllPaths()
        libraryCache.cleanupDeletedFiles(allCurrentPaths.toList())
        val validPathsSet = allCurrentPaths
        val retained = retainedFiles.filter { it.path in validPathsSet }
        settingsDataStore.setLastKnownFileCount(allCurrentPaths.size)

        if (updatedFiles.isEmpty()) {
            return@withContext retained
        }

        (retained + updatedFiles)
            .distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }
}
