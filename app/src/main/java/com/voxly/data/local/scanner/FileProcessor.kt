package com.voxly.data.local.scanner

import com.voxly.core.util.Constants
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileProcessor @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val metadataProcessor: com.voxly.data.local.metadata.TagLibMetadataProcessor
) {
    companion object {
        private const val TAG = "FileProcessor"
    }

    suspend fun createAudioFileFromPath(filePath: String): AudioFile = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        val (duration, bitrate) = mediaStoreDataSource.queryFileDurationAndBitrate(filePath)

        val lightweightResult = LightweightMetadataParser.parse(file)
        val fullMetadata = if (lightweightResult != null) {
            lightweightResult.metadata
        } else {
            metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false)?.metadata
                ?: AudioMetadata()
        }

        val audioInfo = lightweightResult?.audioInfo ?: metadataProcessor.readAudioInfo(filePath)
        val finalDuration = if (duration == 0L) audioInfo?.durationMs ?: 0L else duration
        val finalBitrate = if (bitrate == 0) (audioInfo?.bitrate ?: 0) / Constants.BPS_TO_KBPS else bitrate

        AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = finalDuration,
            format = extension.uppercase(),
            bitrate = finalBitrate,
            sampleRate = audioInfo?.sampleRate ?: 0,
            channels = audioInfo?.channels ?: 0,
            metadata = fullMetadata
        )
    }

    suspend fun scanFilesInParallel(
        filePaths: List<String>,
        maxConcurrency: Int = 4
    ): List<AudioFile> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        filePaths
            .sortedBy { File(it).length() }
            .map { path ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            createAudioFileFromPath(path)
                        } catch (e: Exception) {
                            Timber.w(TAG, "Failed to scan: $path", e)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
    }
}
