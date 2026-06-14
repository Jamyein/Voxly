package com.voxly.data.local

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O(1) cache-validity probe for the MediaStore audio collection.
 *
 * Reads [MediaStore.getVersion], an opaque string token bumped by the system
 * whenever ANY audio row in the collection changes. Persisting the last-seen
 * version and comparing on the next call lets us skip the mtime-diffing
 * `queryFilePathsAndModificationTimes` call entirely when nothing has changed.
 */
@Singleton
class MediaStoreVersionCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the current MediaStore audio-collection version string.
     * Empty string if unavailable (defensive fallback).
     */
    suspend fun current(): String = withContext(Dispatchers.IO) {
        MediaStore.getVersion(context)
    }
}
