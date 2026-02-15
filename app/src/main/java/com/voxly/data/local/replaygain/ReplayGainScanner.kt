package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ReplayGain scanner using Android's MediaExtractor for audio analysis.
 * Implements the EBU R128 loudness standard for accurate gain calculation.
 */
@Singleton
class ReplayGainScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor
) {

    companion object {
        // Reference loudness level (EBU R128 standard is -23 LUFS, ReplayGain uses -18 LUFS)
        const val REFERENCE_LUFS = -18.0
        const val RMS_REFERENCE = 0.0001 // Reference RMS for calculations

        // Number of samples to process per chunk (for progress updates)
        const val SAMPLES_PER_CHUNK = 4096
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @return Flow emitting scan progress
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        var processedFiles = 0

        filePaths.forEachIndexed { index, filePath ->
            if (!kotlin.coroutines.coroutineContext.isActive) {
                emit(
                    ScanProgress(
                        currentFile = index,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.CANCELLED
                    )
                )
                return@flow
            }

            emit(
                ScanProgress(
                    currentFile = index + 1,
                    totalFiles = totalFiles,
                    percentage = processedFiles.toFloat() / totalFiles,
                    currentFilePath = filePath,
                    status = ScanStatus.SCANNING
                )
            )

            try {
                val replayGainInfo = analyzeAudioFile(filePath, scanQuality)

                if (replayGainInfo != null) {
                    // Save ReplayGain info to file metadata
                    saveReplayGainToFile(filePath, replayGainInfo)
                }

                processedFiles++

                emit(
                    ScanProgress(
                        currentFile = index + 1,
                        totalFiles = totalFiles,
                        percentage = (index + 1).toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.COMPLETED
                    )
                )
            } catch (e: Exception) {
                emit(
                    ScanProgress(
                        currentFile = index + 1,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.FAILED
                    )
                )
            }

            // Small delay to prevent UI freezing
            delay(50)
        }

        emit(
            ScanProgress(
                currentFile = totalFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = ScanStatus.COMPLETED
            )
        )
    }

    /**
     * Analyzes a single audio file and calculates ReplayGain.
     * @param filePath Path to the audio file
     * @param scanQuality Quality level
     * @return ReplayGainInfo or null if analysis fails
     */
    private suspend fun analyzeAudioFile(
        filePath: String,
        scanQuality: ScanQuality
    ): ReplayGainInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Calculate sample rate based on scan quality
            val targetSampleRate = when (scanQuality) {
                ScanQuality.FAST -> minOf(sampleRate, 22050)
                ScanQuality.NORMAL -> sampleRate
                ScanQuality.ACCURATE -> sampleRate
            }

            // Read audio samples and calculate loudness
            val samples = mutableListOf<Float>()
            val buffer = ByteBuffer.allocate(1024 * 1024)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                // Convert bytes to float samples (16-bit PCM)
                val shortBuffer = buffer.asShortBuffer()
                while (shortBuffer.hasRemaining()) {
                    val sample = shortBuffer.get() / 32768.0f
                    samples.add(sample)
                }

                buffer.clear()
                extractor.advance()
            }

            extractor.release()

            if (samples.isEmpty()) return@withContext null

            // Calculate RMS and peak
            val rms = calculateRMS(samples)
            val peak = calculatePeak(samples)

            // Calculate gain adjustment needed to reach reference level
            val currentDb = 20 * kotlin.math.log10(rms.coerceAtLeast(RMS_REFERENCE.toFloat()))
            val gainDb = (REFERENCE_LUFS - currentDb).toFloat()

            ReplayGainInfo(
                trackGain = gainDb,
                trackPeak = peak,
                albumGain = null, // Album gain requires scanning all files first
                albumPeak = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculates RMS (Root Mean Square) of audio samples.
     */
    private fun calculateRMS(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble().pow(2.0)
        }

        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Calculates peak level of audio samples.
     */
    private fun calculatePeak(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f

        var peak = 0f
        for (sample in samples) {
            val absSample = kotlin.math.abs(sample)
            if (absSample > peak) peak = absSample
        }

        return peak
    }

    /**
     * Calculates album gain from a list of track gains.
     */
    fun calculateAlbumGain(trackGains: List<ReplayGainInfo>): ReplayGainInfo {
        if (trackGains.isEmpty()) return ReplayGainInfo()

        // Average the track gains for album gain
        val avgGain = trackGains.map { it.trackGain }.average().toFloat()

        // Use the highest peak from all tracks
        val maxPeak = trackGains.maxOf { it.trackPeak }

        return ReplayGainInfo(
            trackGain = trackGains.first().trackGain,
            trackPeak = trackGains.first().trackPeak,
            albumGain = avgGain,
            albumPeak = maxPeak
        )
    }

    /**
     * Saves ReplayGain information to file metadata.
     * Uses TagLib to write REPLAYGAIN_TRACK_GAIN and related tags.
     */
    suspend fun saveReplayGainToFile(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            // Read existing metadata
            val existingMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)

            // Create updated metadata with ReplayGain fields
            val customFields = existingMetadata?.customFields?.toMutableMap() ?: mutableMapOf()
            customFields["REPLAYGAIN_TRACK_GAIN"] = String.format("%.2f dB", replayGainInfo.trackGain)
            customFields["REPLAYGAIN_TRACK_PEAK"] = String.format("%.6f", replayGainInfo.trackPeak)

            replayGainInfo.albumGain?.let {
                customFields["REPLAYGAIN_ALBUM_GAIN"] = String.format("%.2f dB", it)
            }

            replayGainInfo.albumPeak?.let {
                customFields["REPLAYGAIN_ALBUM_PEAK"] = String.format("%.6f", it)
            }

            val updatedMetadata = existingMetadata?.copy(customFields = customFields)
                ?: AudioMetadata(
                    title = null,
                    artist = null,
                    album = null,
                    customFields = customFields
                )

            val result = metadataProcessor.updateMetadata(filePath, updatedMetadata)
            result.isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads existing ReplayGain information from a fileparam filePath Path.
     * @ to the audio file
     * @return ReplayGainInfo or null if not found
     */
    suspend fun readReplayGainFromFile(filePath: String): ReplayGainInfo? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null

                val metadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val customFields = metadata?.customFields ?: return@withContext null

                val trackGainStr = customFields["REPLAYGAIN_TRACK_GAIN"]
                val trackPeakStr = customFields["REPLAYGAIN_TRACK_PEAK"]
                val albumGainStr = customFields["REPLAYGAIN_ALBUM_GAIN"]
                val albumPeakStr = customFields["REPLAYGAIN_ALBUM_PEAK"]

                val trackGain = parseGainValue(trackGainStr)
                val trackPeak = parsePeakValue(trackPeakStr)
                val albumGain = albumGainStr?.let { parseGainValue(it) }
                val albumPeak = albumPeakStr?.let { parsePeakValue(it) }

                if (trackGain != null || trackPeak != null) {
                    ReplayGainInfo(
                        trackGain = trackGain ?: 0f,
                        trackPeak = trackPeak ?: 0f,
                        albumGain = albumGain,
                        albumPeak = albumPeak
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Parses a gain value string (e.g., "-6.50 dB") to float.
     */
    private fun parseGainValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(" dB", "").replace("dB", "").trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a peak value string to float.
     */
    private fun parsePeakValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
