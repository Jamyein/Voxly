package com.voxly.data.local.replaygain

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.voxly.core.util.Logger
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReplayGain scanner using EBU R128 standard via libebur128.
 * 
 * Features:
 * - EBU R128 compliant loudness measurement (ITU-R BS.1770-4)
 * - True Peak detection with 4x oversampling
 * - Album gain using libebur128's loudness_global_multiple
 * - Target loudness: -18 LUFS (foobar2000 modern default)
 * - Supports all sample rates
 * 
 * Scan modes:
 * 1. Track Only: Individual track gain, no album gain
 * 2. Single Album: Treat all files as one album
 * 3. Albums: Auto-group by album metadata
 */
@Singleton
class Ebur128ReplayGainScanner @Inject constructor(
    private val metadataProcessor: TagLibMetadataProcessor
) {

    companion object {
        // foobar2000 1.1.6+ default: -18 LUFS
        const val TARGET_LUFS = -18.0f
        
        // Reference levels
        const val LUFS_EBU_R128 = -23.0f
        const val LUFS_REPLAYGAIN_CLASSIC = -14.0f
        
        // Decode timeout
        private const val DECODE_TIMEOUT_US = 10_000L
    }

    /**
     * Scans audio files and calculates ReplayGain values using EBU R128.
     * Track-only mode: calculates individual track gain only.
     * 
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level (reserved; EBU R128 uses native sample rate)
     * @param targetLoudness Target loudness in LUFS (default -18.0)
     * @return Flow emitting scan progress
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = TARGET_LUFS
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        val scanStartedAt = SystemClock.elapsedRealtime()
        
        Logger.i(
            "EBU R128 track scan started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS",
            "Ebur128ReplayGainScanner"
        )

        filePaths.forEachIndexed { index, filePath ->
            if (!kotlin.coroutines.coroutineContext.isActive) {
                emitCancelledProgress(index, totalFiles, filePath)
                return@flow
            }

            emitProgress(index, totalFiles, filePath, ScanStatus.SCANNING)

            try {
                val fileStartedAt = SystemClock.elapsedRealtime()
                val replayGainInfo = analyzeSingleTrack(filePath, scanQuality, targetLoudness)

                if (replayGainInfo != null) {
                    saveReplayGainToFile(filePath, replayGainInfo)
                    Logger.i(
                        "Track scan success: ${File(filePath).name} gain=${replayGainInfo.trackGain}dB " +
                        "peak=${replayGainInfo.trackPeak} truePeak=${replayGainInfo.truePeak} " +
                        "elapsed=${SystemClock.elapsedRealtime() - fileStartedAt}ms",
                        "Ebur128ReplayGainScanner"
                    )
                } else {
                    Logger.w("Track scan failed: ${File(filePath).name}", "Ebur128ReplayGainScanner")
                }

                emitProgress(index + 1, totalFiles, filePath, ScanStatus.COMPLETED)
            } catch (e: Exception) {
                Logger.e("Track scan error: ${File(filePath).name}", e, "Ebur128ReplayGainScanner")
                emitProgress(index + 1, totalFiles, filePath, ScanStatus.FAILED)
            }

            delay(50)
        }

        emitCompleteProgress(totalFiles)
        Logger.i(
            "EBU R128 track scan finished. elapsed=${SystemClock.elapsedRealtime() - scanStartedAt}ms",
            "Ebur128ReplayGainScanner"
        )
    }

    /**
     * Scans files grouped by album.
     * Uses libebur128's loudness_global_multiple for accurate album gain calculation.
     * 
     * @param filesByAlbum Map of album key to list of file paths
     * @param scanQuality Quality level (reserved; EBU R128 uses native sample rate)
     * @param targetLoudness Target loudness in LUFS
     * @return Flow emitting scan progress
     */
    fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float = TARGET_LUFS
    ): Flow<ScanProgress> = flow {
        val totalFiles = filesByAlbum.values.sumOf { it.size }
        val totalAlbums = filesByAlbum.size
        var processedFiles = 0
        val scanStartedAt = SystemClock.elapsedRealtime()
        
        Logger.i(
            "EBU R128 album scan started. albums=$totalAlbums files=$totalFiles targetLoudness=$targetLoudness LUFS",
            "Ebur128ReplayGainScanner"
        )

        for ((albumKey, albumFiles) in filesByAlbum) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
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

            Logger.d("Scanning album: $albumKey (${albumFiles.size} tracks)", "Ebur128ReplayGainScanner")

            // Skip albums with only one track - no album gain needed
            if (albumFiles.size <= 1) {
                for (filePath in albumFiles) {
                    emitProgress(processedFiles, totalFiles, filePath, ScanStatus.SCANNING)
                    try {
                        val info = analyzeSingleTrack(filePath, scanQuality, targetLoudness)
                        if (info != null) {
                            saveReplayGainToFile(filePath, info)
                        }
                    } catch (e: Exception) {
                        Logger.e("Single track scan error: $filePath", e, "Ebur128ReplayGainScanner")
                    }
                    processedFiles++
                    emitProgress(processedFiles, totalFiles, filePath, ScanStatus.COMPLETED)
                    delay(50)
                }
                continue
            }

            // Multi-track album: use libebur128 multiple state calculation
            val trackResults = mutableListOf<Pair<String, TrackAnalysisResult>>()
            val scanners = mutableListOf<Ebur128Scanner>()

            // First pass: analyze all tracks and collect scanner handles
            for (filePath in albumFiles) {
                emitProgress(processedFiles, totalFiles, filePath, ScanStatus.SCANNING)

                try {
                    val result = analyzeTrackWithScanner(filePath, scanQuality, targetLoudness)
                    if (result != null) {
                        trackResults.add(filePath to result)
                        scanners.add(result.scanner)
                    }
                } catch (e: Exception) {
                    Logger.e("Album track scan error: $filePath", e, "Ebur128ReplayGainScanner")
                }

                processedFiles++
                emitProgress(processedFiles, totalFiles, filePath, ScanStatus.SCANNING)
                delay(50)
            }

            // Second pass: calculate album gain using libebur128 multiple
            if (trackResults.isNotEmpty()) {
                val albumLoudness = calculateAlbumLoudness(scanners)
                val albumGain = if (albumLoudness.isFinite()) {
                    targetLoudness - albumLoudness.toFloat()
                } else {
                    // Fallback: average of track gains
                    trackResults.map { it.second.trackGain }.average().toFloat()
                }
                
                // Find max peaks across all tracks
                val maxSamplePeak = trackResults.maxOf { it.second.samplePeak }
                val maxTruePeak = trackResults.maxOf { it.second.truePeak }

                Logger.i(
                    "Album gain calculated: $albumKey loudness=${albumLoudness}dB " +
                    "gain=${albumGain}dB tracks=${trackResults.size}",
                    "Ebur128ReplayGainScanner"
                )

                // Save combined results
                for ((filePath, result) in trackResults) {
                    val combinedInfo = ReplayGainInfo(
                        trackGain = result.trackGain,
                        trackPeak = result.samplePeak.toFloat(),
                        truePeak = result.truePeak.toFloat(),
                        albumGain = albumGain,
                        albumPeak = maxSamplePeak.toFloat()
                    )
                    saveReplayGainToFile(filePath, combinedInfo)
                }
            }

            // Clean up scanners
            scanners.forEach { it.close() }
        }

        emitCompleteProgress(totalFiles)
        Logger.i(
            "EBU R128 album scan finished. albums=$totalAlbums files=$totalFiles " +
            "elapsed=${SystemClock.elapsedRealtime() - scanStartedAt}ms",
            "Ebur128ReplayGainScanner"
        )
    }

    /**
     * Scans with automatic album grouping by metadata.
     */
    fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = TARGET_LUFS
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        
        emit(
            ScanProgress(
                currentFile = 0,
                totalFiles = totalFiles,
                percentage = 0f,
                currentFilePath = "Reading metadata...",
                status = ScanStatus.SCANNING
            )
        )

        // Group files by album
        val filesByAlbum = mutableMapOf<String, MutableList<String>>()
        var singletonIndex = 0

        for (filePath in filePaths) {
            try {
                val metadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val album = metadata?.album?.trim() ?: ""
                val artist = metadata?.artist?.trim() ?: ""
                val albumKey = "${album}_$artist"

                if (album.isEmpty() && artist.isEmpty()) {
                    filesByAlbum.getOrPut("singleton_${singletonIndex++}") { mutableListOf() }.add(filePath)
                } else {
                    filesByAlbum.getOrPut(albumKey) { mutableListOf() }.add(filePath)
                }
            } catch (e: Exception) {
                filesByAlbum.getOrPut("singleton_${singletonIndex++}") { mutableListOf() }.add(filePath)
            }
        }

        Logger.i("Grouped $totalFiles files into ${filesByAlbum.size} albums", "Ebur128ReplayGainScanner")

        // Delegate to album scanning
        scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness).collect { progress ->
            emit(progress)
        }
    }

    /**
     * Analyzes a single track and returns complete results.
     */
    private suspend fun analyzeSingleTrack(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float
    ): ReplayGainInfo? = withContext(Dispatchers.IO) {
        val result = analyzeTrackWithScanner(filePath, scanQuality, targetLoudness)
        result?.scanner?.close()
        
        result?.let {
            ReplayGainInfo(
                trackGain = it.trackGain,
                trackPeak = it.samplePeak.toFloat(),
                truePeak = it.truePeak.toFloat(),
                albumGain = null,
                albumPeak = null
            )
        }
    }

    /**
     * Internal data class for track analysis results.
     */
    private data class TrackAnalysisResult(
        val scanner: Ebur128Scanner,
        val trackGain: Float,
        val samplePeak: Double,
        val truePeak: Double
    )

    /**
     * Analyzes a track and returns the scanner handle for album calculation.
     */
    private suspend fun analyzeTrackWithScanner(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float
    ): TrackAnalysisResult? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
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
            // EBU R128 requires correct sample rate; no resampling is applied here.
            val targetSampleRate = sampleRate

            val mode = Ebur128Scanner.MODE_I or 
                      Ebur128Scanner.MODE_TRUE_PEAK or
                      Ebur128Scanner.MODE_SAMPLE_PEAK

            val scanner = Ebur128Scanner(channelCount, targetSampleRate, mode)

            decodeAndAnalyze(extractor, format, targetSampleRate, channelCount, scanner)
            extractor.release()

            val loudness = scanner.getLoudnessGlobal()
            val samplePeak = scanner.getSamplePeak()
            val truePeak = scanner.getTruePeak()

            if (!loudness.isFinite()) {
                scanner.close()
                return@withContext null
            }

            val trackGain = (targetLoudness - loudness.toFloat()).coerceIn(-50f, 50f)

            TrackAnalysisResult(scanner, trackGain, samplePeak, truePeak)
        } catch (e: Exception) {
            Logger.e("Error analyzing track: $filePath", e, "Ebur128ReplayGainScanner")
            null
        }
    }

    /**
     * Calculates album loudness using libebur128's multiple state function.
     */
    private fun calculateAlbumLoudness(scanners: List<Ebur128Scanner>): Double {
        if (scanners.isEmpty()) return Double.NEGATIVE_INFINITY
        
        val handles = scanners.map { it.getHandle() }.toLongArray()
        return Ebur128Scanner.getLoudnessGlobalMultiple(handles)
    }

    /**
     * Decodes audio and feeds it to the EBU R128 scanner.
     */
    private fun decodeAndAnalyze(
        extractor: MediaExtractor,
        format: MediaFormat,
        targetSampleRate: Int,
        channelCount: Int,
        scanner: Ebur128Scanner
    ) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val codec = MediaCodec.createDecoderByType(mime)

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex, 0, sampleSize,
                                    extractor.sampleTime, 0
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

                            val shortBuffer = outputBuffer.asShortBuffer()
                            val samples = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(samples)

                            // Convert to float for EBU R128 scanner
                            val floatSamples = FloatArray(samples.size) { i ->
                                samples[i].toFloat() / 32768.0f
                            }

                            val frames = samples.size / channelCount
                            scanner.addFrames(floatSamples, frames)
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format changed, continue
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    /**
     * Saves ReplayGain information to file metadata.
     */
    suspend fun saveReplayGainToFile(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            val existingMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
            val customFields = existingMetadata?.customFields?.toMutableMap() ?: mutableMapOf()

            // Standard ReplayGain tags
            customFields["REPLAYGAIN_TRACK_GAIN"] = String.format("%.2f dB", replayGainInfo.trackGain)
            customFields["REPLAYGAIN_TRACK_PEAK"] = String.format("%.6f", replayGainInfo.trackPeak)

            // True peak
            replayGainInfo.truePeak?.let {
                customFields["REPLAYGAIN_TRACK_TRUE_PEAK"] = String.format("%.6f", it)
            }

            // Album tags
            replayGainInfo.albumGain?.let {
                customFields["REPLAYGAIN_ALBUM_GAIN"] = String.format("%.2f dB", it)
            }
            replayGainInfo.albumPeak?.let {
                customFields["REPLAYGAIN_ALBUM_PEAK"] = String.format("%.6f", it)
            }

            // EBU R128 metadata
            customFields["EBU_R128_ALGORITHM"] = "ITU-R BS.1770-4"
            customFields["EBU_R128_REFERENCE"] = "-18 LUFS"

            val audioMetadata = existingMetadata?.copy(customFields = customFields)
                ?: AudioMetadata(customFields = customFields)

            metadataProcessor.updateMetadata(filePath, audioMetadata)
            true
        } catch (e: Exception) {
            Logger.e("Failed to save ReplayGain: $filePath", e, "Ebur128ReplayGainScanner")
            false
        }
    }

    /**
     * Reads existing ReplayGain information from file.
     */
    suspend fun readReplayGainFromFile(filePath: String): ReplayGainInfo? = withContext(Dispatchers.IO) {
        try {
            val metadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
            val customFields = metadata?.customFields ?: return@withContext null

            fun parseGain(value: String?): Float? {
                return value?.replace(" dB", "")?.toFloatOrNull()
            }

            fun parsePeak(value: String?): Float? {
                return value?.toFloatOrNull()
            }

            ReplayGainInfo(
                trackGain = parseGain(customFields["REPLAYGAIN_TRACK_GAIN"]) ?: 0f,
                trackPeak = parsePeak(customFields["REPLAYGAIN_TRACK_PEAK"]) ?: 0f,
                truePeak = parsePeak(customFields["REPLAYGAIN_TRACK_TRUE_PEAK"]),
                albumGain = parseGain(customFields["REPLAYGAIN_ALBUM_GAIN"]),
                albumPeak = parsePeak(customFields["REPLAYGAIN_ALBUM_PEAK"])
            )
        } catch (e: Exception) {
            Logger.e("Failed to read ReplayGain: $filePath", e, "Ebur128ReplayGainScanner")
            null
        }
    }

    // Helper methods for progress emission
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ScanProgress>.emitProgress(
        current: Int, total: Int, path: String, status: ScanStatus
    ) {
        emit(
            ScanProgress(
                currentFile = current,
                totalFiles = total,
                percentage = if (total > 0) current.toFloat() / total else 0f,
                currentFilePath = path,
                status = status
            )
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ScanProgress>.emitCancelledProgress(
        current: Int, total: Int, path: String
    ) {
        emit(
            ScanProgress(
                currentFile = current,
                totalFiles = total,
                percentage = if (total > 0) current.toFloat() / total else 0f,
                currentFilePath = path,
                status = ScanStatus.CANCELLED
            )
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ScanProgress>.emitCompleteProgress(
        total: Int
    ) {
        emit(
            ScanProgress(
                currentFile = total,
                totalFiles = total,
                percentage = 1f,
                currentFilePath = "",
                status = ScanStatus.COMPLETED
            )
        )
    }
}
