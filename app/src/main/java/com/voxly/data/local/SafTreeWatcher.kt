package com.voxly.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
 * against the last-seen count. When the count differs, triggers
 * [LibraryDataHolder.requestRefresh] .
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
     * Walk all user-selected SAF directories, detect changes, and trigger
     * refresh if any directory's file count changed since last check.
     *
     * Called from [MediaStoreChangeWatcher] after the debounced observer
     * fires (application-scoped coroutine).
     */
    suspend fun detectChanges(dirUris: List<String>) = withContext(Dispatchers.IO) {
        if (dirUris.isEmpty()) return@withContext
        var changed = false

        for (uriString in dirUris) {
            if (!uriString.startsWith("content://")) continue
            val uri = Uri.parse(uriString)
            val tree = DocumentFile.fromTreeUri(context, uri) ?: continue

            val currentCount = countFiles(tree)
            val previousCount = fileCountCache[uriString] ?: -1

            if (previousCount >= 0 && currentCount != previousCount) {
                Timber.tag(TAG).i("SAF dir changed: $uriString ($previousCount → $currentCount files)")
                changed = true
            }

            fileCountCache[uriString] = currentCount
        }

        if (changed) {
            libraryDataHolder.requestRefresh(forceRefresh = false)
            Timber.tag(TAG).i("SAF tree change detected, refresh requested")
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
