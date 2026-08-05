package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DirectoryScanStrategy @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val libraryCache: MusicLibraryCache,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val fileProcessor: FileProcessor,
    private val fastScanProcessor: FastScanProcessor,
    private val safWriteAccessService: SafWriteAccessService
) : ScanStrategy {
    companion object {
        private const val TAG = "DirectoryScanStrategy"
        private val chineseCollator = SortUtil.chineseCollator
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

        // forceRefresh forces a full re-read; otherwise incremental skips
        // unchanged files. A non-incremental, non-forced request is a full scan.
        return if (incremental && !forceRefresh) {
            scanDirectoriesIncremental(normalizedDirs)
        } else {
            scanDirectoriesFull(normalizedDirs)
        }
    }

    private suspend fun scanDirectoriesFull(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("DirectoryScanStrategy scanDirectoriesFull: dirs=${directoryPaths.size}")
        directoryPaths.filter { kotlinx.coroutines.currentCoroutineContext().isActive }.flatMap { dir ->
            scanDirectoryInternal(dir)
        }.distinctBy { it.path }
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    private suspend fun scanDirectoriesIncremental(
        directoryPaths: List<String>
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("DirectoryScanStrategy scanDirectoriesIncremental: dirs=${directoryPaths.size}")
        val currentFiles = mutableListOf<Pair<String, Long>>()
        // Directories whose tree was actually enumerated. An inaccessible dir
        // (scoped-storage File-walk failure, missing SAF tree URI) contributes
        // nothing to currentFiles and must NOT count as "empty" — purging its
        // cached files would delete valid entries. Lesson #24.
        val accessibleDirs = mutableSetOf<String>()

        // Single full-table mtime projection, reused by the MediaStore
        // completeness check below AND getFilesNeedingRescan — never loaded
        // twice per scan.
        val cachedPathsWithMtimes = libraryCache.getCachedPathsWithModificationTimes()

        directoryPaths.forEach { dir ->
            val dirFile = File(dir)
            // MediaStore-first: the audio table is indexed by RELATIVE_PATH, so
            // this resolves a few-hundred-file tree in ~50ms instead of the
            // ~1.9s recursive File stat walk that dominated cold-start scans
            // (it stats every directory entry, including non-audio files, on
            // the UI-critical path).
            var listing = mediaStoreDataSource.queryDirectoryPathsAndMtimesViaMediaStore(dirFile)
            // Completeness guard (lesson #24): MediaStore must not miss files
            // that exist on disk. If any cached path inside this dir is absent
            // from the MediaStore result AND still exists on the filesystem,
            // MediaStore is not indexing part of the tree (USB/SD volumes,
            // unindexed files) — purge against this result would delete valid
            // entries. Fall back to the authoritative File walk.
            if (listing != null && mediaStoreListingMissesExistingFiles(listing, dir, cachedPathsWithMtimes)) {
                listing = null
            }
            if (listing == null) {
                listing = mediaStoreDataSource.queryDirectoryFilePathsAndModificationTimes(dirFile)
            }
            if (listing.accessible) {
                currentFiles.addAll(listing.files)
                accessibleDirs.add(dir)
            } else {
                val safListing = mediaStoreDataSource.queryDirectoryViaSaf(dir)
                if (safListing.accessible) {
                    currentFiles.addAll(safListing.files)
                    accessibleDirs.add(dir)
                } else {
                    Timber.w(TAG, "Directory inaccessible (File walk + SAF both failed), skipping: $dir")
                }
            }
        }

        val currentPaths = currentFiles.map { it.first }.toSet()
        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles, cachedPathsWithMtimes)

        Timber.i(TAG, "Directory incremental scan: ${pathsNeedingRescan.size} files need rescanning")

        val cachedFiles = libraryCache.getCachedAudioFilesOnce()
        val cachedInDirs = cachedFiles.filter { cached ->
            directoryPaths.any { mediaStoreDataSource.isPathInsideDirectory(cached.path, it) }
        }

        // Only files under accessible dirs may be purged (see accessibleDirs).
        val purgeablePaths = cachedInDirs.filter { cached ->
            accessibleDirs.any { mediaStoreDataSource.isPathInsideDirectory(cached.path, it) }
        }

        val retainedFiles = cachedInDirs.filter { cached ->
            cached.path !in pathsNeedingRescan && cached.path in currentPaths
        }

        // pathsNeedingRescan already covers BOTH new (uncached) and modified
        // files — getFilesNeedingRescan returns cached == null || mtime differs.
        // A separate newFiles pass would re-read the same paths a second time
        // (R4 double-scan on first whitelist setup). One pass is complete:
        // every file on disk is either retained (cached & unchanged) or here.
        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            fileProcessor.scanFilesInParallel(pathsNeedingRescan)
        } else {
            emptyList()
        }

        val deletedPaths = purgeablePaths.map { it.path }.filter { it !in currentPaths }
        if (deletedPaths.isNotEmpty()) {
            libraryCache.removeFromCache(deletedPaths)
        }

        val allFiles = (retainedFiles + updatedFiles)
            .distinctBy { it.path }

        // Only persist changed files. Retained (unchanged) files are already in
        // the DB; rewriting them would churn every FTS row via INSERT OR REPLACE.
        if (updatedFiles.isNotEmpty()) {
            libraryCache.updateCache(updatedFiles)
        }

        allFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }

    /**
     * True when the MediaStore enumeration is missing cached files that still
     * exist on disk inside [dir]. MediaStore does not index every tree (USB/SD
     * volumes, files never scanned into the provider), so a result that omits a
     * live cached path means the provider is incomplete — using it would purge
     * valid entries (lesson #24). The check is cheap in the common case: only
     * cached paths inside this dir that are absent from the MediaStore result
     * are stat'ed, and that set is empty when the provider is complete.
     */
    private fun mediaStoreListingMissesExistingFiles(
        listing: com.voxly.data.local.scanner.MediaStoreDataSource.DirectoryFileListing,
        dir: String,
        cachedPathsWithMtimes: List<Pair<String, Long>>
    ): Boolean {
        val mediaStorePaths = listing.files.mapTo(java.util.HashSet()) { it.first }
        return cachedPathsWithMtimes.any { (path, _) ->
            mediaStoreDataSource.isPathInsideDirectory(path, dir) &&
                path !in mediaStorePaths &&
                File(path).exists()
        }
    }

    private suspend fun scanDirectoryInternal(
        directoryPath: String
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val normalizedDir = directoryPath.trimEnd('/', '\\')

        val relativeDir = mediaStoreDataSource.getRelativePathFromAbsolute(normalizedDir)
        val audioFiles = if (relativeDir != null) {
            val storeFiles = mediaStoreDataSource.queryFromDirectory(relativeDir)
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
