package com.voxly.data.local.scanner

import com.voxly.core.util.Constants
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
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

        // Single MediaStore round-trip instead of 3 separate queries
        val msData = mediaStoreDataSource.queryAllMediaStoreFields(filePath)
        val mediaStoreMetadata = if (msData != null) {
            AudioMetadata(
                title = msData.title,
                artist = msData.artist,
                album = msData.album,
                year = msData.year,
                trackNumber = msData.trackNumber,
                totalTracks = msData.totalTracks
            )
        } else AudioMetadata()
        val duration = msData?.duration ?: 0L
        val bitrate = msData?.bitrate ?: 0
        val mediaStoreAlbumId = msData?.albumId

        val lightweightResult = LightweightMetadataParser.parse(file)
        val fullMetadata = when {
            lightweightResult != null -> {
                val merged = mergeMetadata(lightweightResult.metadata, mediaStoreMetadata)
                merged
            }
            else -> metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false)?.metadata
                ?: mediaStoreMetadata
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
            format = AudioFormat.fromExtension(extension),
            bitrate = finalBitrate,
            sampleRate = audioInfo?.sampleRate ?: 0,
            channels = audioInfo?.channels ?: 0,
            mediaStoreAlbumId = mediaStoreAlbumId,
            metadata = fullMetadata
        )
    }

    private fun mergeMetadata(
        primary: AudioMetadata,
        fallback: AudioMetadata
    ): AudioMetadata {
        return primary.copy(
            title = primary.title.takeIf { !it.isNullOrBlank() } ?: fallback.title,
            artist = primary.artist.takeIf { !it.isNullOrBlank() } ?: fallback.artist,
            album = primary.album.takeIf { !it.isNullOrBlank() } ?: fallback.album,
            albumArtist = primary.albumArtist?.takeIf { it.isNotBlank() } ?: fallback.albumArtist?.takeIf { it.isNotBlank() },
            year = primary.year.takeIf { !it.isNullOrBlank() } ?: fallback.year,
            genre = primary.genre.takeIf { !it.isNullOrBlank() } ?: fallback.genre,
            trackNumber = primary.trackNumber ?: fallback.trackNumber,
            totalTracks = primary.totalTracks ?: fallback.totalTracks,
            discNumber = primary.discNumber ?: fallback.discNumber,
            totalDiscs = primary.totalDiscs ?: fallback.totalDiscs,
            composer = primary.composer.takeIf { !it.isNullOrBlank() } ?: fallback.composer
        )
    }

    suspend fun scanFilesInParallel(
        filePaths: List<String>,
        maxConcurrency: Int = 4
    ): List<AudioFile> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        filePaths
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
            }.awaitAll().filterNotNull().also { result ->
                Timber.tag("Voxly").i("FileProcessor scanFilesInParallel: processed=${result.size} total=${filePaths.size}")
            }
    }
}
