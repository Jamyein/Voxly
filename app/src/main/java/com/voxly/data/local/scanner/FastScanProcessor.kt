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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
     *
     * Processes files in parallel with configurable concurrency to speed up scanning.
     */
    suspend fun enrichAll(audioFiles: List<AudioFile>, maxConcurrency: Int = 16): List<AudioFile> =
        coroutineScope {
            val semaphore = Semaphore(maxConcurrency)
            audioFiles
                .map { audioFile ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val file = File(audioFile.path)
                                if (!file.exists() || !file.canRead()) return@async audioFile

                                val lightweightResult = LightweightMetadataParser.parse(file)

                                // Single combined MediaStore query instead of per-file albumId + year queries
                                val needsMsData = (audioFile.mediaStoreAlbumId == null) ||
                                    (lightweightResult == null && audioFile.metadata.year.isNullOrBlank())
                                val msData = if (needsMsData) {
                                    mediaStoreDataSource.queryAllMediaStoreFields(audioFile.path)
                                } else null

                                val mediaStoreAlbumId = audioFile.mediaStoreAlbumId
                                    ?: msData?.albumId

                                val effectiveYear = when {
                                    lightweightResult != null && !lightweightResult.metadata.year.isNullOrBlank() -> lightweightResult.metadata.year
                                    !audioFile.metadata.year.isNullOrBlank() -> audioFile.metadata.year
                                    else -> msData?.year
                                }

                                audioFile.copy(
                                    sampleRate = lightweightResult?.audioInfo?.sampleRate ?: audioFile.sampleRate,
                                    channels = lightweightResult?.audioInfo?.channels ?: audioFile.channels,
                                    mediaStoreAlbumId = mediaStoreAlbumId,
                                    metadata = audioFile.metadata.copy(
                                        title = lightweightResult?.metadata?.title ?: audioFile.metadata.title,
                                        artist = lightweightResult?.metadata?.artist ?: audioFile.metadata.artist,
                                        album = lightweightResult?.metadata?.album,
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
                }.awaitAll()
        }


}
