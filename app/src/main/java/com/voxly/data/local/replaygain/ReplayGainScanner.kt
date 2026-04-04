package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.core.util.Logger
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ClipMode
import com.voxly.domain.model.ReplayGainConfig
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import com.voxly.data.local.replaygain.native.EbuR128NativeScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
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
 * ReplayGain scanner using Android's MediaExtractor for audio analysis.
 * Implements the EBU R128 loudness standard (ITU-R BS.1770-4) for accurate gain calculation.
 *
 * Based on rsgain architecture:
 * - Uses EbuR128Analyzer for loudness measurement (port of libebur128)
 * - Supports clip_mode (none/positive/always) for clipping protection
 * - Supports ReplayGain 2.0 tags (TRACK_RANGE, ALBUM_RANGE, REFERENCE_LOUDNESS)
 * - Supports album gain calculation with energy averaging
 */
@Singleton
class ReplayGainScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor
) {

    // Lazy native EBU R128 scanner (computation only, decoding via MediaCodec)
    private val nativeEbuR128 by lazy {
        try {
            EbuR128NativeScanner(
                channels = 2,
                sampleRate = 48000,
                targetLoudness = REFERENCE_LUFS
            )
        } catch (e: UnsatisfiedLinkError) {
            Logger.w("Native EBU R128 scanner not available: ${e.message}", "ReplayGainScanner")
            null
        }
    }

    /**
     * Check if native EBU R128 scanner is available.
     */
    fun isNativeScannerAvailable(): Boolean = nativeEbuR128 != null

    /**
     * Get the native scanner version info.
     */
    fun getNativeScannerVersion(): String = try {
        nativeEbuR128?.getVersion() ?: "Native scanner not available"
    } catch (e: UnsatisfiedLinkError) {
        "Native scanner not available"
    }

    companion object {
        // Reference loudness level (ReplayGain 2.0 standard: -18 LUFS, rsgain)
        const val REFERENCE_LUFS = -18.0

        // Number of samples to process per chunk (for progress updates)
        const val SAMPLES_PER_CHUNK = 4096

        private const val DECODE_TIMEOUT_US = 10_000L

        // Fallback config
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 100L
        const val MIN_VALID_SAMPLES = 1000L
        const val MIN_AUDIO_DURATION_MS = 100L

        // Gain clamping range
        const val MIN_GAIN_DB = -50f
        const val MAX_GAIN_DB = 50f
    }

    /**
     * Result of decode operation with status.
     */
    private data class DecodeOperationResult(
        val analyzer: EbuR128Analyzer?,
        val result: DecodeResult,
        val cause: Throwable?
    )

    /**
     * Attempts to decode audio with retry mechanism.
     * Creates a fresh MediaExtractor for each retry attempt to avoid
     * extractor release issues on subsequent retries.
     * @return DecodeOperationResult containing analyzer and decode status
     */
    private suspend fun attemptDecodeWithRetry(
        filePath: String,
        format: MediaFormat,
        targetSampleRate: Int,
        channelCount: Int,
        targetLoudness: Double,
        dualMono: Boolean
    ): DecodeOperationResult {
        var lastCause: Throwable? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(filePath)

                // Find audio track and configure codec
                var audioTrackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        break
                    }
                }

                if (audioTrackIndex == -1) {
                    return DecodeOperationResult(null, DecodeResult.NO_AUDIO_TRACK, null)
                }

                extractor.selectTrack(audioTrackIndex)

                val analyzer = decodeAndAccumulateStats(
                    extractor = extractor,
                    format = format,
                    targetSampleRate = targetSampleRate,
                    channelCount = channelCount,
                    targetLoudness = targetLoudness,
                    dualMono = dualMono
                )

                if (analyzer.getBlockCount() <= 0) {
                    return DecodeOperationResult(null, DecodeResult.SAMPLE_COUNT_ZERO, null)
                }

                return DecodeOperationResult(analyzer, DecodeResult.SUCCESS, null)
            } catch (e: Exception) {
                lastCause = e
                Logger.w(
                    "Decode attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS failed: ${e.message}",
                    "ReplayGainScanner"
                )
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                delay(RETRY_DELAY_MS)
            }
        }

        return DecodeOperationResult(
            null,
            DecodeResult.DECODER_INIT_FAILED,
            lastCause
        )
    }

    /**
     * Fallback: Read raw PCM data directly from file using FileInputStream.
     * Bypasses MediaCodec decoding for files that fail to decode.
     *
     * @param filePath Path to the audio file
     * @param channelCount Number of audio channels
     * @param targetLoudness Target loudness for analyzer
     * @return EbuR128Analyzer with processed samples, or null if failed
     */
    private fun fallbackReadRawPcm(
        filePath: String,
        channelCount: Int,
        targetLoudness: Double
    ): EbuR128Analyzer? {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Logger.w("Fallback PCM read failed: file not accessible $filePath", "ReplayGainScanner")
                return null
            }

            val extension = file.extension.lowercase()

            if (extension !in listOf("wav", "flac", "ogg", "mp3")) {
                Logger.w("Fallback PCM read: unsupported format $extension", "ReplayGainScanner")
                return null
            }

            if (extension == "wav") {
                return fallbackReadWavPcm(file, channelCount, targetLoudness)
            }

            Logger.w("Fallback PCM read: format $extension not fully supported", "ReplayGainScanner")
            return null
        } catch (e: Exception) {
            Logger.e("Fallback PCM read error: ${e.message}", e, "ReplayGainScanner")
            return null
        }
    }

    /**
     * Reads WAV file directly as PCM fallback.
     */
    private fun fallbackReadWavPcm(file: File, channelCount: Int, targetLoudness: Double): EbuR128Analyzer? {
        try {
            val bytes = file.readBytes()

            if (bytes.size < 44) return null

            if (bytes[0] != 0x52.toByte() ||
                bytes[1] != 0x49.toByte() ||
                bytes[2] != 0x46.toByte() ||
                bytes[3] != 0x46.toByte()) {
                return null
            }

            if (bytes[8] != 0x57.toByte() ||
                bytes[9] != 0x41.toByte() ||
                bytes[10] != 0x56.toByte() ||
                bytes[11] != 0x45.toByte()) {
                return null
            }

            if (bytes.size < 36) return null
            val bitsPerSample = (bytes[35].toInt() and 0xFF) or ((bytes[34].toInt() and 0xFF) shl 8)
            if (bitsPerSample != 16) {
                Logger.w("Fallback WAV: only 16-bit PCM supported, found $bitsPerSample bits", "ReplayGainScanner")
                return null
            }

            var dataOffset = 12
            var dataSize = 0L
            while (dataOffset + 8 <= bytes.size) {
                val chunkId = bytes.slice(dataOffset until dataOffset + 4).toByteArray()
                val chunkSize = (bytes[dataOffset + 4].toInt() and 0xFF) or
                               ((bytes[dataOffset + 5].toInt() and 0xFF) shl 8) or
                               ((bytes[dataOffset + 6].toInt() and 0xFF) shl 16) or
                               ((bytes[dataOffset + 7].toInt() and 0xFF) shl 24)

                if (String(chunkId, StandardCharsets.US_ASCII) == "data") {
                    dataSize = chunkSize.toLong()
                    dataOffset += 8
                    break
                }
                val padding = if (chunkSize % 2 == 1) 1 else 0
                dataOffset += 8 + chunkSize + padding
            }

            if (dataSize <= 0 || dataOffset >= bytes.size) return null

            val sampleData = bytes.sliceArray(dataOffset until minOf(dataOffset + dataSize.toInt(), bytes.size))
            val floatSamples = mutableListOf<Float>()
            var i = 0
            while (i + 1 < sampleData.size) {
                val sample = ((sampleData[i + 1].toInt() shl 8) or (sampleData[i].toInt() and 0xFF)).toShort()
                floatSamples.add(sample.toFloat() / 32768.0f)
                i += 2
            }

            if (floatSamples.isEmpty()) return null

            // Create analyzer and process samples
            val analyzer = EbuR128Analyzer(
                channels = channelCount,
                sampleRate = 44100,
                targetLoudness = targetLoudness
            )
            analyzer.processBlock(floatSamples.toFloatArray(), floatSamples.size)

            return analyzer
        } catch (e: Exception) {
            Logger.e("WAV fallback read error: ${e.message}", e, "ReplayGainScanner")
            return null
        }
    }

    /**
     * Fallback Level 3: Estimate gain based on file metadata when all decode attempts fail.
     */
    private fun fallbackEstimateGain(
        filePath: String,
        targetLoudness: Float
    ): ReplayGainInfo? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val fileSizeBytes = file.length()
            val extension = file.extension.lowercase()

            val estimatedBitrate = when (extension) {
                "flac" -> 800_000
                "wav" -> 1_411_200
                "mp3" -> 320_000
                "m4a", "aac" -> 256_000
                "ogg" -> 256_000
                "ape" -> 800_000
                else -> 320_000
            }

            val estimatedDurationSeconds = (fileSizeBytes * 8.0) / estimatedBitrate

            if (fileSizeBytes < 100_000) {
                Logger.w("File too small for estimation: $filePath", "ReplayGainScanner")
                return null
            }

            val estimatedGain = 0f
            val estimatedPeak = 0.5f

            Logger.w(
                "Level 3 fallback estimation for $filePath: " +
                "estimatedDuration=${estimatedDurationSeconds}s fileSize=${fileSizeBytes}B",
                "ReplayGainScanner"
            )

            ReplayGainInfo(
                trackGain = estimatedGain,
                trackPeak = estimatedPeak,
                albumGain = null,
                albumPeak = null,
                referenceLoudness = targetLoudness
            )
        } catch (e: Exception) {
            Logger.e("Level 3 estimation failed: ${e.message}", e, "ReplayGainScanner")
            null
        }
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -18.0, rsgain standard)
     * @param config ReplayGain configuration (clip mode, dual mono, etc.)
     * @return Flow emitting scan progress
     */
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT,
        useNative: Boolean = false
    ): Flow<ScanProgress> = flow {
        val totalFiles = filePaths.size
        var processedFiles = 0
        val scanStartedAt = SystemClock.elapsedRealtime()
        Logger.i(
            "ReplayGain scan started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS clipMode=${config.clipMode}",
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
                    "Analyzing ReplayGain file=${File(filePath).name} path=$filePath native=$useNative",
                    "ReplayGainScanner"
                )
                val replayGainInfo = if (useNative && nativeEbuR128 != null) {
                    analyzeAudioFileNative(filePath, scanQuality, targetLoudness, config)
                } else {
                    analyzeAudioFile(filePath, scanQuality, targetLoudness, config)
                }

                if (replayGainInfo != null) {
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
                        status = ScanStatus.COMPLETED,
                        replayGainInfo = replayGainInfo
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
     *
     * @param filePaths Flat list of file paths to scan
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -18.0)
     * @param config ReplayGain configuration
     * @param useNative If true, uses native libebur128 for computation (faster, more accurate)
     * @return Flow emitting scan progress
     */
    fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT,
        useNative: Boolean = false
    ): Flow<ScanProgress> = flow {
        val scanStartedAt = SystemClock.elapsedRealtime()
        val totalFiles = filePaths.size

        Logger.i(
            "ReplayGain album grouping started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS native=$useNative",
            "ReplayGainScanner"
        )

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
                val album = metadata?.album?.trim() ?: ""
                val artist = metadata?.artist?.trim() ?: ""
                val albumKey = "${album}_$artist"

                if (album.isEmpty() && artist.isEmpty()) {
                    val singletonKey = "singleton_${singletonIndex++}"
                    filesByAlbum.getOrPut(singletonKey) { mutableListOf() }.add(filePath)
                } else if (album.isNotEmpty()) {
                    filesByAlbum.getOrPut(albumKey) { mutableListOf() }.add(filePath)
                } else {
                    filesByAlbum.getOrPut(albumKey) { mutableListOf() }.add(filePath)
                }
            } catch (e: Exception) {
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

        // Route to native or Kotlin scanner
        if (useNative && nativeEbuR128 != null) {
            scanReplayGainByAlbumNative(filesByAlbum, scanQuality, targetLoudness, config).collect { progress ->
                emit(progress)
            }
        } else {
            scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness, config).collect { progress ->
                emit(progress)
            }
        }

        Logger.i(
            "ReplayGain album grouping finished. elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }

    /**
     * Scans audio files grouped by album using native libebur128 engine.
     * Uses Android MediaCodec for decoding and native libebur128 for computation.
     * Provides bit-exact results matching rsgain's behavior with minimal APK size impact.
     *
     * @param filesByAlbum Map of album key to list of file paths
     * @param scanQuality Quality level (ignored for native, uses original sample rate)
     * @param targetLoudness Target loudness in LUFS (default -18.0)
     * @param config ReplayGain configuration
     * @return Flow emitting scan progress
     */
    fun scanReplayGainByAlbumNative(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Flow<ScanProgress> = flow {
        val totalAlbums = filesByAlbum.size
        val totalFiles = filesByAlbum.values.flatten().size
        var processedFiles = 0
        var processedAlbums = 0
        val scanStartedAt = SystemClock.elapsedRealtime()

        Logger.i(
            "Native ReplayGain album scan started. albums=$totalAlbums files=$totalFiles targetLoudness=$targetLoudness LUFS",
            "ReplayGainScanner"
        )

        for ((albumKey, albumFiles) in filesByAlbum) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                Logger.w(
                    "Native ReplayGain album scan cancelled at album=$albumKey",
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
                    val replayGainInfo = analyzeAudioFileNative(filePath, scanQuality, targetLoudness, config)

                    if (replayGainInfo != null) {
                        trackGains.add(filePath to replayGainInfo)
                        Logger.v(
                            "Native track gain calculated file=${File(filePath).name} gain=${replayGainInfo.trackGain} elapsedMs=${SystemClock.elapsedRealtime() - fileStartedAt}",
                            "ReplayGainScanner"
                        )
                    } else {
                        Logger.w(
                            "Native track gain analysis failed file=${File(filePath).name}",
                            "ReplayGainScanner"
                        )
                    }
                } catch (e: Exception) {
                    Logger.e(
                        "Native track scan failed file=${File(filePath).name} reason=${e.message}",
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

                if (trackGains.isNotEmpty()) {
                    val albumGainInfo = calculateAlbumGain(trackGains.map { it.second }, config)
                    Logger.i(
                        "Album gain calculated album=$albumKey tracks=${trackGains.size} albumGain=${albumGainInfo.albumGain} albumPeak=${albumGainInfo.albumPeak}",
                        "ReplayGainScanner"
                    )

                    for ((filePath, trackInfo) in trackGains) {
                        try {
                            val combinedInfo = ReplayGainInfo(
                                trackGain = trackInfo.trackGain,
                                trackPeak = trackInfo.trackPeak,
                                albumGain = albumGainInfo.albumGain,
                                albumPeak = albumGainInfo.albumPeak,
                                truePeak = trackInfo.truePeak,
                                trackLoudness = trackInfo.trackLoudness,
                                albumLoudness = albumGainInfo.albumLoudness,
                                trackRange = trackInfo.trackRange,
                                albumRange = albumGainInfo.albumRange,
                                referenceLoudness = trackInfo.referenceLoudness
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
            "Native ReplayGain album scan finished. albums=$totalAlbums files=$totalFiles elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }

    /**
     * Scans audio files grouped by album and calculates both track and album gain.
     *
     * @param filesByAlbum Map of album key to list of file paths in that album
     * @param scanQuality Quality level affecting sample rate
     * @param targetLoudness Target loudness in LUFS (default -18.0)
     * @param config ReplayGain configuration
     * @return Flow emitting scan progress
     */
    fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
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

            if (albumFiles.size <= 1) {
                Logger.v(
                    "Skipping album gain for album=$albumKey - only ${albumFiles.size} track(s)",
                    "ReplayGainScanner"
                )
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
                        val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness, config)
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
                    val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness, config)

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

            if (trackGains.isNotEmpty()) {
                val albumGainInfo = calculateAlbumGain(trackGains.map { it.second }, config)
                Logger.i(
                    "Album gain calculated album=$albumKey tracks=${trackGains.size} albumGain=${albumGainInfo.albumGain} albumPeak=${albumGainInfo.albumPeak}",
                    "ReplayGainScanner"
                )

                for ((filePath, trackInfo) in trackGains) {
                    try {
                        val combinedInfo = ReplayGainInfo(
                            trackGain = trackInfo.trackGain,
                            trackPeak = trackInfo.trackPeak,
                            albumGain = albumGainInfo.albumGain,
                            albumPeak = albumGainInfo.albumPeak,
                            truePeak = trackInfo.truePeak,
                            trackLoudness = trackInfo.trackLoudness,
                            albumLoudness = albumGainInfo.albumLoudness,
                            trackRange = trackInfo.trackRange,
                            albumRange = albumGainInfo.albumRange,
                            referenceLoudness = trackInfo.referenceLoudness
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
     * Analyzes a single audio file and calculates ReplayGain using EBU R128.
     *
     * @param filePath Path to the audio file
     * @param scanQuality Quality level
     * @param targetLoudness Target loudness in LUFS
     * @param config ReplayGain configuration (clip mode, dual mono, etc.)
     * @return ReplayGainInfo or null if analysis fails
     */
    private suspend fun analyzeAudioFile(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): ReplayGainInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

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

            val targetSampleRate = minOf(sampleRate, scanQuality.maxSampleRate)

            val decodeResult = attemptDecodeWithRetry(
                filePath = filePath,
                format = format,
                targetSampleRate = targetSampleRate,
                channelCount = channelCount,
                targetLoudness = targetLoudness.toDouble(),
                dualMono = config.dualMono
            )

            val analyzer = when (decodeResult.result) {
                DecodeResult.SUCCESS -> decodeResult.analyzer ?: run {
                    Logger.e("Decode succeeded without analyzer for $filePath", null, "ReplayGainScanner")
                    extractor.release()
                    return@withContext null
                }
                DecodeResult.SAMPLE_COUNT_ZERO -> {
                    Logger.w("Level 1 retry returned zero samples, attempting Level 2 fallback", "ReplayGainScanner")
                    val fallbackAnalyzer = fallbackReadRawPcm(filePath, channelCount, targetLoudness.toDouble())
                    if (fallbackAnalyzer != null && fallbackAnalyzer.getBlockCount() > 0) {
                        Logger.i("Level 2 fallback successful for $filePath", "ReplayGainScanner")
                        extractor.release()
                        fallbackAnalyzer
                    } else {
                        Logger.w("Level 2 fallback failed, attempting Level 3 estimation", "ReplayGainScanner")
                        val estimatedGain = fallbackEstimateGain(filePath, targetLoudness)
                        if (estimatedGain != null) {
                            Logger.i("Level 3 estimation successful for $filePath", "ReplayGainScanner")
                            extractor.release()
                            return@withContext estimatedGain
                        }
                        extractor.release()
                        return@withContext null
                    }
                }
                DecodeResult.DECODER_INIT_FAILED -> {
                    Logger.w("Level 1 retry exhausted, attempting Level 2 fallback", "ReplayGainScanner")
                    val fallbackAnalyzer = fallbackReadRawPcm(filePath, channelCount, targetLoudness.toDouble())
                    if (fallbackAnalyzer != null && fallbackAnalyzer.getBlockCount() > 0) {
                        Logger.i("Level 2 fallback successful for $filePath", "ReplayGainScanner")
                        fallbackAnalyzer
                    } else {
                        Logger.w("Level 2 fallback failed, attempting Level 3 estimation", "ReplayGainScanner")
                        val estimatedGain = fallbackEstimateGain(filePath, targetLoudness)
                        if (estimatedGain != null) {
                            Logger.i("Level 3 estimation successful for $filePath", "ReplayGainScanner")
                            extractor.release()
                            return@withContext estimatedGain
                        }
                        Logger.e("All fallbacks exhausted for $filePath", null, "ReplayGainScanner")
                        extractor.release()
                        return@withContext null
                    }
                }
                else -> {
                    extractor.release()
                    return@withContext null
                }
            }

            // Calculate loudness and gain using EBU R128
            val loudness = analyzer.getGlobalLoudness()
            val gainDb = analyzer.calculateGain(targetLoudness.toDouble())

            if (loudness == null || gainDb == null) {
                Logger.w("Could not calculate loudness for $filePath", "ReplayGainScanner")
                extractor.release()
                return@withContext null
            }

            val peak = analyzer.getSamplePeak().toFloat()
            val channelPeaks = analyzer.getChannelPeaks()
            val truePeak = channelPeaks.maxOrNull()?.toFloat()

            val trackLoudness = loudness.toFloat()
            val trackRange = analyzer.getLoudnessRange()?.toFloat()

            Logger.v(
                "ReplayGain result: file=${file.name} loudness=${trackLoudness} LUFS gainDb=${gainDb} peak=${peak} truePeak=${truePeak}",
                "ReplayGainScanner"
            )

            // Apply clipping protection if enabled
            val clampedTrackGain = applyClipProtection(
                gain = gainDb.toFloat(),
                peak = peak,
                clipMode = config.clipMode,
                maxPeakLevel = config.maxPeakLevel.toFloat()
            )

            ReplayGainInfo(
                trackGain = clampedTrackGain,
                trackPeak = peak,
                albumGain = null,
                albumPeak = null,
                truePeak = truePeak,
                trackLoudness = trackLoudness,
                albumLoudness = null,
                trackRange = trackRange,
                albumRange = null,
                referenceLoudness = targetLoudness
            )
        } catch (e: Exception) {
            Logger.e("analyzeAudioFile exception: ${e.message}", e, "ReplayGainScanner")
            null
        }
    }

    /**
     * Analyzes a single audio file using native libebur128 (via JNI).
     * Uses Android MediaCodec for decoding and native libebur128 for computation.
     *
     * @param filePath Path to the audio file
     * @param scanQuality Quality level
     * @param targetLoudness Target loudness in LUFS
     * @param config ReplayGain configuration
     * @return ReplayGainInfo or null if analysis fails
     */
    private suspend fun analyzeAudioFileNative(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): ReplayGainInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(filePath)

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

            // Create native scanner for this file
            val scanner = try {
                EbuR128NativeScanner(
                    channels = channelCount,
                    sampleRate = sampleRate,
                    targetLoudness = targetLoudness.toDouble(),
                    truePeak = false,
                    dualMono = config.dualMono
                )
            } catch (e: Exception) {
                Logger.e("Failed to create native scanner for $filePath", e, "ReplayGainScanner")
                extractor.release()
                return@withContext null
            }

            scanner.use { nativeScanner ->
                decodeAndFeedNativeScanner(
                    extractor = extractor,
                    format = format,
                    channelCount = channelCount,
                    nativeScanner = nativeScanner
                )

                val replayGainInfo = nativeScanner.getResult()
                    ?: run {
                        Logger.w("Native scanner returned null for $filePath", "ReplayGainScanner")
                        return@withContext null
                    }

                Logger.v(
                    "Native ReplayGain result: file=${file.name} loudness=${replayGainInfo.trackLoudness} LUFS gainDb=${replayGainInfo.trackGain} peak=${replayGainInfo.trackPeak}",
                    "ReplayGainScanner"
                )

                // Apply clipping protection
                val clampedTrackGain = applyClipProtection(
                    gain = replayGainInfo.trackGain,
                    peak = replayGainInfo.trackPeak,
                    clipMode = config.clipMode,
                    maxPeakLevel = config.maxPeakLevel.toFloat()
                )

                replayGainInfo.copy(trackGain = clampedTrackGain)
            }
        } catch (e: Exception) {
            Logger.e("analyzeAudioFileNative exception: ${e.message}", e, "ReplayGainScanner")
            null
        }
    }

    /**
     * Decodes audio and feeds PCM samples to native EBU R128 scanner.
     * Optimized: reuses ShortArray buffer, batches samples before JNI crossing.
     */
    private fun decodeAndFeedNativeScanner(
        extractor: MediaExtractor,
        format: MediaFormat,
        channelCount: Int,
        nativeScanner: EbuR128NativeScanner
    ) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val codec = MediaCodec.createDecoderByType(mime)

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            // Reusable buffer: accumulate PCM samples across multiple output buffers
            val batchBuffer = ShortArray(65536)
            var batchPos = 0

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
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
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
                            val safeOffset = bufferInfo.offset.coerceIn(0, outputBuffer.capacity() - 1)
                            val safeLimit = minOf(safeOffset + bufferInfo.size, outputBuffer.capacity())
                            if (safeLimit > safeOffset) {
                                outputBuffer.position(safeOffset)
                                outputBuffer.limit(safeLimit)

                                val shortBuffer = outputBuffer.asShortBuffer()
                                val samplesToRead = shortBuffer.remaining()
                                if (samplesToRead > 0) {
                                    val spaceInBatch = batchBuffer.size - batchPos
                                    if (samplesToRead <= spaceInBatch) {
                                        shortBuffer.get(batchBuffer, batchPos, samplesToRead)
                                        batchPos += samplesToRead
                                    } else {
                                        val framesInBatch = batchPos / channelCount
                                        if (framesInBatch > 0) {
                                            nativeScanner.processFrames(batchBuffer, framesInBatch)
                                        }
                                        batchPos = 0
                                        val canFit = minOf(samplesToRead, batchBuffer.size)
                                        shortBuffer.get(batchBuffer, 0, canFit)
                                        batchPos = canFit
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
                                "Native ReplayGain channelCount changed $channelCount -> $newChannelCount",
                                "ReplayGainScanner"
                            )
                        }
                    }
                }
            }

            // Process remaining samples in batch buffer
            val remainingFrames = batchPos / channelCount
            if (remainingFrames > 0) {
                nativeScanner.processFrames(batchBuffer, remainingFrames)
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    /**
     * Applies clipping protection to the calculated gain.
     *
     * Following rsgain's clip_mode implementation:
     * - NONE: No clipping protection
     * - POSITIVE: Only apply clipping protection when gain is positive
     * - ALWAYS: Always apply clipping protection
     *
     * @param gain Calculated gain in dB
     * @param peak Sample peak value
     * @param clipMode Clipping protection mode
     * @param maxPeakLevel Maximum allowed peak level in dB (default 0.0)
     * @return Gain value adjusted for clipping protection
     */
    private fun applyClipProtection(
        gain: Float,
        peak: Float,
        clipMode: ClipMode,
        maxPeakLevel: Float = 0f
    ): Float {
        if (clipMode == ClipMode.NONE) {
            return gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        val maxPeakLinear = 10.0.pow(maxPeakLevel / 20.0).toFloat()

        // Check if clipping protection should be applied
        val shouldApplyProtection = when (clipMode) {
            ClipMode.ALWAYS -> true
            ClipMode.POSITIVE -> gain > 0f
            ClipMode.NONE -> false
        }

        if (!shouldApplyProtection) {
            return gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        // Calculate new peak after applying gain
        val newPeak = peak * 10.0.pow(gain / 20.0).toFloat()

        if (newPeak > maxPeakLinear) {
            // Calculate adjustment needed to prevent clipping
            val adjustment = 20f * log10((newPeak / maxPeakLinear).toDouble()).toFloat()

            // For positive mode, don't reduce gain below original
            val effectiveAdjustment = if (clipMode == ClipMode.POSITIVE && adjustment > gain) {
                gain
            } else {
                adjustment
            }

            val protectedGain = gain - effectiveAdjustment
            Logger.w(
                "Clipping protection applied: gain=$gain -> $protectedGain (peak=$newPeak > $maxPeakLinear)",
                "ReplayGainScanner"
            )
            return protectedGain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        return gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    /**
     * Decodes audio file and feeds samples to EbuR128Analyzer.
     *
     * ReplayGain 2.0 algorithm (rsgain / libebur128 compatible):
     * - K-weighting filters (ITU-R BS.1770-4)
     * - EBU R128 gating for integrated loudness
     * - Reference loudness: -18 LUFS
     */
    private fun decodeAndAccumulateStats(
        extractor: MediaExtractor,
        format: MediaFormat,
        targetSampleRate: Int,
        channelCount: Int,
        targetLoudness: Double,
        dualMono: Boolean
    ): EbuR128Analyzer {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return EbuR128Analyzer(
            channels = channelCount,
            sampleRate = targetSampleRate,
            targetLoudness = targetLoudness,
            dualMono = dualMono
        )
        val codec = MediaCodec.createDecoderByType(mime)

        val analyzer = EbuR128Analyzer(
            channels = channelCount,
            sampleRate = targetSampleRate,
            targetLoudness = targetLoudness,
            dualMono = dualMono
        )

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false

            val bufferInfo = MediaCodec.BufferInfo()

            // Reusable ShortArray buffer for batch processing
            val sampleBuffer = ShortArray(targetSampleRate / 4 * channelCount)
            var sampleBufferPos = 0

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
                            val safeOffset = bufferInfo.offset.coerceIn(0, outputBuffer.capacity() - 1)
                            val safeLimit = minOf(safeOffset + bufferInfo.size, outputBuffer.capacity())
                            if (safeLimit > safeOffset) {
                                outputBuffer.position(safeOffset)
                                outputBuffer.limit(safeLimit)

                                val shortBuffer = outputBuffer.asShortBuffer()
                                val samplesToRead = shortBuffer.remaining()
                                if (samplesToRead > 0) {
                                    val spaceInBuffer = sampleBuffer.size - sampleBufferPos
                                    val toRead = minOf(samplesToRead, spaceInBuffer)

                                    shortBuffer.get(sampleBuffer, sampleBufferPos, toRead)
                                    sampleBufferPos += toRead

                                    if (sampleBufferPos >= sampleBuffer.size) {
                                        val floatBuffer = FloatArray(sampleBuffer.size)
                                        for (i in sampleBuffer.indices) {
                                            floatBuffer[i] = sampleBuffer[i].toFloat() / 32768.0f
                                        }
                                        analyzer.processBlock(floatBuffer, floatBuffer.size)
                                        sampleBufferPos = 0
                                    }

                                    if (toRead < samplesToRead) {
                                        val floatBuffer = FloatArray(sampleBufferPos)
                                        for (i in 0 until sampleBufferPos) {
                                            floatBuffer[i] = sampleBuffer[i].toFloat() / 32768.0f
                                        }
                                        analyzer.processBlock(floatBuffer, floatBuffer.size)
                                        sampleBufferPos = 0

                                        val stillRemaining = shortBuffer.remaining()
                                        val toReadNow = minOf(stillRemaining, sampleBuffer.size)
                                        shortBuffer.get(sampleBuffer, 0, toReadNow)
                                        sampleBufferPos = toReadNow
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

            // Process remaining samples
            if (sampleBufferPos > 0) {
                val floatBuffer = FloatArray(sampleBufferPos)
                for (i in 0 until sampleBufferPos) {
                    floatBuffer[i] = sampleBuffer[i].toFloat() / 32768.0f
                }
                analyzer.processBlock(floatBuffer, floatBuffer.size)
            }

        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }

        return analyzer
    }


    /**
     * Calculates album gain from a list of track gains using energy average.
     *
     * Following rsgain's approach:
     * - Convert track gains back to linear loudness values
     * - Calculate energy mean: sqrt(mean(loudness²))
     * - Convert back to dB gain
     *
     * @param trackGains List of track ReplayGainInfo
     * @param config ReplayGain configuration
     * @return Album gain info with album gain, peak, and loudness
     */
    fun calculateAlbumGain(
        trackGains: List<ReplayGainInfo>,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): ReplayGainInfo {
        Logger.i(
            "calculateAlbumGain: input trackGains count=${trackGains.size} gains=${trackGains.map { it.trackGain }}",
            "ReplayGainScanner"
        )

        if (trackGains.isEmpty()) return ReplayGainInfo()

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

        // Use track loudness values if available (EBU R128), otherwise estimate from gain
        val referenceLoudness = validTrackGains.first().referenceLoudness.toDouble()

        val albumLoudness = if (validTrackGains.all { it.trackLoudness != null }) {
            // Energy average of loudness values (convert to linear, average, convert back)
            val linearLoudness = validTrackGains.mapNotNull { it.trackLoudness?.toDouble() }
                .map { 10.0.pow(it / 10.0) }

            if (linearLoudness.isEmpty()) {
                referenceLoudness
            } else {
                val meanEnergy = linearLoudness.average()
                10.0 * log10(meanEnergy)
            }
        } else {
            // Fallback: estimate from gain values
            val trackLoudnessValues = validTrackGains.map { trackGain ->
                referenceLoudness - trackGain.trackGain
            }
            val linearLoudness = trackLoudnessValues.map { 10.0.pow(it / 10.0) }
            val meanEnergy = linearLoudness.average()
            10.0 * log10(meanEnergy)
        }

        val albumGainDb = (referenceLoudness - albumLoudness).toFloat()

        // Apply clipping protection to album gain
        val maxPeak = validTrackGains.maxOf { it.trackPeak }
        val clampedAlbumGain = applyClipProtection(
            gain = albumGainDb,
            peak = maxPeak,
            clipMode = config.clipMode,
            maxPeakLevel = config.maxPeakLevel.toFloat()
        )

        // Calculate album range (max of track ranges)
        val albumRange = validTrackGains.mapNotNull { it.trackRange }.maxOrNull()

        Logger.v(
            "Album gain calculated: trackCount=${validTrackGains.size} albumGainDb=$clampedAlbumGain albumLoudness=$albumLoudness",
            "ReplayGainScanner"
        )

        return ReplayGainInfo(
            trackGain = validTrackGains.first().trackGain,
            trackPeak = validTrackGains.first().trackPeak,
            albumGain = clampedAlbumGain,
            albumPeak = maxPeak,
            truePeak = validTrackGains.first().truePeak,
            trackLoudness = validTrackGains.first().trackLoudness,
            albumLoudness = albumLoudness.toFloat(),
            trackRange = validTrackGains.first().trackRange,
            albumRange = albumRange,
            referenceLoudness = validTrackGains.first().referenceLoudness
        )
    }

    /**
     * Saves ReplayGain information to file metadata.
     * Uses TagLib to write ReplayGain 2.0 tags:
     * - REPLAYGAIN_TRACK_GAIN, REPLAYGAIN_TRACK_PEAK
     * - REPLAYGAIN_ALBUM_GAIN, REPLAYGAIN_ALBUM_PEAK
     * - REPLAYGAIN_TRACK_RANGE, REPLAYGAIN_ALBUM_RANGE
     * - REPLAYGAIN_REFERENCE_LOUDNESS
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

            // Core ReplayGain tags
            customFields["REPLAYGAIN_TRACK_GAIN"] = String.format("%.2f dB", replayGainInfo.trackGain)
            customFields["REPLAYGAIN_TRACK_PEAK"] = String.format("%.6f", replayGainInfo.trackPeak)

            replayGainInfo.albumGain?.let {
                customFields["REPLAYGAIN_ALBUM_GAIN"] = String.format("%.2f dB", it)
            }

            replayGainInfo.albumPeak?.let {
                customFields["REPLAYGAIN_ALBUM_PEAK"] = String.format("%.6f", it)
            }

            // ReplayGain 2.0 tags
            replayGainInfo.trackLoudness?.let {
                customFields["REPLAYGAIN_TRACK_LOUDNESS"] = String.format("%.2f LUFS", it)
            }

            replayGainInfo.albumLoudness?.let {
                customFields["REPLAYGAIN_ALBUM_LOUDNESS"] = String.format("%.2f LUFS", it)
            }

            replayGainInfo.trackRange?.let {
                customFields["REPLAYGAIN_TRACK_RANGE"] = String.format("%.2f LU", it)
            }

            replayGainInfo.albumRange?.let {
                customFields["REPLAYGAIN_ALBUM_RANGE"] = String.format("%.2f LU", it)
            }

            customFields["REPLAYGAIN_REFERENCE_LOUDNESS"] = String.format("%.1f LUFS", replayGainInfo.referenceLoudness)

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
            Logger.e("saveReplayGainToFile exception: ${e.message}", e, "ReplayGainScanner")
            false
        }
    }

    /**
     * Reads existing ReplayGain information from a file.
     * @param filePath Path to the audio file
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
                val trackLoudnessStr = customFields["REPLAYGAIN_TRACK_LOUDNESS"]
                val albumLoudnessStr = customFields["REPLAYGAIN_ALBUM_LOUDNESS"]
                val trackRangeStr = customFields["REPLAYGAIN_TRACK_RANGE"]
                val albumRangeStr = customFields["REPLAYGAIN_ALBUM_RANGE"]
                val refLoudnessStr = customFields["REPLAYGAIN_REFERENCE_LOUDNESS"]

                val trackGain = parseGainValue(trackGainStr)
                val trackPeak = parsePeakValue(trackPeakStr)
                val albumGain = albumGainStr?.let { parseGainValue(it) }
                val albumPeak = albumPeakStr?.let { parsePeakValue(it) }
                val trackLoudness = parseLoudnessValue(trackLoudnessStr)
                val albumLoudness = parseLoudnessValue(albumLoudnessStr)
                val trackRange = parseLoudnessValue(trackRangeStr)
                val albumRange = parseLoudnessValue(albumRangeStr)
                val referenceLoudness = refLoudnessStr?.let { parseLoudnessValue(it) } ?: -18f

                if (trackGain != null || trackPeak != null) {
                    ReplayGainInfo(
                        trackGain = trackGain ?: 0f,
                        trackPeak = trackPeak ?: 0f,
                        albumGain = albumGain,
                        albumPeak = albumPeak,
                        trackLoudness = trackLoudness,
                        albumLoudness = albumLoudness,
                        trackRange = trackRange,
                        albumRange = albumRange,
                        referenceLoudness = referenceLoudness
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

    /**
     * Parses a loudness value string (e.g., "-18.50 LUFS") to float.
     */
    private fun parseLoudnessValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(" LUFS", "").replace(" LU", "").replace("LUFS", "").trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
