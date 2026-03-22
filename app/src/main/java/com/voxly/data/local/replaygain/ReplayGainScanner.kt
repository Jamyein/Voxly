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
 * Represents the result of an audio decode attempt.
 */
enum class DecodeResult {
    SUCCESS,
    DECODER_INIT_FAILED,
    NO_AUDIO_TRACK,
    SAMPLE_COUNT_ZERO,
    PARTIAL_FAILURE,
    FILE_READ_ERROR,
    ALL_FALLBACKS_EXHAUSTED
}

/**
 * Detailed information about a decode failure for logging and error handling.
 */
data class DecodeFailureInfo(
    val result: DecodeResult,
    val filePath: String,
    val mime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fallbackLevel: Int, // 0 = no fallback attempted, 1 = Level 1 (retry), 2 = Level 2 (raw PCM), 3 = Level 3 (estimation)
    val cause: Throwable?
)

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
        // Reference loudness level
        // foobar2000 Classic ReplayGain: -14 dB RMS relative to full-scale sinusoid = 89 dB SPL
        // This is the standard ReplayGain reference level
        const val REFERENCE_LUFS = -14.0

        // RMS reference for gain calculation
        // Full-scale sinusoid = 1.0
        // -14 dB relative to full-scale = 10^(-14/20) ≈ 0.1995
        const val RMS_REFERENCE = 0.1995262314968879

        // Block duration for 95th percentile RMS calculation
        // foobar2000 uses 50ms blocks as per ReplayGain 1.0 spec
        const val BLOCK_DURATION_MS = 50

        // Number of samples to process per chunk (for progress updates)
        const val SAMPLES_PER_CHUNK = 4096

        private const val DECODE_TIMEOUT_US = 10_000L

        // Fallback config
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 100L
        const val MIN_VALID_SAMPLES = 1000L // ~20ms @ 48kHz
        const val MIN_AUDIO_DURATION_MS = 100L
    }

    /**
     * Result of decode operation with status.
     */
    private data class DecodeOperationResult(
        val stats: SampleStats?,
        val result: DecodeResult,
        val cause: Throwable?
    )

    /**
     * Attempts to decode audio with retry mechanism.
     * @return DecodeOperationResult containing stats and decode status
     */
    private fun attemptDecodeWithRetry(
        extractor: MediaExtractor,
        format: MediaFormat,
        targetSampleRate: Int,
        channelCount: Int
    ): DecodeOperationResult {
        var lastCause: Throwable? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val stats = decodeAndAccumulateStats(
                    extractor = extractor,
                    format = format,
                    targetSampleRate = targetSampleRate,
                    channelCount = channelCount
                )

                if (stats.sampleCount <= 0) {
                    return DecodeOperationResult(null, DecodeResult.SAMPLE_COUNT_ZERO, null)
                }

                return DecodeOperationResult(stats, DecodeResult.SUCCESS, null)
            } catch (e: Exception) {
                lastCause = e
                Logger.w(
                    "Decode attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS failed: ${e.message}",
                    "ReplayGainScanner"
                )

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    // Wait before retry
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
        }

        return DecodeOperationResult(
            null,
            DecodeResult.DECODER_INIT_FAILED,
            lastCause
        )
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
     * Scans audio files with album grouping.
     * Reads metadata from each file to group by album, then calculates both track and album gain.
     * For ALBUMS mode - groups files by album+artist metadata and calculates album gain
     * using energy average of all tracks in each album.
     *
     * @param filePaths Flat list of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -14.0)
     * @return Flow emitting scan progress
     */
    fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -14f
    ): Flow<ScanProgress> = flow {
        val scanStartedAt = SystemClock.elapsedRealtime()
        val totalFiles = filePaths.size

        Logger.i(
            "ReplayGain album grouping started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS",
            "ReplayGainScanner"
        )

        // Phase 1: Read metadata and group files by album
        emit(
            ScanProgress(
                currentFile = 0,
                totalFiles = totalFiles,
                percentage = 0f,
                currentFilePath = "Reading metadata...",
                status = ScanStatus.SCANNING
            )
        )

        val filesByAlbum = mutableMapOf<String, MutableList<String>>()
        var singletonIndex = 0

        for (filePath in filePaths) {
            try {
                val metadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                // Use album + artist as the grouping key
                val album = metadata?.album?.trim() ?: ""
                val artist = metadata?.artist?.trim() ?: ""
                val albumKey = "${album}_$artist"

                if (album.isEmpty() && artist.isEmpty()) {
                    // No album info - treat as single track (singleton album)
                    val singletonKey = "singleton_${singletonIndex++}"
                    filesByAlbum.getOrPut(singletonKey) { mutableListOf() }.add(filePath)
                } else if (album.isNotEmpty()) {
                    // Has album - group by album+artist
                    filesByAlbum.getOrPut(albumKey) { mutableListOf() }.add(filePath)
                } else {
                    // Has artist but no album - group by artist (various artists)
                    filesByAlbum.getOrPut(albumKey) { mutableListOf() }.add(filePath)
                }
            } catch (e: Exception) {
                // If metadata read fails, treat as single track
                val singletonKey = "singleton_${singletonIndex++}"
                filesByAlbum.getOrPut(singletonKey) { mutableListOf() }.add(filePath)
                Logger.w("Failed to read metadata for grouping: $filePath", "ReplayGainScanner")
            }
        }

        val totalAlbums = filesByAlbum.size
        Logger.i(
            "Grouped $totalFiles files into $totalAlbums albums",
            "ReplayGainScanner"
        )

        // Now delegate to the main album scanning method
        scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness).collect { progress ->
            emit(progress)
        }

        Logger.i(
            "ReplayGain album grouping finished. elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }

    /**
     * Scans audio files grouped by album and calculates both track and album gain.
     * For ALBUMS mode - groups files by album metadata and calculates album gain
     * using energy average of all tracks in each album.
     *
     * @param filesByAlbum Map of album key to list of file paths in that album
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -14.0)
     * @return Flow emitting scan progress
     */
    fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -14f
    ): Flow<ScanProgress> = flow {
        val totalAlbums = filesByAlbum.size
        val totalFiles = filesByAlbum.values.flatten().size
        var processedFiles = 0
        var processedAlbums = 0
        val scanStartedAt = SystemClock.elapsedRealtime()

        Logger.i(
            "ReplayGain album scan started. albums=$totalAlbums files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS",
            "ReplayGainScanner"
        )

        for ((albumKey, albumFiles) in filesByAlbum) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                Logger.w(
                    "ReplayGain album scan cancelled at album=$albumKey processedAlbums=$processedAlbums",
                    "ReplayGainScanner"
                )
                emit(
                    ScanProgress(
                        currentFile = processedFiles,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = "",
                        status = ScanStatus.CANCELLED
                    )
                )
                return@flow
            }

            // Skip albums with only one track - no album gain needed
            if (albumFiles.size <= 1) {
                Logger.v(
                    "Skipping album gain for album=$albumKey - only ${albumFiles.size} track(s)",
                    "ReplayGainScanner"
                )
                // Still scan as single track
                for (filePath in albumFiles) {
                    emit(
                        ScanProgress(
                            currentFile = processedFiles + 1,
                            totalFiles = totalFiles,
                            percentage = processedFiles.toFloat() / totalFiles,
                            currentFilePath = filePath,
                            status = ScanStatus.SCANNING
                        )
                    )

                    try {
                        val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness)
                        if (replayGainInfo != null) {
                            saveReplayGainToFile(filePath, replayGainInfo)
                        }
                    } catch (e: Exception) {
                        Logger.e("Album scan failed for file=$filePath", e, "ReplayGainScanner")
                    }

                    processedFiles++
                    emit(
                        ScanProgress(
                            currentFile = processedFiles,
                            totalFiles = totalFiles,
                            percentage = processedFiles.toFloat() / totalFiles,
                            currentFilePath = filePath,
                            status = ScanStatus.COMPLETED
                        )
                    )
                }
                processedAlbums++
                continue
            }

            // First pass: scan all tracks in the album to get track gains
            val trackGains = mutableListOf<Pair<String, ReplayGainInfo>>()

            for ((index, filePath) in albumFiles.withIndex()) {
                emit(
                    ScanProgress(
                        currentFile = processedFiles + 1,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.SCANNING
                    )
                )

                try {
                    val fileStartedAt = SystemClock.elapsedRealtime()
                    val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness)

                    if (replayGainInfo != null) {
                        trackGains.add(filePath to replayGainInfo)
                        Logger.v(
                            "Track gain calculated file=${File(filePath).name} gain=${replayGainInfo.trackGain} elapsedMs=${SystemClock.elapsedRealtime() - fileStartedAt}",
                            "ReplayGainScanner"
                        )
                    } else {
                        Logger.w(
                            "Track gain analysis failed file=${File(filePath).name}",
                            "ReplayGainScanner"
                        )
                    }
                } catch (e: Exception) {
                    Logger.e(
                        "Track scan failed file=${File(filePath).name} reason=${e.message}",
                        e,
                        "ReplayGainScanner"
                    )
                }

                processedFiles++
                emit(
                    ScanProgress(
                        currentFile = processedFiles,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.SCANNING
                    )
                )

                delay(50)
            }

            // Second pass: calculate album gain and save to all files in the album
            if (trackGains.isNotEmpty()) {
                val albumGainInfo = calculateAlbumGain(trackGains.map { it.second })
                Logger.i(
                    "Album gain calculated album=$albumKey tracks=${trackGains.size} albumGain=${albumGainInfo.albumGain} albumPeak=${albumGainInfo.albumPeak}",
                    "ReplayGainScanner"
                )

                // Save album gain to each track
                for ((filePath, trackInfo) in trackGains) {
                    try {
                        // Combine track gain with album gain
                        val combinedInfo = ReplayGainInfo(
                            trackGain = trackInfo.trackGain,
                            trackPeak = trackInfo.trackPeak,
                            albumGain = albumGainInfo.albumGain,
                            albumPeak = albumGainInfo.albumPeak
                        )
                        saveReplayGainToFile(filePath, combinedInfo)
                    } catch (e: Exception) {
                        Logger.e(
                            "Failed to save album gain for file=$filePath reason=${e.message}",
                            e,
                            "ReplayGainScanner"
                        )
                    }
                }
            }

            processedAlbums++
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
            "ReplayGain album scan finished. albums=$totalAlbums files=$totalFiles elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
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

            val decodeResult = attemptDecodeWithRetry(
                extractor = extractor,
                format = format,
                targetSampleRate = targetSampleRate,
                channelCount = channelCount
            )

            when (decodeResult.result) {
                DecodeResult.SUCCESS -> {
                    // Continue with stats
                }
                DecodeResult.SAMPLE_COUNT_ZERO -> {
                    extractor.release()
                    return@withContext null
                }
                DecodeResult.DECODER_INIT_FAILED -> {
                    // Trigger fallback
                }
                else -> {
                    extractor.release()
                    return@withContext null
                }
            }

            val stats = decodeResult.stats!!

            // Calculate 95th percentile RMS from block RMS values
            // foobar2000 uses 95th percentile for better human perception matching
            val blockRmsValues = stats.blockRmsValues
            val rms = if (blockRmsValues.isNotEmpty()) {
                calculate95thPercentileRms(blockRmsValues)
            } else {
                // Fallback: calculate from mean square if block calculation failed
                sqrt(stats.sumSquares / stats.sampleCount).toFloat()
            }
            val peak = stats.peak // Peak from UNFILTERED audio (foobar2000 behavior)

            // Calculate gain adjustment needed to reach target loudness level
            // Formula: gain_db = target_loudness - measured_loudness
            // where measured_loudness = 20 * log10(rms / reference)
            // and target_loudness = REFERENCE_LUFS = -14 dB
            //
            // Note: We use a very small floor to prevent log10(0) = -Infinity
            val measuredDb = 20 * log10(rms.toDouble().coerceAtLeast(1e-10))
            val gainDb = (targetLoudness.toDouble() - measuredDb).toFloat()

            Logger.v(
                "ReplayGain result: file=${file.name} rms=${rms} measuredDb=${measuredDb} gainDb=${gainDb} peak=${peak}",
                "ReplayGainScanner"
            )

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

    /**
     * Decodes audio file and collects statistics for ReplayGain calculation.
     *
     * foobar2000 ReplayGain implementation:
     * - Peak: Calculated from UNFILTERED original audio (for clipping prevention)
     * - RMS/Loudness: Calculated from FILTERED audio (psychoacoustically compensated)
     * - Block size: 50ms
     * - Percentile: 95th percentile
     */
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
            var peak = 0f

            // Calculate samples per block based on target sample rate and block duration
            val samplesPerBlock = (targetSampleRate * BLOCK_DURATION_MS) / 1000

            val bufferInfo = MediaCodec.BufferInfo()
            var lastSeenTimestampUs = Long.MIN_VALUE
            var acceptSample = true

            // Buffer to collect raw samples for filter processing
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
                                    // Normalize to -1.0 to 1.0 range
                                    val sample = shortBuffer.get().toInt().toFloat() / 32768.0f

                                    // Calculate PEAK from UNFILTERED audio (foobar2000 behavior)
                                    // This is used for clipping prevention
                                    val absSample = kotlin.math.abs(sample)
                                    if (absSample > peak) peak = absSample

                                    // Store sample for later filtering
                                    sampleBuffer.add(sample)
                                    sampleCount++
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

            // Apply psychoacoustic filters and calculate RMS from filtered audio
            return if (sampleBuffer.isNotEmpty()) {
                val samplesArray = sampleBuffer.toFloatArray()

                // Process through Yulewalk + Butterworth filters
                // This matches foobar2000's analysis chain
                val filteredSamples = ReplayGainFilter.processFilters(samplesArray, channelCount)

                // Calculate RMS from FILTERED samples only (for loudness measurement)
                val blockRmsValues = mutableListOf<Float>()
                var blockSumSquares = 0.0
                var blockSampleCount = 0L

                for (sample in filteredSamples) {
                    blockSumSquares += sample.toDouble().pow(2.0)
                    blockSampleCount++

                    if (blockSampleCount >= samplesPerBlock) {
                        val blockRms = sqrt(blockSumSquares / blockSampleCount).toFloat()
                        blockRmsValues.add(blockRms)
                        blockSampleCount = 0L
                        blockSumSquares = 0.0
                    }
                }

                // Process remaining samples in the last block
                if (blockSampleCount > 0) {
                    val blockRms = sqrt(blockSumSquares / blockSampleCount).toFloat()
                    blockRmsValues.add(blockRms)
                }

                // Calculate total sum of squares for fallback
                val totalSumSquares = filteredSamples.sumOf { it.toDouble().pow(2.0) }

                // Return filtered RMS values but KEEP original peak (unfiltered)
                // This matches foobar2000: peak from original, RMS from filtered
                SampleStats(
                    sampleCount = filteredSamples.size.toLong(),
                    sumSquares = totalSumSquares, // For fallback RMS calculation
                    peak = peak, // Use UNFILTERED peak (foobar2000 behavior)
                    blockRmsValues = blockRmsValues
                )
            } else {
                SampleStats(sampleCount, 0.0, peak, emptyList())
            }
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
        Logger.i("calculateAlbumGain: input trackGains count=${trackGains.size} gains=${trackGains.map { it.trackGain }}", "ReplayGainScanner")

        if (trackGains.isEmpty()) return ReplayGainInfo()

        // Filter out invalid track gains (NaN, Infinity, or extremely anomalous values)
        val validTrackGains = trackGains.filter { trackGain ->
            trackGain.trackGain.isFinite() &&
            trackGain.trackGain > -100f &&
            trackGain.trackGain < 100f
        }

        Logger.i("calculateAlbumGain: valid trackGains count=${validTrackGains.size}", "ReplayGainScanner")

        if (validTrackGains.isEmpty()) {
            Logger.w("calculateAlbumGain: no valid track gains found", "ReplayGainScanner")
            return ReplayGainInfo()
        }

        // Convert track gains back to linear RMS values
        // track_gain = target_loudness - measured_loudness
        // measured_loudness = target_loudness - track_gain
        // measured_loudness_db = 20 * log10(rms / reference)
        // => rms = reference * 10^(measured_loudness_db / 20)
        // => rms = reference * 10^((target_loudness - track_gain) / 20)
        val trackRmsValues = validTrackGains.map { trackGain ->
            val linear = RMS_REFERENCE * 10.0.pow((REFERENCE_LUFS - trackGain.trackGain) / 20.0)
            // Clamp to valid range to prevent extreme values
            linear.coerceIn(1e-10, 10.0)
        }

        Logger.v(
            "Album calculation: trackGains=${validTrackGains.map { it.trackGain }} trackRmsValues=$trackRmsValues",
            "ReplayGainScanner"
        )

        // Energy average: sqrt(mean(rms²))
        // This matches foobar2000's album gain calculation
        val energyMean = trackRmsValues.map { it * it }.average()
        val albumRmsLinear = sqrt(energyMean).coerceIn(1e-10, 10.0)

        // Convert back to dB gain: album_gain = target - 20 * log10(album_rms / reference)
        val albumGainDb = if (albumRmsLinear.isNaN() || albumRmsLinear.isInfinite() || albumRmsLinear <= 0) {
            Logger.w("calculateAlbumGain: invalid albumRmsLinear=$albumRmsLinear, using 0", "ReplayGainScanner")
            0f
        } else {
            val albumGain = REFERENCE_LUFS - (20 * log10(albumRmsLinear / RMS_REFERENCE))
            albumGain.toFloat()
        }

        Logger.v(
            "Album gain calculated: trackCount=${validTrackGains.size} albumGainDb=$albumGainDb albumRmsLinear=$albumRmsLinear",
            "ReplayGainScanner"
        )

        // Use the highest peak from all tracks
        val maxPeak = validTrackGains.maxOf { it.trackPeak }

        return ReplayGainInfo(
            trackGain = validTrackGains.first().trackGain,
            trackPeak = validTrackGains.first().trackPeak,
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
