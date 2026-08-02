package com.voxly.data.local.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.scanner.MediaStoreDataSource
import com.voxly.domain.repository.ChangeSource
import com.voxly.domain.repository.LibraryDataHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic background library scan worker.
 *
 * Scheduled with `charging + batteryNotLow` constraints so it only runs when
 * the device is plugged in — no battery cost to the user.
 *
 * Responsibilities (in order):
 * 1. Trigger an incremental scan via [LibraryDataHolder.requestRefresh].
 * 2. Purge cached entries for files that have been deleted from MediaStore.
 *    This is the primary cleanup path; the incremental scan path defers
 *    deletion detection here.
 * 3. (Future) SAF tree change detection via DocumentFile.lastModified.
 */
@HiltWorker
class LibraryPeriodicScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val libraryDataHolder: LibraryDataHolder,
    private val musicLibraryCache: MusicLibraryCache,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).i("Periodic scan started")
        return try {
            // 1. Trigger an incremental scan. The scan path skips deletion
            //    detection; we do it here afterward.
            libraryDataHolder.requestGlobalRefresh(
                forceRefresh = false,
                bypassVersionCache = false,
                source = ChangeSource.PERIODIC_WORKER
            )

            // 2. Deletion cleanup: fast path-only query to find files no
            //    longer known to MediaStore, then purge from cache.
            val allPaths = mediaStoreDataSource.queryAllPaths()
            val deleted = musicLibraryCache.cleanupDeletedFiles(allPaths.toList())
            if (deleted > 0) {
                Timber.tag(TAG).i("Purged $deleted deleted files from cache")
            }
            settingsDataStore.setLastKnownFileCount(allPaths.size)

            // TODO(phase-3b): SAF tree change detection via DocumentFile
            // lastModified comparison against persisted snapshot.

            Timber.tag(TAG).i("Periodic scan completed")
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Periodic scan failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PeriodicScanWorker"
        const val UNIQUE_WORK_NAME = "library_periodic_scan"
        const val INTERVAL_HOURS = 6L
    }
}
