package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.core.util.Logger
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
import kotlin.math.log10
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
        // Reference loudness level (standard ReplayGain uses -14 LUFS, corresponds to 89 dB SPL)
        // This matches foobar2000's default reference level
        const val REFERENCE_LUFS = -14.0
        const val RMS_REFERENCE = 0.0001 // Reference RMS for calculations

        // Block duration for 95th percentile RMS calculation (50ms blocks as per ReplayGain spec)
        const val BLOCK_DURATION_MS = 50

        // Number of samples to process per chunk (for progress updates)
        const val SAMPLES_PER_CHUNK = 4096

        private const val DECODE_TIMEOUT_US = 10_000L
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -14.0, standard ReplayGain)
     * @return Flow emitting scan progress
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -14f
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        var processedFiles = 0
        val scanStartedAt = SystemClock.elapsedRealtime()
        Logger.i(
            "ReplayGain scan started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS",
            "ReplayGainScanner"
        )

        filePaths.forEachIndexed { index, filePath ->
            if (!kotlin.coroutines.coroutineContext.isActive) {
                Logger.w(
                    "ReplayGain scan cancelled at index=$index processed=$processedFiles total=$totalFiles",
                    "ReplayGainScanner"
                )
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
                val fileStartedAt = SystemClock.elapsedRealtime()
                Logger.v(
                    "Analyzing ReplayGain file=${File(filePath).name} path=$filePath",
                    "ReplayGainScanner"
                )
                val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness)

                if (replayGainInfo != null) {
                    // Save ReplayGain info to file metadata
                    val saved = saveReplayGainToFile(filePath, replayGainInfo)
                    if (saved) {
                        Logger.i(
                            "ReplayGain success file=${File(filePath).name} gain=${replayGainInfo.trackGain} peak=${replayGainInfo.trackPeak} elapsedMs=${SystemClock.elapsedRealtime() - fileStartedAt}",
                            "ReplayGainScanner"
                        )
                    } else {
                        Logger.w(
                            "ReplayGain analysis done but save failed file=${File(filePath).name} elapsedMs=${SystemClock.elapsedRealtime() - fileStartedAt}",
                            "ReplayGainScanner"
                        )
                    }
                } else {
                    Logger.w(
                        "ReplayGain failed file=${File(filePath).name} reason=analyze_returned_null",
                        "ReplayGainScanner"
                    )
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
                Logger.e(
                    "ReplayGain failed file=${File(filePath).name} reason=${e.message ?: "unknown"}",
                    e,
                    "ReplayGainScanner"
                )
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
        Logger.i(
            "ReplayGain scan finished. files=$totalFiles processed=$processedFiles elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }

    /**
     * Analyzes a single audio file and calculates ReplayGain.
     * @param filePath Path to the audio file
     * @param scanQuality Quality level
     * @param targetLoudness Target loudness in LUFS
     * @return ReplayGainInfo or null if analysis fails
     */
    private suspend fun analyzeAudioFile(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float
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

            // Calculate target sample rate based on scan quality
            // Dynamic sample rate handling:
            // - If file sample rate <= maxSampleRate: use original sample rate
            // - If file sample rate > maxSampleRate: downsample to maxSampleRate
            val targetSampleRate = minOf(sampleRate, scanQuality.maxSampleRate)

            val stats = decodeAndAccumulateStats(
                extractor = extractor,
                format = format,
                targetSampleRate = targetSampleRate,
                channelCount = channelCount
            )

            if (stats.sampleCount <= 0L) {
                extractor.release()
                return@withContext null
            }

            // Calculate 95th percentile RMS from block RMS values (matches foobar2000 ReplayGain)
            val blockRmsValues = stats.blockRmsValues
            val rms = if (blockRmsValues.isNotEmpty()) {
                calculate95thPercentileRms(blockRmsValues)
            } else {
                calculateRMSFromStats(stats.sumSquares, stats.sampleCount)
            }
            val peak = stats.peak

            // Calculate gain adjustment needed to reach target loudness level
            val currentDb = 20 * log10(rms.coerceAtLeast(RMS_REFERENCE.toFloat()))
            val gainDb = (targetLoudness.toDouble() - currentDb).toFloat()

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
    private fun calculateRMSFromStats(sumSquares: Double, sampleCount: Long): Float {
        if (sampleCount <= 0L) return 0f
        return sqrt(sumSquares / sampleCount).toFloat()
    }

    /**
     * Calculates the 95th percentile RMS from a list of block RMS values.
     * This matches the foobar2000 ReplayGain algorithm which uses 95th percentile
     * instead of simple average for better human perception matching.
     *
     * @param blockRmsValues List of RMS values from each 50ms block
     * @return 95th percentile RMS value
     */
    private fun calculate95thPercentileRms(blockRmsValues: List<Float>): Float {
        if (blockRmsValues.isEmpty()) return 0f
        if (blockRmsValues.size == 1) return blockRmsValues.first()

        // Sort the RMS values
        val sortedRms = blockRmsValues.sorted()

        // Calculate the 95th percentile index
        val percentileIndex = ((sortedRms.size - 1) * 0.95).toInt()

        return sortedRms[percentileIndex]
    }

    private data class SampleStats(
        val sampleCount: Long,
        val sumSquares: Double,
        val peak: Float,
        val blockRmsValues: List<Float> = emptyList()
    )

    private fun decodeAndAccumulateStats(
        extractor: MediaExtractor,
        format: MediaFormat,
        targetSampleRate: Int,
        channelCount: Int
    ): SampleStats {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return SampleStats(0L, 0.0, 0f)
        val codec = MediaCodec.createDecoderByType(mime)

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false

            var sampleCount = 0L
            var sumSquares = 0.0
            var peak = 0f

            // For 95th percentile RMS calculation: collect RMS of each 50ms block
            val blockRmsValues = mutableListOf<Float>()
            var blockSampleCount = 0L
            var blockSumSquares = 0.0

            // Calculate samples per block based on target sample rate and block duration
            val samplesPerBlock = (targetSampleRate * BLOCK_DURATION_MS) / 1000

            val bufferInfo = MediaCodec.BufferInfo()
            var lastSeenTimestampUs = Long.MIN_VALUE
            var acceptSample = true

            // Buffer to collect samples for filter processing
            val sampleBuffer = mutableListOf<Float>()

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            if (bufferInfo.presentationTimeUs != lastSeenTimestampUs) {
                                lastSeenTimestampUs = bufferInfo.presentationTimeUs
                                acceptSample = shouldAcceptSample(
                                    timestampUs = bufferInfo.presentationTimeUs,
                                    targetSampleRate = targetSampleRate
                                )
                            }

                            if (acceptSample) {
                                val shortBuffer = outputBuffer.asShortBuffer()
                                while (shortBuffer.hasRemaining()) {
                                    val sample = shortBuffer.get().toInt().toFloat() / 32768.0f
                                    val absSample = kotlin.math.abs(sample)
                                    if (absSample > peak) peak = absSample
                                    sampleBuffer.add(sample)
                                    sampleCount++
                                    blockSampleCount++
                                    blockSumSquares += sample.toDouble().pow(2.0)

                                    // Calculate block RMS when we have enough samples
                                    if (blockSampleCount >= samplesPerBlock) {
                                        val blockRms = sqrt(blockSumSquares / blockSampleCount).toFloat()
                                        blockRmsValues.add(blockRms)
                                        blockSampleCount = 0L
                                        blockSumSquares = 0.0
                                    }
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        val newChannelCount =
                            newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        if (newChannelCount != channelCount) {
                            Logger.w(
                                "ReplayGain channelCount changed $channelCount -> $newChannelCount",
                                "ReplayGainScanner"
                            )
                        }
                    }
                }
            }

            // Process remaining samples in the last block
            if (blockSampleCount > 0) {
                val blockRms = sqrt(blockSumSquares / blockSampleCount).toFloat()
                blockRmsValues.add(blockRms)
            }

            // Apply psychoacoustic filters to all samples and recalculate with filtered audio
            val filteredStats = if (sampleBuffer.isNotEmpty()) {
                val samplesArray = sampleBuffer.toFloatArray()

                // Process through Yulewalk + Butterworth filters
                val filteredSamples = ReplayGainFilter.processFilters(samplesArray, channelCount)

                // Recalculate RMS from filtered samples
                var filteredSumSquares = 0.0
                var filteredPeak = 0f
                var filteredBlockCount = 0L
                var filteredBlockSumSquares = 0.0
                val filteredBlockRmsValues = mutableListOf<Float>()

                for (sample in filteredSamples) {
                    val absSample = kotlin.math.abs(sample)
                    if (absSample > filteredPeak) filteredPeak = absSample
                    filteredSumSquares += sample.toDouble().pow(2.0)
                    filteredBlockCount++
                    filteredBlockSumSquares += sample.toDouble().pow(2.0)

                    if (filteredBlockCount >= samplesPerBlock) {
                        val blockRms = sqrt(filteredBlockSumSquares / filteredBlockCount).toFloat()
                        filteredBlockRmsValues.add(blockRms)
                        filteredBlockCount = 0L
                        filteredBlockSumSquares = 0.0
                    }
                }

                if (filteredBlockCount > 0) {
                    val blockRms = sqrt(filteredBlockSumSquares / filteredBlockCount).toFloat()
                    filteredBlockRmsValues.add(blockRms)
                }

                SampleStats(
                    sampleCount = filteredSamples.size.toLong(),
                    sumSquares = filteredSumSquares,
                    peak = filteredPeak.coerceAtLeast(peak), // Use higher of the two peaks
                    blockRmsValues = filteredBlockRmsValues
                )
            } else {
                SampleStats(sampleCount, sumSquares, peak, blockRmsValues)
            }

            return filteredStats
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            try {
                codec.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun shouldAcceptSample(timestampUs: Long, targetSampleRate: Int): Boolean {
        if (targetSampleRate <= 0) return true
        val stepUs = (1_000_000L / targetSampleRate).coerceAtLeast(1L)
        return timestampUs % stepUs == 0L
    }

    /**
     * Calculates album gain from a list of track gains using energy average.
     *
     * Energy average formula: album_rms = sqrt(mean(track_rms²))
     * This prevents loud tracks from dominating the album gain calculation.
     * Matches foobar2000 ReplayGain album gain calculation.
     *
     * @param trackGains List of track ReplayGainInfo with track RMS values
     * @return Album gain info with album gain and peak
     */
    fun calculateAlbumGain(trackGains: List<ReplayGainInfo>): ReplayGainInfo {
        if (trackGains.isEmpty()) return ReplayGainInfo()

        // Convert track gains back to RMS values for energy average
        // gain_db = 20 * log10(rms / reference)
        // => rms = reference * 10^(gain_db / 20)
        val trackRmsValues = trackGains.map { trackGain ->
            val rmsReference = RMS_REFERENCE.toFloat()
            rmsReference * 10.0.pow(trackGain.trackGain / 20.0).toFloat()
        }

        // Energy average: sqrt(mean(rms²))
        val energyMean = trackRmsValues.map { it * it }.average()
        val albumRms = sqrt(energyMean).toFloat()

        // Convert back to dB gain
        val albumGainDb = 20 * log10(albumRms.coerceAtLeast(RMS_REFERENCE.toFloat()))

        // Use the highest peak from all tracks
        val maxPeak = trackGains.maxOf { it.trackPeak }

        return ReplayGainInfo(
            trackGain = trackGains.first().trackGain,
            trackPeak = trackGains.first().trackPeak,
            albumGain = albumGainDb,
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
