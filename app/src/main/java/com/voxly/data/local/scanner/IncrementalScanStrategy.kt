package com.voxly.data.local.scanner

import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Collator
import java.util.Locale
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
        private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }
    }

    override suspend fun scan(): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("IncrementalScanStrategy scan started")

        val currentFiles = mediaStoreDataSource.queryFilePathsAndModificationTimes()

        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)
        Timber.i(TAG, "Incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val retainedFiles = cachedFiles.filter { it.path !in pathsNeedingRescan }

        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            fileProcessor.scanFilesInParallel(pathsNeedingRescan)
        } else {
            emptyList()
        }

        libraryCache.cleanupDeletedFiles(currentFiles.map { it.first })

        if (updatedFiles.isEmpty()) {
            return@withContext retainedFiles
        }

        (retainedFiles + updatedFiles)
            .distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }
}
