package com.voxly.data.local.scanner

import android.content.Context
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast scan processor - first pass of two-pass scanning.
 * Only reads MediaStore text metadata for instant app startup.
 * Skips cover art and detailed audio properties.
 */
@Singleton
class FastScanProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "FastScanProcessor"
    }

    /**
     * Performs fast scan - reads only essential metadata from MediaStore.
     * Returns minimal AudioFile for instant display.
     */
    suspend fun fastScan(files: List<Pair<String, Long>>): List<AudioFile> = withContext(Dispatchers.IO) {
        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        files.mapNotNull { (path, _) ->
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) return@mapNotNull null

                val extension = file.extension.lowercase()
                val (duration, bitrate) = mediaStoreDataSource.queryFileDurationAndBitrate(path)

                AudioFile(
                    id = path.hashCode().toString(),
                    path = path,
                    name = file.name,
                    size = file.length(),
                    duration = duration,
                    format = extension.uppercase(),
                    bitrate = bitrate,
                    sampleRate = 0,
                    channels = 0,
                    metadata = AudioMetadata()
                )
            } catch (e: Exception) {
                Timber.w(TAG, "Fast scan failed: $path", e)
                null
            }
        }
    }
}