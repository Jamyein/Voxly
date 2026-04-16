package com.voxly.data.local.scanner

import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

class DirectoryScanStrategy @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val fileProcessor: FileProcessor
) : ScanStrategy {
    companion object {
        private const val TAG = "DirectoryScanStrategy"
        private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }
    }

    suspend fun scanDirectories(
        directoryPaths: List<String>,
        incremental: Boolean,
        forceRefresh: Boolean
    ): List<AudioFile> {
        val normalizedDirs = directoryPaths
            .map { it.trimEnd('/', '\\') }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedDirs.isEmpty()) return emptyList()

        return if (incremental) {
            scanDirectoriesIncremental(normalizedDirs)
        } else {
            scanDirectoriesFull(normalizedDirs)
        }
    }

    private suspend fun scanDirectoriesFull(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        directoryPaths.flatMap { dir ->
            scanDirectoryInternal(dir)
        }.distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    private suspend fun scanDirectoriesIncremental(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val currentFiles = mutableListOf<Pair<String, Long>>()
        directoryPaths.forEach { dir ->
            val dirFile = File(dir)
            currentFiles.addAll(mediaStoreDataSource.queryDirectoryFilePathsAndModificationTimes(dirFile))
        }

        val currentPaths = currentFiles.map { it.first }.toSet()
        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)

        Timber.i(TAG, "Directory incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val cachedInDirs = cachedFiles.filter { cached ->
            directoryPaths.any { mediaStoreDataSource.isPathInsideDirectory(cached.path, it) }
        }

        val retainedFiles = cachedInDirs.filter { cached ->
            cached.path !in pathsNeedingRescan && cached.path in currentPaths
        }

        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            fileProcessor.scanFilesInParallel(pathsNeedingRescan)
        } else {
            emptyList()
        }

        val deletedPaths = cachedInDirs.map { it.path }.filter { it !in currentPaths }
        libraryCache.removeFromCache(deletedPaths)

        if (updatedFiles.isEmpty()) {
            return@withContext retainedFiles
        }

        (retainedFiles + updatedFiles)
            .distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    private suspend fun scanDirectoryInternal(
        directoryPath: String
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val normalizedDir = directoryPath.trimEnd('/', '\\')

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val relativeDir = mediaStoreDataSource.getRelativePathFromAbsolute(normalizedDir)
        val audioFiles = if (relativeDir != null) {
            mediaStoreDataSource.queryFromDirectory(relativeDir, minDurationEnabled, minDurationMs)
        } else {
            emptyList()
        }

        if (audioFiles.isEmpty()) {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                mediaStoreDataSource.scanDirectoryRecursive(dir)
            } else {
                emptyList()
            }
        } else {
            audioFiles
        }
    }

    override suspend fun scan(): List<AudioFile> {
        throw UnsupportedOperationException("Use scanDirectories() instead")
    }
}
