package com.voxly.data.repository

import android.content.Context
import android.provider.MediaStore
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.replaygain.ReplayGainScanner
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.model.parseMediaStoreTrackField
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioRepository using jaudiotagger for metadata operations.
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val metadataProcessor: TagLibMetadataProcessor
) : AudioRepository {
    companion object {
        private const val TAG = "AudioRepositoryImpl"
    }

    override fun scanAudioFiles(
        directoryPath: String?,
        forceRefresh: Boolean
    ): Flow<List<AudioFile>> = flow {
        // Use unified scan API
        val files = audioFileScanner.scan(
            directoryPaths = directoryPath?.let { listOf(it) } ?: emptyList(),
            incremental = false,
            forceRefresh = forceRefresh
        )
        emit(files)
    }.flowOn(Dispatchers.IO)

    override suspend fun hasCachedData(): Boolean = audioFileScanner.hasCachedData()

    override fun getCachedAudioFiles(): Flow<List<AudioFile>> = audioFileScanner.getCachedAudioFiles()


    override suspend fun getAudioFile(filePath: String): Result<AudioFile> =
        withContext(Dispatchers.IO) {
            try {
                // Read basic info from file
                val javaFile = java.io.File(filePath)
                val extension = filePath.substringAfterLast('.').lowercase()

                // OPTIMIZATION: Use readAllMetadata() for single TagLib call instead of multiple calls
                // This reads metadata + audio info + album art in one operation with cache support
                val completeMetadata = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = true)

                // Try to get duration and bitrate from MediaStore first (faster than TagLib)
                var duration = 0L
                var bitrate = 0
                try {
                    val selection = "${MediaStore.Audio.Media.DATA} = ?"
                    val selectionArgs = arrayOf(filePath)
                    val cursor = context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.BITRATE),
                        selection,
                        selectionArgs,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                            val bitrateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                            duration = it.getLong(durationCol)
                            // MediaStore returns bitrate in bps, convert to kbps
                            bitrate = it.getInt(bitrateCol) / 1000
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to query MediaStore for: $filePath")
                }

                // Use TagLib data as fallback if MediaStore doesn't have complete info
                var sampleRate = 0
                var channels = 0
                completeMetadata?.audioInfo?.let { audioInfo ->
                    if (duration == 0L) {
                        duration = audioInfo.durationMs
                    }
                    if (bitrate == 0) {
                        // TagLib returns bitrate in bps, convert to kbps
                        bitrate = audioInfo.bitrate / 1000
                    }
                    sampleRate = audioInfo.sampleRate
                    channels = audioInfo.channels
                }

                val audioFile = AudioFile(
                    id = filePath.hashCode().toString(),
                    path = filePath,
                    name = javaFile.name,
                    size = javaFile.length(),
                    duration = duration,
                    format = extension.uppercase(),
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    channels = channels,
                    metadata = completeMetadata?.metadata ?: AudioMetadata()
                )

                // Merge with MediaStore metadata if needed
                val mediaStoreFallbackMetadata = readMediaStoreBasicMetadata(filePath)
                val mergedMetadata = mergeWithFallback(audioFile.metadata, mediaStoreFallbackMetadata)

                val enhancedAudioFile = audioFile.copy(
                    metadata = mergedMetadata ?: AudioMetadata()
                )

                Result.success(enhancedAudioFile)
            } catch (e: SecurityException) {
                Result.failure(Exception("File not accessible due to storage permission/scope: $filePath", e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun readMetadata(filePath: String): Result<AudioMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val metadata = mergeWithFallback(
                    metadataProcessor.readMetadata(filePath),
                    readMediaStoreBasicMetadata(filePath)
                )
                if (metadata != null) {
                    Result.success(metadata)
                } else {
                    Result.failure(Exception("Failed to read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readMediaStoreBasicMetadata(filePath: String): AudioMetadata? {
        return runCatching {
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.TRACK
                ),
                selection,
                arrayOf(filePath),
                null
            )
            // Helper to parse MediaStore TRACK field
            // Uses shared implementation from domain model
            fun parseTrackField(value: Int): Pair<Int?, Int?> = parseMediaStoreTrackField(value)
            cursor?.use {
                if (!it.moveToFirst()) {
                    return@use null
                }
                val trackValue = it.getInt(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                val (parsedTrack, parsedTotal) = parseTrackField(trackValue)
                AudioMetadata(
                    title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                        ?.takeIf { value -> value.isNotBlank() },
                    artist = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                        ?.takeIf { value -> value.isNotBlank() },
                    album = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                        ?.takeIf { value -> value.isNotBlank() },
                    year = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))
                        ?.takeIf { value -> value.isNotBlank() },
                    trackNumber = parsedTrack,
                    totalTracks = parsedTotal
                )
            }
        }.getOrNull()
    }

    private fun mergeWithFallback(
        primary: AudioMetadata?,
        fallback: AudioMetadata?
    ): AudioMetadata? {
        if (primary == null) return fallback
        if (fallback == null) return primary

        return primary.copy(
            title = primary.title.takeIf { !it.isNullOrBlank() } ?: fallback.title,
            artist = primary.artist.takeIf { !it.isNullOrBlank() } ?: fallback.artist,
            album = primary.album.takeIf { !it.isNullOrBlank() } ?: fallback.album,
            year = primary.year.takeIf { !it.isNullOrBlank() } ?: fallback.year,
            trackNumber = primary.trackNumber ?: fallback.trackNumber
        )
    }

    override suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                metadataProcessor.updateMetadata(filePath, metadata).fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { cause ->
                        Result.failure(
                            Exception("Failed to update metadata for: $filePath. ${cause.message}", cause)
                        )
                    }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun extractAlbumArt(filePath: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(metadataProcessor.extractAlbumArt(filePath))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun setAlbumArt(filePath: String, albumArtBytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val currentMetadata = metadataProcessor.readMetadata(filePath)
                if (currentMetadata != null) {
                    val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)
                    metadataProcessor.updateMetadata(filePath, updatedMetadata).fold(
                        onSuccess = { Result.success(Unit) },
                        onFailure = { cause ->
                            Result.failure(
                                Exception("Failed to set album art for: $filePath. ${cause.message}", cause)
                            )
                        }
                    )
                } else {
                    Result.failure(Exception("Could not read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun removeAlbumArt(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val currentMetadata = metadataProcessor.readMetadata(filePath)
                if (currentMetadata != null) {
                    val updatedMetadata = currentMetadata.copy(albumArt = null)
                    metadataProcessor.updateMetadata(filePath, updatedMetadata).fold(
                        onSuccess = { Result.success(Unit) },
                        onFailure = { cause ->
                            Result.failure(
                                Exception("Failed to remove album art from: $filePath. ${cause.message}", cause)
                            )
                        }
                    )
                } else {
                    Result.failure(Exception("Could not read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/**
 * Implementation of ReplayGainRepository.
 * Uses ReplayGainScanner for audio analysis and jaudiotagger for tag writing.
 */
@Singleton
class ReplayGainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val replayGainScanner: ReplayGainScanner
) : ReplayGainRepository {

    override fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGain(filePaths, scanQuality, targetLoudness)

    override fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness)

    override fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGainWithAlbumGrouping(filePaths, scanQuality, targetLoudness)

    override suspend fun applyReplayGain(
        filePaths: List<String>,
        applyToTrack: Boolean,
        applyToAlbum: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // This method is used for applying already scanned ReplayGain
            // For now, return success as the actual scan and save is done through scanReplayGain
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveReplayGain(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val success = replayGainScanner.saveReplayGainToFile(filePath, replayGainInfo)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save ReplayGain information"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readReplayGain(filePath: String): Result<ReplayGainInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val info = replayGainScanner.readReplayGainFromFile(filePath)
                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
