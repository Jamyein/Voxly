package com.voxly.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.voxly.core.util.PathUtils
import com.voxly.domain.repository.ChangeSource
import com.voxly.domain.repository.LibraryDataHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects changes in SAF-picked directories (e.g. USB drives, SD card
 * subdirectories) that MediaStore's ContentObserver does NOT cover.
 *
 * Uses a simple file-count heuristic: walk the tree, count files, compare
 * against the last-seen count. When the count differs, emits a targeted
 * [LibraryChangeEvent.Directory] for that tree so the library updates just
 * that directory (merged with any concurrent directory changes).
 *
 * SAF tree observation is inherently poll-based (no inotify for content
 * URIs). Uses an in-memory [WeakHashMap] for the count cache — lost on
 * process death but that's acceptable since the next check will re-walk.
 */
@Singleton
class SafTreeWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDataHolder: LibraryDataHolder,
) {
    /**
     * Walk all user-selected SAF directories, detect changes, and emit a
     * directory refresh for any directory whose file count changed since
     * the last check.
     *
     * Called from [MediaStoreChangeWatcher] after the debounced observer
     * fires (application-scoped coroutine).
     */
    suspend fun detectChanges(dirUris: List<String>) = withContext(Dispatchers.IO) {
        if (dirUris.isEmpty()) return@withContext
        val changedDirs = mutableListOf<Pair<String, String>>() // uri to path

        for (uriString in dirUris) {
            if (!uriString.startsWith("content://")) continue
            val uri = Uri.parse(uriString)
            val tree = DocumentFile.fromTreeUri(context, uri) ?: continue

            val currentCount = countFiles(tree)
            val previousCount = fileCountCache[uriString] ?: -1

            if (previousCount >= 0 && currentCount != previousCount) {
                Timber.tag(TAG).i("SAF dir changed: $uriString ($previousCount → $currentCount files)")
                changedDirs.add(uriString to PathUtils.getPathFromUri(uri))
            }

            fileCountCache[uriString] = currentCount
        }

        changedDirs.forEach { (uri, path) ->
            libraryDataHolder.requestDirectoryRefresh(
                directoryUri = uri,
                directoryPath = path,
                forceRefresh = false,
                source = ChangeSource.SAF_TREE
            )
        }
        if (changedDirs.isNotEmpty()) {
            Timber.tag(TAG).i("SAF tree change detected, ${changedDirs.size} dir(s) refresh requested")
        }
    }

    private fun countFiles(tree: DocumentFile): Int {
        var count = 0
        for (child in tree.listFiles()) {
            when {
                child.isDirectory -> count += countFiles(child)
                child.isFile -> count++
            }
        }
        return count
    }

    companion object {
        private const val TAG = "SafTreeWatcher"

        /**
         * In-memory cache of last-seen file counts per URI.
         * Weak values — cleared on GC pressure, but good enough for
         * the typical "check every few minutes" cadence.
         */
        private val fileCountCache = WeakHashMap<String, Int>()
    }
}
