package com.voxly.data.local.scanner

import android.content.Context
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
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
     * Enriches a list of AudioFiles (already populated with MediaStore system data)
     * by parsing metadata via [LightweightMetadataParser] for all files.
     *
     * Preserves MediaStore-specific fields such as [mediaStoreAlbumId], [dateAdded],
     * [duration] and [bitrate]. Metadata fields are overwritten only when the
     * lightweight parser returns a non-null value.
     */
    suspend fun enrichAll(audioFiles: List<AudioFile>): List<AudioFile> = withContext(Dispatchers.IO) {
        audioFiles.map { audioFile ->
            try {
                val file = File(audioFile.path)
                if (!file.exists() || !file.canRead()) return@map audioFile

                val lightweightResult = LightweightMetadataParser.parse(file)
                val mediaStoreAlbumId = audioFile.mediaStoreAlbumId
                    ?: mediaStoreDataSource.queryMediaStoreAlbumId(audioFile.path)

                val effectiveYear = when {
                    lightweightResult != null && !lightweightResult.metadata.year.isNullOrBlank() -> lightweightResult.metadata.year
                    !audioFile.metadata.year.isNullOrBlank() -> audioFile.metadata.year
                    else -> mediaStoreDataSource.queryYearFromMediaStore(audioFile.path)
                }

                audioFile.copy(
                    sampleRate = lightweightResult?.audioInfo?.sampleRate ?: audioFile.sampleRate,
                    channels = lightweightResult?.audioInfo?.channels ?: audioFile.channels,
                    mediaStoreAlbumId = mediaStoreAlbumId,
                    metadata = audioFile.metadata.copy(
                        title = lightweightResult?.metadata?.title ?: audioFile.metadata.title,
                        artist = lightweightResult?.metadata?.artist ?: audioFile.metadata.artist,
                        album = lightweightResult?.metadata?.album ?: audioFile.metadata.album,
                        albumArtist = lightweightResult?.metadata?.albumArtist ?: audioFile.metadata.albumArtist,
                        year = effectiveYear,
                        genre = lightweightResult?.metadata?.genre ?: audioFile.metadata.genre,
                        trackNumber = lightweightResult?.metadata?.trackNumber ?: audioFile.metadata.trackNumber,
                        totalTracks = lightweightResult?.metadata?.totalTracks ?: audioFile.metadata.totalTracks,
                        discNumber = lightweightResult?.metadata?.discNumber ?: audioFile.metadata.discNumber,
                        totalDiscs = lightweightResult?.metadata?.totalDiscs ?: audioFile.metadata.totalDiscs,
                        composer = lightweightResult?.metadata?.composer ?: audioFile.metadata.composer
                    )
                )
            } catch (e: Exception) {
                Timber.w(TAG, "Fast scan enrichment failed: ${audioFile.path}", e)
                audioFile
            }
        }
    }

    /**
     * Legacy fast-scan entry point that reads per-file duration/bitrate from MediaStore.
     * Kept for compatibility; prefer [enrichAll] when MediaStore data is already available.
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

                val lightweightResult = LightweightMetadataParser.parse(file)
                val metadata = lightweightResult?.metadata
                    ?: AudioMetadata(year = mediaStoreDataSource.queryYearFromMediaStore(path))

                AudioFile(
                    id = path.hashCode().toString(),
                    path = path,
                    name = file.name,
                    size = file.length(),
                    duration = duration,
                    format = extension.uppercase(),
                    bitrate = bitrate,
                    sampleRate = lightweightResult?.audioInfo?.sampleRate ?: 0,
                    channels = lightweightResult?.audioInfo?.channels ?: 0,
                    metadata = metadata
                )
            } catch (e: Exception) {
                Timber.w(TAG, "Fast scan failed: $path", e)
                null
            }
        }
    }
}