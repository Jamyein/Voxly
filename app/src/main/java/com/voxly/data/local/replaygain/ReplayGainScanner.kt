package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.pow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReplayGain scanner using native libebur128 (via JNI) for EBU R128 loudness analysis.
 * Uses Android MediaCodec for audio decoding.
 *
 * Matches rsgain's behavior exactly since it uses the same libebur128 library.
 * ReplayGain 2.0 standard: -18 LUFS reference loudness.
 */
@Singleton
class ReplayGainScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor
) {

    companion object {
        const val REFERENCE_LUFS = -18.0
        const val SAMPLES_PER_CHUNK = 4096
        private const val DECODE_TIMEOUT_US = 100_000L
        const val MIN_GAIN_DB = -50f
        const val MAX_GAIN_DB = 50f
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
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
                    "Analyzing ReplayGain file=${File(filePath).name} path=$filePath",
                    "ReplayGainScanner"
                )
                val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness, config)

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
    }.sample(50)

    /**
     * Scans audio files with album grouping.
     * Reads metadata from each file to group by album, then calculates both track and album gain.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Flow<ScanProgress> = flow {
        val scanStartedAt = SystemClock.elapsedRealtime()
        val totalFiles = filePaths.size

        Logger.i(
            "ReplayGain album grouping started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS",
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

        Logger.i(
            "Grouped $totalFiles files into ${filesByAlbum.size} albums",
            "ReplayGainScanner"
        )

        scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness, config).collect { progress ->
            emit(progress)
        }

        Logger.i(
            "ReplayGain album grouping finished. elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }

    /**
     * Scans audio files grouped by album and calculates both track and album gain.
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
            "ReplayGain album scan started. albums=$totalAlbums files=$totalFiles targetLoudness=$targetLoudness LUFS",
            "ReplayGainScanner"
        )

        for ((albumKey, albumFiles) in filesByAlbum) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                Logger.w(
                    "ReplayGain album scan cancelled at album=$albumKey",
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

            for ((_, filePath) in albumFiles.withIndex()) {
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
                        val saved = saveReplayGainToFile(filePath, combinedInfo)
                        if (saved) {
                            emit(
                                ScanProgress(
                                    currentFile = totalFiles,
                                    totalFiles = totalFiles,
                                    percentage = 1f,
                                    currentFilePath = filePath,
                                    status = ScanStatus.COMPLETED,
                                    replayGainInfo = combinedInfo
                                )
                            )
                        }
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

        Logger.i(
            "ReplayGain album scan finished. albums=$totalAlbums files=$totalFiles elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}",
            "ReplayGainScanner"
        )
    }.sample(50)

    /**
     * Analyzes a single audio file using native libebur128 (via JNI).
     * Uses Android MediaCodec for decoding and native libebur128 for computation.
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
                decodeAndFeedScanner(
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

                val clampedTrackGain = applyClipProtection(
                    gain = replayGainInfo.trackGain,
                    peak = replayGainInfo.trackPeak,
                    clipMode = config.clipMode,
                    maxPeakLevel = config.maxPeakLevel.toFloat()
                )

                replayGainInfo.copy(trackGain = clampedTrackGain)
            }
        } catch (e: Exception) {
            Logger.e("analyzeAudioFile exception: ${e.message}", e, "ReplayGainScanner")
            null
        }
    }

    /**
     * Decodes audio and feeds PCM samples to native EBU R128 scanner.
     * Uses Direct ByteBuffer for zero-copy JNI transfer.
     * Uses hardware-accelerated decoder when available.
     */
    private fun decodeAndFeedScanner(
        extractor: MediaExtractor,
        format: MediaFormat,
        channelCount: Int,
        nativeScanner: EbuR128NativeScanner
    ) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val codec = findBestDecoder(mime)?.let { name ->
            try {
                MediaCodec.createByCodecName(name).also {
                    Logger.i("Using decoder: $name (hw=${isHardwareAccelerated(name)})", "ReplayGainScanner")
                }
            } catch (e: Exception) {
                Logger.w("Failed to create codec $name, falling back: ${e.message}", "ReplayGainScanner")
                null
            }
        } ?: MediaCodec.createDecoderByType(mime).also {
            Logger.i("Using default decoder for $mime", "ReplayGainScanner")
        }

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()

            val batchBuffer = ByteBuffer.allocateDirect(256 * 1024)
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
                            val safeOffset = bufferInfo.offset.coerceIn(0, outputBuffer.capacity() - 1)
                            val safeLimit = minOf(safeOffset + bufferInfo.size, outputBuffer.capacity())
                            if (safeLimit > safeOffset) {
                                outputBuffer.position(safeOffset)
                                outputBuffer.limit(safeLimit)

                                val bytesToRead = outputBuffer.remaining()
                                val spaceInBatch = batchBuffer.capacity() - batchPos

                                if (bytesToRead <= spaceInBatch) {
                                    batchBuffer.put(outputBuffer)
                                    batchPos += bytesToRead
                                } else {
                                    if (batchPos > 0) {
                                        batchBuffer.flip()
                                        nativeScanner.processBuffer(batchBuffer, batchPos)
                                        batchBuffer.clear()
                                        batchPos = 0
                                    }

                                    val canFit = minOf(bytesToRead, batchBuffer.capacity())
                                    val oldLimit = outputBuffer.limit()
                                    outputBuffer.limit(outputBuffer.position() + canFit)
                                    batchBuffer.put(outputBuffer)
                                    batchPos = canFit
                                    outputBuffer.limit(oldLimit)
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

            if (batchPos > 0) {
                batchBuffer.flip()
                nativeScanner.processBuffer(batchBuffer, batchPos)
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    /**
     * Finds the best available decoder for the given MIME type, preferring hardware decoders.
     */
    private fun findBestDecoder(mimeType: String): String? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = codecList.codecInfos

        val hardwareDecoders = codecs.filter {
            it.isEncoder.not() && it.isHardwareAccelerated && it.supportsType(mimeType)
        }
        if (hardwareDecoders.isNotEmpty()) {
            return hardwareDecoders.first().name
        }

        val softwareDecoders = codecs.filter {
            it.isEncoder.not() && it.supportsType(mimeType)
        }
        return softwareDecoders.firstOrNull()?.name
    }

    /**
     * Checks if a codec is hardware accelerated.
     */
    private fun isHardwareAccelerated(codecName: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.find { it.name == codecName }?.isHardwareAccelerated ?: false
    }

    /**
     * Checks if a codec supports the given MIME type.
     */
    private fun MediaCodecInfo.supportsType(mimeType: String): Boolean {
        return try {
            getCapabilitiesForType(mimeType) != null
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Applies clipping protection to the calculated gain.
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

        val shouldApplyProtection = when (clipMode) {
            ClipMode.ALWAYS -> true
            ClipMode.POSITIVE -> gain > 0f
            ClipMode.NONE -> false
        }

        if (!shouldApplyProtection) {
            return gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        val newPeak = peak * 10.0.pow(gain / 20.0).toFloat()

        if (newPeak > maxPeakLinear) {
            val adjustment = 20f * log10((newPeak / maxPeakLinear).toDouble()).toFloat()
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
     * Calculates album gain from a list of track gains using energy average.
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

        val referenceLoudness = validTrackGains.first().referenceLoudness.toDouble()

        val albumLoudness = if (validTrackGains.all { it.trackLoudness != null }) {
            val linearLoudness = validTrackGains.mapNotNull { it.trackLoudness?.toDouble() }
                .map { 10.0.pow(it / 10.0) }

            if (linearLoudness.isEmpty()) {
                referenceLoudness
            } else {
                val meanEnergy = linearLoudness.average()
                10.0 * log10(meanEnergy)
            }
        } else {
            val trackLoudnessValues = validTrackGains.map { trackGain ->
                referenceLoudness - trackGain.trackGain
            }
            val linearLoudness = trackLoudnessValues.map { 10.0.pow(it / 10.0) }
            val meanEnergy = linearLoudness.average()
            10.0 * log10(meanEnergy)
        }

        val albumGainDb = (referenceLoudness - albumLoudness).toFloat()

        val maxPeak = validTrackGains.maxOf { it.trackPeak }
        val clampedAlbumGain = applyClipProtection(
            gain = albumGainDb,
            peak = maxPeak,
            clipMode = config.clipMode,
            maxPeakLevel = config.maxPeakLevel.toFloat()
        )

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
     */
    suspend fun saveReplayGainToFile(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            val existingMetadata = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false, bypassCache = true)?.metadata
                ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)

            val customFields = existingMetadata?.customFields?.toMutableMap() ?: mutableMapOf()

            customFields["REPLAYGAIN_TRACK_GAIN"] = String.format("%.2f dB", replayGainInfo.trackGain)
            customFields["REPLAYGAIN_TRACK_PEAK"] = String.format("%.6f", replayGainInfo.trackPeak)

            replayGainInfo.albumGain?.let {
                customFields["REPLAYGAIN_ALBUM_GAIN"] = String.format("%.2f dB", it)
            }

            replayGainInfo.albumPeak?.let {
                customFields["REPLAYGAIN_ALBUM_PEAK"] = String.format("%.6f", it)
            }

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
     */
    suspend fun readReplayGainFromFile(filePath: String): ReplayGainInfo? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null

                val metadata = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = false, bypassCache = true)?.metadata
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
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

    private fun parseGainValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(" dB", "").replace("dB", "").trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePeakValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLoudnessValue(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(" LUFS", "").replace(" LU", "").replace("LUFS", "").trim().toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
