package com.voxly.data.local.scanner

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.saf.SafWriteAccessService
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
    private val fileProcessor: FileProcessor,
    private val fastScanProcessor: FastScanProcessor,
    private val safWriteAccessService: SafWriteAccessService
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

        return if (forceRefresh) {
            scanDirectoriesFull(normalizedDirs)
        } else if (incremental) {
            scanDirectoriesIncremental(normalizedDirs)
        } else {
            scanDirectoriesFull(normalizedDirs)
        }
    }

    private suspend fun scanDirectoriesFull(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("DirectoryScanStrategy scanDirectoriesFull: dirs=${directoryPaths.size}")
        directoryPaths.flatMap { dir ->
            scanDirectoryInternal(dir)
        }.distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    private suspend fun scanDirectoriesIncremental(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("DirectoryScanStrategy scanDirectoriesIncremental: dirs=${directoryPaths.size}")
        val currentFiles = mutableListOf<Pair<String, Long>>()
        directoryPaths.forEach { dir ->
            val dirFile = File(dir)
            val listed = mediaStoreDataSource.queryDirectoryFilePathsAndModificationTimes(dirFile)
            if (listed.isEmpty()) {
                val safResult = mediaStoreDataSource.queryDirectoryViaSaf(dir)
                currentFiles.addAll(safResult)
            } else {
                currentFiles.addAll(listed)
            }
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

        val newPaths = currentPaths.filter { path -> cachedInDirs.none { it.path == path } }
        val newFiles = if (newPaths.isNotEmpty()) {
            fileProcessor.scanFilesInParallel(newPaths.toList())
        } else {
            emptyList()
        }

        val allFiles = (retainedFiles + updatedFiles + newFiles)
            .distinctBy { it.path }

        if (updatedFiles.isNotEmpty() || newFiles.isNotEmpty()) {
            libraryCache.updateCache(allFiles)
        }

        allFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    private suspend fun scanDirectoryInternal(
        directoryPath: String
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val normalizedDir = directoryPath.trimEnd('/', '\\')

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val relativeDir = mediaStoreDataSource.getRelativePathFromAbsolute(normalizedDir)
        val audioFiles = if (relativeDir != null) {
            val storeFiles = mediaStoreDataSource.queryFromDirectory(relativeDir, minDurationEnabled, minDurationMs)
            fastScanProcessor.enrichAll(storeFiles)
        } else {
            emptyList()
        }

        if (audioFiles.isEmpty()) {
            val dir = File(directoryPath)
            if (dir.exists() && dir.isDirectory) {
                val listed = mediaStoreDataSource.scanDirectoryRecursive(dir)
                if (listed.isEmpty()) {
                    val safFiles = mediaStoreDataSource.scanDirectoryViaSaf(directoryPath)
                    if (safFiles.isNotEmpty()) {
                        return@withContext safFiles
                    }
                }
                if (listed.isEmpty()) {
                    Timber.w(TAG, "Both File.listFiles() and SAF scan returned empty for $directoryPath")
                }
                return@withContext listed
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
