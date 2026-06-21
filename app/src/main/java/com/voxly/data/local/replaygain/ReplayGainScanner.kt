package com.voxly.data.local.replaygain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import timber.log.Timber
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ClipMode
import com.voxly.domain.model.ReplayGainConfig
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import com.voxly.domain.repository.ScanStatus
import com.voxly.data.local.replaygain.native.EbuR128NativeScanner
import com.voxly.data.local.replaygain.native.NativeAudioDecoder
import com.voxly.data.local.replaygain.native.NativeFlacDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
    private val metadataProcessor: TagLibMetadataProcessor,
    private val albumGroupingProvider: AlbumGroupingProvider,
    private val cachedAudioFileDao: com.voxly.data.local.cache.CachedAudioFileDao
) {

    companion object {
        const val REFERENCE_LUFS = -18.0
        const val SAMPLES_PER_CHUNK = 4096
        // 10ms instead of 100ms — tighter end-of-stream detection matters when the
        // decoder has just consumed the last packet. The previous 100ms wait
        // multiplied across many drain loops at the tail of each file.
        private const val DECODE_TIMEOUT_US = 10_000L
        const val MIN_GAIN_DB = -50f
        const val MAX_GAIN_DB = 50f
        const val BATCH_BUFFER_SIZE = 8 * 1024 * 1024 // 8MB batch buffer
        val MAX_CONCURRENT_SCANS = Runtime.getRuntime().availableProcessors()
            .coerceIn(2, 6)
        @Volatile
        private var nativeScannerAvailable = true

        // LRU cache for scan results to avoid repeated scans
        // Key: filePath, Value: ReplayGainInfo
        private val scanResultCache = object : LinkedHashMap<String, ReplayGainInfo>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReplayGainInfo>): Boolean {
                return size > 100 // Max 100 entries
            }
        }

        @Synchronized
        fun getCachedResult(filePath: String): ReplayGainInfo? {
            val cached = scanResultCache[filePath]
            // Reject cached results with invalid gain (decoder failure, no audio data)
            if (cached != null && !cached.trackGain.isFinite()) {
                scanResultCache.remove(filePath)
                return null
            }
            return cached
        }

        @Synchronized
        fun cacheResult(filePath: String, result: ReplayGainInfo) {
            // Don't cache non-finite results (decoder failure produced no audio data)
            if (!result.trackGain.isFinite()) {
                Timber.w("Refusing to cache invalid ReplayGain for $filePath: gain=${result.trackGain}")
                return
            }
            scanResultCache[filePath] = result
        }

        @Synchronized
        fun clearCache() {
            scanResultCache.clear()
        }
    }

    private val scanSemaphore = Semaphore(MAX_CONCURRENT_SCANS)
    private val codecPool = CodecPool()
    private val nativeFlacDecoder = NativeFlacDecoder()
private val nativeAudioDecoder = NativeAudioDecoder()

    /**
     * Finds a hardware-accelerated decoder for the given MIME type.
     * Returns null if no hardware decoder is available — caller should fall back
     * to MediaCodecList.findDecoderForFormat (which may return a software decoder).
     */
    private fun findHardwareDecoder(mime: String): String? {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .firstOrNull { info ->
                !info.isAlias &&
                    info.isHardwareAccelerated &&
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }?.name
    }

    /**
     * Pool that reuses MediaCodec instances across same-format files within an album scan.
     * Keyed by MIME type. On [acquire], a cached codec (in Stopped state) is reconfigured
     * and restarted via configure() + start(). On [release], the codec is stopped and
     * returned to the pool only if stop() succeeded cleanly. Callers MUST call [closeAll]
     * when the scan is done to release native resources.
     *
     * Decoder priority: 1) hardware-accelerated (isHardwareAccelerated, API 29+)
     *                   2) MediaCodecList.findDecoderForFormat (official recommended API)
     *                   3) createDecoderByType (last resort)
     */
    private inner class CodecPool {
        private val pool = mutableMapOf<String, MediaCodec>()
        private val mutex = Mutex()

        suspend fun acquire(
            mime: String,
            format: MediaFormat,
            callback: MediaCodec.Callback? = null
        ): MediaCodec? = withContext(Dispatchers.IO) {
            val existing = mutex.withLock { pool.remove(mime) }
            if (existing != null) {
                try {
                    if (callback != null) existing.setCallback(callback)
                    existing.configure(format, null, null, 0)
                    existing.start()
                    Timber.v("CodecPool: reused codec for $mime")
                    return@withContext existing
                } catch (e: Exception) {
                    Timber.w("CodecPool: reuse failed for $mime, creating new: ${e.message}")
                    try { existing.release() } catch (_: Exception) {}
                }
            }

            // Pass 1: hardware-accelerated decoder (API 29+, minSdk=30)
            val hwName = findHardwareDecoder(mime)
            if (hwName != null) {
                try {
                    val codec = MediaCodec.createByCodecName(hwName)
                    if (callback != null) codec.setCallback(callback)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    return@withContext codec
                } catch (e: Exception) {
                    Timber.w("CodecPool: hw decoder $hwName failed, falling back: ${e.message}")
                }
            }

            // Pass 2: findDecoderForFormat (official recommended API)
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val decoderName = codecList.findDecoderForFormat(format)
            if (decoderName != null) {
                try {
                    val codec = MediaCodec.createByCodecName(decoderName)
                    if (callback != null) codec.setCallback(callback)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    return@withContext codec
                } catch (e: Exception) {
                    Timber.w("CodecPool: createByCodecName($decoderName) failed: ${e.message}")
                }
            }

            // Pass 3: createDecoderByType (last resort)
            try {
                val codec = MediaCodec.createDecoderByType(mime)
                if (callback != null) codec.setCallback(callback)
                codec.configure(format, null, null, 0)
                codec.start()
                return@withContext codec
            } catch (e: Exception) {
                Timber.w("CodecPool: createDecoderByType($mime) also failed: ${e.message}")
                null
            }
        }

        suspend fun release(codec: MediaCodec, mime: String) = withContext(Dispatchers.IO) {
            val stoppedCleanly = try { codec.stop(); true } catch (_: Exception) { false }
            if (stoppedCleanly) {
                mutex.withLock { pool[mime] = codec }
            } else {
                try { codec.release() } catch (_: Exception) {}
            }
        }

        suspend fun closeAll() = withContext(Dispatchers.IO) {
            mutex.withLock {
                pool.values.forEach { try { it.release() } catch (_: Exception) {} }
                pool.clear()
            }
        }
    }

    /**
     * Scans audio files and calculates ReplayGain values.
     * Optimized with parallel processing for multi-core CPUs.
     */
    @OptIn(FlowPreview::class)
    fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Flow<ScanProgress> = flow {
        try {
        val totalFiles = filePaths.size
        val scanStartedAt = SystemClock.elapsedRealtime()
        Timber.i(
            "ReplayGain scan started. files=$totalFiles quality=$scanQuality targetLoudness=$targetLoudness LUFS clipMode=${config.clipMode}"
        )

        emit(
            ScanProgress(
                currentFile = 0,
                totalFiles = totalFiles,
                percentage = 0f,
                currentFilePath = "Starting parallel scan...",
                status = ScanStatus.SCANNING
            )
        )

        // Process files in parallel batches
        val results = mutableMapOf<Int, Pair<String, ReplayGainInfo?>>()

        coroutineScope {
            val jobs = filePaths.mapIndexed { index, filePath ->
                async(Dispatchers.IO) {
                    val result = processSingleFile(filePath, scanQuality, targetLoudness, config, index, totalFiles)
                    index to result
                }
            }
            jobs.awaitAll().forEach { job ->
                val idx = job.first
                val fileResult = job.second
                results[idx] = fileResult
            }
        }

        // Emit results in order
        results.toSortedMap().forEach { (index, pair) ->
            val (filePath, replayGainInfo) = pair
            if (replayGainInfo != null) {
                saveReplayGainToFile(filePath, replayGainInfo)
            }
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
        }

        Timber.i(
            "ReplayGain scan finished. files=$totalFiles processed=${results.size} elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}"
        )
        } finally {
            codecPool.closeAll()
        }
    }

    /**
     * Process a single file for scanReplayGain.
     * Returns Pair of (filePath, ReplayGainInfo).
     */
    private suspend fun processSingleFile(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: ReplayGainConfig,
        index: Int,
        totalFiles: Int
    ): Pair<String, ReplayGainInfo?> {
        val fileStartedAt = SystemClock.elapsedRealtime()
        Timber.v("Analyzing ReplayGain file=${File(filePath).name} path=$filePath")

        val replayGainInfo = analyzeAudioFile(filePath, scanQuality, targetLoudness, config)

        if (replayGainInfo != null) {
            Timber.i(
                "ReplayGain success file=${File(filePath).name} gain=${replayGainInfo.trackGain} peak=${replayGainInfo.trackPeak} elapsedMs=${SystemClock.elapsedRealtime() - fileStartedAt}"
            )
        } else {
            Timber.w(
                "ReplayGain failed file=${File(filePath).name} reason=analyze_returned_null"
            )
        }

        return filePath to replayGainInfo
    }

    /**
     * Scans audio files with album grouping.
     * Reads metadata from each file to group by album, then calculates both track and album gain.
     */
    @OptIn(FlowPreview::class)
    fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Flow<ScanProgress> = flow {
        try {
        val scanStartedAt = SystemClock.elapsedRealtime()
        Timber.i(
            "ReplayGain album grouping started. files=${filePaths.size} quality=$scanQuality targetLoudness=$targetLoudness LUFS"
        )

        emit(
            ScanProgress(
                currentFile = 0,
                totalFiles = filePaths.size,
                percentage = 0f,
                currentFilePath = "Grouping by album...",
                status = ScanStatus.SCANNING
            )
        )

        val filesByAlbum = albumGroupingProvider.groupByAlbum(filePaths)

        Timber.i("Grouped ${filePaths.size} files into ${filesByAlbum.size} albums")

        scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness, config).collect { progress ->
            emit(progress)
        }

        Timber.i(
            "ReplayGain album grouping finished. elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}"
        )
        } finally {
            codecPool.closeAll()
        }
    }

    /**
     * Scans audio files grouped by album and calculates both track and album gain.
     */
    @OptIn(FlowPreview::class)
    fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float = -18f,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Flow<ScanProgress> = flow {
        try {
        val totalAlbums = filesByAlbum.size
        val totalFiles = filesByAlbum.values.flatten().size
        var processedFiles = 0
        var processedAlbums = 0
        val scanStartedAt = SystemClock.elapsedRealtime()

        Timber.i(
            "ReplayGain album scan started. albums=$totalAlbums files=$totalFiles targetLoudness=$targetLoudness LUFS"
        )

        for ((albumKey, albumFiles) in filesByAlbum) {
            if (!kotlin.coroutines.coroutineContext.isActive) {
                Timber.w(
                    "ReplayGain album scan cancelled at album=$albumKey"
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
            val trackScanners = mutableListOf<EbuR128NativeScanner>()

            coroutineScope {
                val deferred = albumFiles.map { filePath ->
                    async(Dispatchers.IO) {
                        val (scanner, trackInfo) = analyzeAudioFileKeepScanner(filePath, scanQuality, targetLoudness, config)
                        Triple(filePath, scanner, trackInfo)
                    }
                }

                for (deferredResult in deferred) {
                    kotlin.coroutines.coroutineContext.ensureActive()

                    val (filePath, scanner, replayGainInfo) = deferredResult.await()
                    processedFiles++

                    if (scanner != null) {
                        synchronized(trackScanners) { trackScanners.add(scanner) }
                    }
                    if (replayGainInfo != null) {
                        trackGains.add(filePath to replayGainInfo)
                        Timber.v("Track gain calculated file=${File(filePath).name} gain=${replayGainInfo.trackGain}")
                    } else {
                        Timber.w("Track gain analysis failed file=${File(filePath).name}")
                    }

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
            }

            // Check cancellation before album gain calculation
            kotlin.coroutines.coroutineContext.ensureActive()

            // Determine if all tracks were freshly scanned (have native scanners)
            // Cached tracks return null scanner, so album gain can only use native
            // ebur128_loudness_global_multiple when ALL tracks were freshly scanned.
            val allTracksFreshlyScanned = trackScanners.size == trackGains.size

            if (allTracksFreshlyScanned && trackScanners.isNotEmpty()) {
                // Use EBU R128 compliant native calculation (ebur128_loudness_global_multiple)
                val albumResult = try {
                    EbuR128NativeScanner.getAlbumGain(trackScanners)
                } finally {
                    trackScanners.forEach { try { it.close() } catch (_: Exception) {} }
                }

                if (albumResult == null) {
                    Timber.w("getAlbumGain returned null for album=$albumKey, using fallback values")
                }

                val albumLoudness = albumResult?.get(0) ?: targetLoudness.toDouble()
                val albumRange = albumResult?.get(1) ?: 0.0
                val albumPeak = albumResult?.get(2) ?: 0.0
                val albumGainDb = (targetLoudness.toDouble() - albumLoudness).toFloat()
                val maxPeak = albumPeak.toFloat()

                val clampedAlbumGain = applyClipProtection(
                    gain = albumGainDb,
                    peak = maxPeak,
                    clipMode = config.clipMode,
                    maxPeakLevel = config.maxPeakLevel.toFloat()
                )

                Timber.i("Album gain (EBU R128) album=$albumKey tracks=${trackGains.size} albumGain=$clampedAlbumGain albumLoudness=$albumLoudness")

                for ((filePath, trackInfo) in trackGains) {
                    try {
                        val combinedInfo = ReplayGainInfo(
                            trackGain = trackInfo.trackGain,
                            trackPeak = trackInfo.trackPeak,
                            albumGain = clampedAlbumGain,
                            albumPeak = maxPeak,
                            truePeak = trackInfo.truePeak,
                            trackLoudness = trackInfo.trackLoudness,
                            albumLoudness = albumLoudness.toFloat(),
                            trackRange = trackInfo.trackRange,
                            albumRange = albumRange.toFloat(),
                            referenceLoudness = trackInfo.referenceLoudness
                        )
                        val saved = saveReplayGainToFile(filePath, combinedInfo)
                        if (!saved) {
                            Timber.w("Album gain save failed but analysis complete file=$filePath")
                        }
                        emit(
                            ScanProgress(
                                currentFile = processedFiles,
                                totalFiles = totalFiles,
                                percentage = processedFiles.toFloat() / totalFiles,
                                currentFilePath = filePath,
                                status = ScanStatus.TRACK_COMPLETED,
                                replayGainInfo = combinedInfo
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save album gain for file=$filePath reason=${e.message}")
                    }
                }

                emit(
                    ScanProgress(
                        currentFile = processedFiles,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = "",
                        status = ScanStatus.ALBUM_COMPLETED
                    )
                )

            } else if (trackGains.isNotEmpty()) {
                // Some or all tracks were cached (no scanner available).
                // Fall back to energy-average album gain calculation.
                val gainValues = trackGains.map { it.second }
                val (albumLoudness, albumRange, albumPeak) = calculateEnergyAverageAlbumGain(gainValues, targetLoudness)
                val albumGainDb = targetLoudness - albumLoudness

                val clampedAlbumGain = applyClipProtection(
                    gain = albumGainDb,
                    peak = albumPeak,
                    clipMode = config.clipMode,
                    maxPeakLevel = config.maxPeakLevel.toFloat()
                )

                Timber.i("Album gain (energy-average) album=$albumKey tracks=${trackGains.size} albumGain=$clampedAlbumGain albumLoudness=$albumLoudness")

                // Close any scanners that were created (mixed cached/fresh case)
                trackScanners.forEach { try { it.close() } catch (_: Exception) {} }

                for ((filePath, trackInfo) in trackGains) {
                    try {
                        val combinedInfo = ReplayGainInfo(
                            trackGain = trackInfo.trackGain,
                            trackPeak = trackInfo.trackPeak,
                            albumGain = clampedAlbumGain,
                            albumPeak = albumPeak,
                            truePeak = trackInfo.truePeak,
                            trackLoudness = trackInfo.trackLoudness,
                            albumLoudness = albumLoudness,
                            trackRange = trackInfo.trackRange,
                            albumRange = albumRange,
                            referenceLoudness = trackInfo.referenceLoudness
                        )
                        val saved = saveReplayGainToFile(filePath, combinedInfo)
                        if (!saved) {
                            Timber.w("Album gain save failed but analysis complete file=$filePath")
                        }
                        emit(
                            ScanProgress(
                                currentFile = processedFiles,
                                totalFiles = totalFiles,
                                percentage = processedFiles.toFloat() / totalFiles,
                                currentFilePath = filePath,
                                status = ScanStatus.TRACK_COMPLETED,
                                replayGainInfo = combinedInfo
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save album gain for file=$filePath reason=${e.message}")
                    }
                }

                emit(
                    ScanProgress(
                        currentFile = processedFiles,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = "",
                        status = ScanStatus.ALBUM_COMPLETED
                    )
                )

            } else {
                // No track succeeded for this album — still emit ALBUM_COMPLETED so the
                // UI's `isScanning` flag transitions out of "scanning" state.
                Timber.w("Album scan produced no successful tracks album=$albumKey")
                // Close any scanners that were created
                trackScanners.forEach { try { it.close() } catch (_: Exception) {} }
                emit(
                    ScanProgress(
                        currentFile = processedFiles,
                        totalFiles = totalFiles,
                        percentage = processedFiles.toFloat() / totalFiles,
                        currentFilePath = "",
                        status = ScanStatus.ALBUM_COMPLETED,
                        replayGainInfo = null
                    )
                )
            }

            processedAlbums++
        }

        emit(
            ScanProgress(
                currentFile = processedFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = ScanStatus.COMPLETED
            )
        )

        Timber.i(
            "ReplayGain album scan finished. albums=$totalAlbums files=$totalFiles elapsedMs=${SystemClock.elapsedRealtime() - scanStartedAt}"
        )
        } finally {
            codecPool.closeAll()
        }
    }

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
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        // Check cache first - cache hits don't need semaphore permit
        getCachedResult(filePath)?.let { cached ->
            Timber.v("ReplayGain cache hit: ${file.name}")
            return@withContext cached
        }

        // Check Room cache if skipExisting is enabled
        if (config.skipExisting) {
            val cachedEntity = cachedAudioFileDao.getAudioFileByPath(filePath)
            if (cachedEntity?.replayGainTrackGain != null) {
                val existing = ReplayGainInfo(
                    trackGain = cachedEntity.replayGainTrackGain,
                    trackPeak = cachedEntity.replayGainTrackPeak ?: 0f,
                    albumGain = cachedEntity.replayGainAlbumGain,
                    albumPeak = cachedEntity.replayGainAlbumPeak,
                    truePeak = cachedEntity.replayGainTruePeak,
                    trackLoudness = cachedEntity.replayGainTrackLoudness,
                    albumLoudness = cachedEntity.replayGainAlbumLoudness,
                    trackRange = cachedEntity.replayGainTrackRange,
                    albumRange = cachedEntity.replayGainAlbumRange,
                    referenceLoudness = cachedEntity.replayGainReferenceLoudness ?: -18f
                )
                cacheResult(filePath, existing)
                Timber.v("ReplayGain skipExisting hit: ${file.name}")
                return@withContext existing
            }
        }

        scanSemaphore.withPermit {
            try {
                if (!nativeScannerAvailable) {
                    return@withPermit null
                }

                // Lower thread priority for CPU-intensive audio processing to reduce overheating
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

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
                    return@withPermit null
                }

                extractor.selectTrack(audioTrackIndex)
                val trackFormat = extractor.getTrackFormat(audioTrackIndex)

                // Read sample rate and channel count from extractor format (before any override)
                val originalSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                // When the extractor reports audio/raw for a file whose extension
                // suggests a compressed format, the extractor may have pre-decoded
                // to PCM (emulator) or misreported the MIME (some real devices).
                // Strategy: try the extension-based decoder FIRST (correct for real
                // devices, uses hardware acceleration), then fall back to raw
                // (correct for emulator pre-decode). The FLAC decoder fails FAST
                // on raw PCM input (no sync code found), so the emulator overhead
                // is minimal.
                val isRawExtractor = trackFormat.getString(MediaFormat.KEY_MIME) == "audio/raw"
                val primaryFormat: MediaFormat
                val fallbackFormat: MediaFormat?
                if (isRawExtractor) {
                    val correctedMime = getMimeFromExtension(filePath)
                    if (correctedMime != null && correctedMime != "audio/raw") {
                        Timber.w("Extractor reported audio/raw for ${file.name} but extension suggests $correctedMime — trying $correctedMime decoder first, then raw on failure")
                        primaryFormat = MediaFormat().apply {
                            setString(MediaFormat.KEY_MIME, correctedMime)
                            setInteger(MediaFormat.KEY_SAMPLE_RATE, originalSampleRate)
                            setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        }
                        fallbackFormat = trackFormat
                    } else {
                        primaryFormat = trackFormat
                        fallbackFormat = null
                    }
                } else {
                    primaryFormat = trackFormat
                    fallbackFormat = null
                }

                // PCM-level decimation: skip samples in decoded output to reduce effective rate.
                // MediaCodec decoders ignore KEY_SAMPLE_RATE changes on the input format,
                // so we must downsample the PCM output ourselves.
                val decimationFactor = if (scanQuality != ScanQuality.ACCURATE &&
                    originalSampleRate > scanQuality.maxSampleRate
                ) {
                    val factor = originalSampleRate / scanQuality.maxSampleRate
                    Timber.d(
                        "PCM decimation for ${file.name}: factor=$factor " +
                            "(${originalSampleRate}Hz -> ${originalSampleRate / factor}Hz)"
                    )
                    factor.coerceAtLeast(1)
                } else {
                    1
                }

                // Scanner must be initialized with the effective sample rate of the data
                // it will actually receive (after decimation), not the original file rate.
                val effectiveSampleRate = originalSampleRate / decimationFactor

                val scanner = try {
                    EbuR128NativeScanner(
                        channels = channelCount,
                        sampleRate = effectiveSampleRate,
                        targetLoudness = targetLoudness.toDouble(),
                        truePeak = false,
                        dualMono = config.dualMono
                    )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    if (t is LinkageError || t.cause is LinkageError) {
                        nativeScannerAvailable = false
                        Timber.e(
                            t,
                            "Disabling native ReplayGain scanner due to JNI/linkage failure: ${t.message}"
                        )
                    } else {
                        Timber.e(t, "Failed to create native scanner for $filePath")
                    }
                    extractor.release()
                    return@withPermit null
                }

                scanner.use { nativeScanner ->
                    var decodeResult: ReplayGainInfo? = null
                    var primaryFailed = false

                    try {
                        decodeAndFeedScanner(extractor, primaryFormat, channelCount, decimationFactor, nativeScanner)
                        decodeResult = nativeScanner.getResult()
                        if (decodeResult == null || !decodeResult.trackGain.isFinite()) {
                            primaryFailed = true
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        primaryFailed = true
                    }

                    if (primaryFailed && fallbackFormat != null) {
                        val fallbackMime = fallbackFormat.getString(MediaFormat.KEY_MIME) ?: "unknown"
                        Timber.w("Primary decoder failed for ${file.name}, falling back to $fallbackMime")

                        // Try native decode via AMediaExtractor+AMediaCodec — single JNI
                        // call for the entire file, all processing in native code.
                        if (fallbackMime == "audio/raw") {
                            try {
                                val natResult = nativeAudioDecoder.decodeFileGain(
                                    filePath,
                                    targetLoudness.toDouble(),
                                    truePeak = false,
                                    dualMono = config.dualMono,
                                    maxSampleRate = if (scanQuality != ScanQuality.ACCURATE)
                                        scanQuality.maxSampleRate else 0
                                )
                                if (natResult != null) {
                                    primaryFailed = false
                                    decodeResult = ReplayGainInfo(
                                        trackGain = natResult[0].toFloat(),
                                        trackPeak = natResult[1].toFloat(),
                                        albumGain = null, albumPeak = null,
                                        truePeak = natResult[4].toFloat().takeIf { it > 0f },
                                        trackLoudness = natResult[2].toFloat(),
                                        albumLoudness = null,
                                        trackRange = natResult[3].toFloat(),
                                        albumRange = null,
                                        referenceLoudness = natResult[5].toFloat()
                                    )
                                    Timber.i("Native decode succeeded for ${file.name}")
                                } else {
                                    Timber.w("Native decode returned null for ${file.name}")
                                }
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                Timber.w("Native decode failed for ${file.name}: ${t.message}")
                            }
                        }

                        if (primaryFailed) {
                            try { extractor.release() } catch (_: Exception) {}
                            nativeScanner.close()
                            EbuR128NativeScanner(
                                channels = channelCount,
                                sampleRate = effectiveSampleRate,
                                targetLoudness = targetLoudness.toDouble(),
                                truePeak = false,
                                dualMono = config.dualMono
                            ).use { fallbackScanner ->
                                val fallbackExtractor = MediaExtractor()
                                fallbackExtractor.setDataSource(filePath)
                                fallbackExtractor.selectTrack(audioTrackIndex)
                                try {
                                    if (fallbackMime == "audio/raw") {
                                        feedRawPcmFromExtractor(fallbackExtractor, channelCount, decimationFactor, fallbackScanner)
                                    } else {
                                        decodeAndFeedScanner(fallbackExtractor, fallbackFormat, channelCount, decimationFactor, fallbackScanner)
                                    }
                                    decodeResult = fallbackScanner.getResult()
                                } finally {
                                    fallbackExtractor.release()
                                }
                            }
                        }
                    } else if (primaryFailed) {
                        Timber.w("Decoder failed for ${file.name} (no fallback available)")
                        return@withPermit null
                    }

                    val replayGainInfo = decodeResult
                        ?: run {
                            Timber.w("Native scanner returned null for $filePath")
                            return@withPermit null
                        }

                    Timber.v(
                        "Native ReplayGain result: file=${file.name} loudness=${replayGainInfo.trackLoudness} LUFS gainDb=${replayGainInfo.trackGain} peak=${replayGainInfo.trackPeak}"
                    )

                    val clampedTrackGain = applyClipProtection(
                        gain = replayGainInfo.trackGain,
                        peak = replayGainInfo.trackPeak,
                        clipMode = config.clipMode,
                        maxPeakLevel = config.maxPeakLevel.toFloat()
                    )

                    if (!replayGainInfo.trackGain.isFinite()) {
                        Timber.w("Invalid gain ${replayGainInfo.trackGain} for ${file.name} — likely decoder failure, not caching")
                        return@withPermit null
                    }

                    val result = replayGainInfo.copy(trackGain = clampedTrackGain)

                    cacheResult(filePath, result)

                    result
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Timber.e(t, "analyzeAudioFile exception: ${t.message}")
                null
            }
        }
    }

    /**
     * Analyzes a single file and returns both the track gain info AND the native scanner
     * (state preserved for album-level ebur128_loudness_global_multiple calculation).
     * Caller MUST close the returned scanner after album gain calculation.
     */
    private suspend fun analyzeAudioFileKeepScanner(
        filePath: String,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: ReplayGainConfig = ReplayGainConfig.DEFAULT
    ): Pair<EbuR128NativeScanner?, ReplayGainInfo?> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null to null

        // Check cache first - cache hits don't need semaphore permit
        getCachedResult(filePath)?.let { cached ->
            Timber.v("ReplayGain cache hit: ${file.name}")
            return@withContext null to cached
        }

        // Check Room cache if skipExisting is enabled
        if (config.skipExisting) {
            val cachedEntity = cachedAudioFileDao.getAudioFileByPath(filePath)
            if (cachedEntity?.replayGainTrackGain != null) {
                val existing = ReplayGainInfo(
                    trackGain = cachedEntity.replayGainTrackGain,
                    trackPeak = cachedEntity.replayGainTrackPeak ?: 0f,
                    albumGain = cachedEntity.replayGainAlbumGain,
                    albumPeak = cachedEntity.replayGainAlbumPeak,
                    truePeak = cachedEntity.replayGainTruePeak,
                    trackLoudness = cachedEntity.replayGainTrackLoudness,
                    albumLoudness = cachedEntity.replayGainAlbumLoudness,
                    trackRange = cachedEntity.replayGainTrackRange,
                    albumRange = cachedEntity.replayGainAlbumRange,
                    referenceLoudness = cachedEntity.replayGainReferenceLoudness ?: -18f
                )
                cacheResult(filePath, existing)
                Timber.v("ReplayGain skipExisting hit: ${file.name}")
                return@withContext null to existing
            }
        }

        scanSemaphore.withPermit {
            var scanner: EbuR128NativeScanner? = null
            try {
                if (!nativeScannerAvailable) {
                    return@withPermit null to null
                }

                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

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
                    return@withPermit null to null
                }

                extractor.selectTrack(audioTrackIndex)
                val trackFormat = extractor.getTrackFormat(audioTrackIndex)

                val originalSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                // When the extractor reports audio/raw for a compressed extension,
                // try the extension-based decoder first, then fall back to raw.
                // See analyzeAudioFile for full rationale.
                val isRawExtractor = trackFormat.getString(MediaFormat.KEY_MIME) == "audio/raw"
                val primaryFormat: MediaFormat
                val fallbackFormat: MediaFormat?
                if (isRawExtractor) {
                    val correctedMime = getMimeFromExtension(filePath)
                    if (correctedMime != null && correctedMime != "audio/raw") {
                        Timber.w("Extractor reported audio/raw for ${file.name} but extension suggests $correctedMime — trying $correctedMime decoder first, then raw on failure")
                        primaryFormat = MediaFormat().apply {
                            setString(MediaFormat.KEY_MIME, correctedMime)
                            setInteger(MediaFormat.KEY_SAMPLE_RATE, originalSampleRate)
                            setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        }
                        fallbackFormat = trackFormat
                    } else {
                        primaryFormat = trackFormat
                        fallbackFormat = null
                    }
                } else {
                    primaryFormat = trackFormat
                    fallbackFormat = null
                }

                val decimationFactor = if (scanQuality != ScanQuality.ACCURATE &&
                    originalSampleRate > scanQuality.maxSampleRate) {
                    (originalSampleRate / scanQuality.maxSampleRate).coerceAtLeast(1)
                } else {
                    1
                }
                val effectiveSampleRate = originalSampleRate / decimationFactor

                scanner = try {
                    EbuR128NativeScanner(
                        channels = channelCount,
                        sampleRate = effectiveSampleRate,
                        targetLoudness = targetLoudness.toDouble(),
                        truePeak = false,
                        dualMono = config.dualMono
                    )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    if (t is LinkageError || t.cause is LinkageError) {
                        nativeScannerAvailable = false
                        Timber.e(t, "Disabling native ReplayGain scanner: ${t.message}")
                    } else {
                        Timber.e(t, "Failed to create native scanner for $filePath")
                    }
                    extractor.release()
                    return@withPermit null to null
                }

                var decodeResult: ReplayGainInfo? = null
                var primaryFailed = false

                try {
                    decodeAndFeedScanner(extractor, primaryFormat, channelCount, decimationFactor, scanner)
                    decodeResult = scanner.getResult()
                    if (decodeResult == null || !decodeResult.trackGain.isFinite()) {
                        primaryFailed = true
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    primaryFailed = true
                }

                if (primaryFailed && fallbackFormat != null) {
                    val fallbackMime = fallbackFormat.getString(MediaFormat.KEY_MIME) ?: "unknown"
                    Timber.w("Primary decoder failed for ${file.name}, falling back to $fallbackMime")

                    // Try native decode via AMediaExtractor+AMediaCodec — single JNI
                    // call for the entire file, all processing in native code.
                    // Returns null scanner since album gain falls back to energy-average.
                    var nativeDecodeResult: ReplayGainInfo? = null
                    if (fallbackMime == "audio/raw") {
                        try {
                            val natResult = nativeAudioDecoder.decodeFileGain(
                                filePath,
                                targetLoudness.toDouble(),
                                truePeak = false,
                                dualMono = config.dualMono,
                                maxSampleRate = if (scanQuality != ScanQuality.ACCURATE)
                                    scanQuality.maxSampleRate else 0
                            )
                            if (natResult != null) {
                                nativeDecodeResult = ReplayGainInfo(
                                    trackGain = natResult[0].toFloat(),
                                    trackPeak = natResult[1].toFloat(),
                                    albumGain = null, albumPeak = null,
                                    truePeak = natResult[4].toFloat().takeIf { it > 0f },
                                    trackLoudness = natResult[2].toFloat(),
                                    albumLoudness = null,
                                    trackRange = natResult[3].toFloat(),
                                    albumRange = null,
                                    referenceLoudness = natResult[5].toFloat()
                                )
                                Timber.i("Native decode succeeded for ${file.name}")
                            }
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            Timber.w("Native decode failed for ${file.name}: ${t.message}")
                        }
                    }

                    if (nativeDecodeResult != null) {
                        try { scanner.close() } catch (_: Exception) {}
                        try { extractor.release() } catch (_: Exception) {}
                        val result = nativeDecodeResult.copy(trackGain = applyClipProtection(
                            gain = nativeDecodeResult.trackGain,
                            peak = nativeDecodeResult.trackPeak,
                            clipMode = config.clipMode,
                            maxPeakLevel = config.maxPeakLevel.toFloat()
                        ))
                        cacheResult(filePath, result)
                        return@withPermit null to result
                    }

                    try { extractor.release() } catch (_: Exception) {}
                    scanner.close()
                    scanner = EbuR128NativeScanner(
                        channels = channelCount,
                        sampleRate = effectiveSampleRate,
                        targetLoudness = targetLoudness.toDouble(),
                        truePeak = false,
                        dualMono = config.dualMono
                    )
                    val fallbackExtractor = MediaExtractor()
                    fallbackExtractor.setDataSource(filePath)
                    fallbackExtractor.selectTrack(audioTrackIndex)
                    try {
                        if (fallbackMime == "audio/raw") {
                            feedRawPcmFromExtractor(fallbackExtractor, channelCount, decimationFactor, scanner)
                        } else {
                            decodeAndFeedScanner(fallbackExtractor, fallbackFormat, channelCount, decimationFactor, scanner)
                        }
                        decodeResult = scanner.getResult()
                    } finally {
                        fallbackExtractor.release()
                    }
                } else if (primaryFailed) {
                    Timber.w("Decoder failed for ${file.name} (no fallback available)")
                    scanner.close()
                    return@withPermit null to null
                }

                val replayGainInfo = decodeResult ?: run {
                    scanner.close()
                    return@withPermit null to null
                }

                val clampedTrackGain = applyClipProtection(
                    gain = replayGainInfo.trackGain,
                    peak = replayGainInfo.trackPeak,
                    clipMode = config.clipMode,
                    maxPeakLevel = config.maxPeakLevel.toFloat()
                )

                if (!replayGainInfo.trackGain.isFinite()) {
                    Timber.w("Invalid gain ${replayGainInfo.trackGain} for ${file.name} — likely decoder failure, not caching")
                    scanner.close()
                    return@withPermit null to null
                }

                val result = replayGainInfo.copy(trackGain = clampedTrackGain)
                cacheResult(filePath, result)

                // Don't close scanner - return it for album gain calculation
                scanner to result
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Timber.e(t, "analyzeAudioFileKeepScanner exception: ${t.message}")
                try { scanner?.close() } catch (_: Exception) {}
                null to null
            }
        }
    }

    /**
     * Reads raw PCM data directly from MediaExtractor, bypassing MediaCodec entirely.
     *
     * When the extractor reports audio/raw, it has already decoded the audio to PCM.
     * Uses readSampleData(byteBuf, offset) to write directly into the batch buffer,
     * eliminating the intermediate readBuffer copy (API 21+ updates position/limit
     * to the data just read, but we use absolute getShort/putShort for decimation).
     */
    private suspend fun feedRawPcmFromExtractor(
        extractor: MediaExtractor,
        channelCount: Int,
        decimationFactor: Int,
        nativeScanner: EbuR128NativeScanner
    ) {
        val batchBuffer = ByteBuffer.allocateDirect(BATCH_BUFFER_SIZE)
        batchBuffer.order(ByteOrder.LITTLE_ENDIAN)
        var batchPos = 0

        fun flushBatch() {
            if (batchPos > 0) {
                batchBuffer.flip()
                nativeScanner.processBuffer(batchBuffer, batchPos)
                batchBuffer.clear()
                batchBuffer.order(ByteOrder.LITTLE_ENDIAN)
                batchPos = 0
            }
        }

        val bytesPerFrame = channelCount * 2

        while (true) {
            kotlin.coroutines.coroutineContext.ensureActive()

            // Ensure enough room — flush if <4096 frames remaining
            if (batchPos + bytesPerFrame * 4096 > batchBuffer.capacity()) flushBatch()

            // Read raw PCM directly into batchBuffer at current position
            val sampleSize = extractor.readSampleData(batchBuffer, batchPos)
            if (sampleSize < 0) break

            if (decimationFactor > 1) {
                val totalFrames = sampleSize / bytesPerFrame
                var writePos = batchPos
                for (frameIndex in 0 until totalFrames step decimationFactor) {
                    for (ch in 0 until channelCount) {
                        val srcOffset = batchPos + (frameIndex * channelCount + ch) * 2
                        batchBuffer.putShort(writePos, batchBuffer.getShort(srcOffset))
                        writePos += 2
                    }
                }
                batchPos = writePos
            } else {
                batchPos += sampleSize
            }

            extractor.advance()
        }

        flushBatch()
    }

    /**
     * Decodes audio and feeds PCM samples to native EBU R128 scanner.
     * Uses synchronous mode with getInputBuffer/getOutputBuffer (modern API 21+ pattern).
     * When the decoder can't handle the format (e.g. FLAC decoder receiving raw PCM),
     * dequeueOutputBuffer(timeout) reliably signals failure via exception or timeout,
     * unlike async callback mode where the decoder may silently stall without triggering
     * onInputBufferAvailable or onError.
     *
     * Optimized with pooled 2MB batch buffer to reduce JNI call frequency.
     */
    private suspend fun decodeAndFeedScanner(
        extractor: MediaExtractor,
        format: MediaFormat,
        channelCount: Int,
        decimationFactor: Int,
        nativeScanner: EbuR128NativeScanner
    ) {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val codec = codecPool.acquire(mime, format)
        if (codec == null) {
            Timber.w("No decoder found for $mime")
            return
        }
        Timber.i("Using decoder: ${codec.name} (hw=${isHardwareAccelerated(codec.name)})")

        try {
            var inputDone = false
            var outputDone = false
            val bufferInfo = MediaCodec.BufferInfo()
            val batchBuffer = ByteBuffer.allocateDirect(BATCH_BUFFER_SIZE)
            batchBuffer.order(ByteOrder.LITTLE_ENDIAN)
            var batchPos = 0

            fun flushBatch() {
                if (batchPos > 0) {
                    batchBuffer.flip()
                    nativeScanner.processBuffer(batchBuffer, batchPos)
                    batchBuffer.clear()
                    batchBuffer.order(ByteOrder.LITTLE_ENDIAN)
                    batchPos = 0
                }
            }

            while (!outputDone) {
                kotlin.coroutines.coroutineContext.ensureActive()

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

                val outputIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    if (inputDone) 5_000L else DECODE_TIMEOUT_US
                )
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val safeOffset = bufferInfo.offset.coerceIn(0, outputBuffer.capacity() - 1)
                            val safeLimit = minOf(safeOffset + bufferInfo.size, outputBuffer.capacity())

                            if (safeLimit > safeOffset) {
                                if (decimationFactor > 1) {
                                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                                    val bytesPerSample = 2
                                    val bytesPerFrame = channelCount * bytesPerSample
                                    val totalFrames = bufferInfo.size / bytesPerFrame
                                    val decimatedBytes = ((totalFrames + decimationFactor - 1) / decimationFactor) * bytesPerFrame
                                    val spaceInBatch = batchBuffer.capacity() - batchPos

                                    if (decimatedBytes <= spaceInBatch) {
                                        var keptFrames = 0
                                        for (frameIndex in 0 until totalFrames step decimationFactor) {
                                            for (ch in 0 until channelCount) {
                                                val srcByteOffset = safeOffset + (frameIndex * channelCount + ch) * bytesPerSample
                                                batchBuffer.putShort(outputBuffer.getShort(srcByteOffset))
                                            }
                                            keptFrames++
                                        }
                                        batchPos += keptFrames * bytesPerFrame
                                    } else {
                                        flushBatch()
                                        for (frameIndex in 0 until totalFrames step decimationFactor) {
                                            if (batchPos + bytesPerFrame > batchBuffer.capacity()) {
                                                flushBatch()
                                            }
                                            for (ch in 0 until channelCount) {
                                                val srcByteOffset = safeOffset + (frameIndex * channelCount + ch) * bytesPerSample
                                                batchBuffer.putShort(outputBuffer.getShort(srcByteOffset))
                                            }
                                            batchPos += bytesPerFrame
                                        }
                                    }
                                } else {
                                    outputBuffer.position(safeOffset)
                                    outputBuffer.limit(safeLimit)
                                    val bytesToRead = outputBuffer.remaining()
                                    if (batchPos + bytesToRead <= batchBuffer.capacity()) {
                                        batchBuffer.put(outputBuffer)
                                        batchPos += bytesToRead
                                    } else {
                                        flushBatch()
                                        val canFit = minOf(bytesToRead, batchBuffer.capacity())
                                        val oldLimit = outputBuffer.limit()
                                        outputBuffer.limit(outputBuffer.position() + canFit)
                                        batchBuffer.put(outputBuffer)
                                        batchPos = canFit
                                        outputBuffer.limit(oldLimit)
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
                        val newChannels = codec.outputFormat
                            .getInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        if (newChannels != channelCount) {
                            Timber.w("ReplayGain channelCount changed $channelCount -> $newChannels")
                        }
                    }
                }
            }

            flushBatch()

            Timber.v("Decode complete: $mime ${codec.name}")
        } finally {
            codecPool.release(codec, mime)
        }
    }

    /**
     * Maps file extension to MIME type for audio formats.
     */
    private fun getMimeFromExtension(filePath: String): String? {
        return when (filePath.substringAfterLast('.', "").lowercase()) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/vorbis"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            else -> null
        }
    }

    /**
     * Checks if a codec is hardware accelerated (used for logging only).
     */
    private fun isHardwareAccelerated(codecName: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.find { it.name == codecName }?.isHardwareAccelerated ?: false
    }

    /**
     * Calculates album gain using energy-average method.
     * Used as fallback when some tracks are cached (no native scanner available).
     * Converts track loudness values to linear energy, averages them, then converts back.
     *
     * @return Triple of (albumLoudness, albumRange, albumPeak)
     */
    private fun calculateEnergyAverageAlbumGain(
        trackGains: List<ReplayGainInfo>,
        targetLoudness: Float
    ): Triple<Float, Float, Float> {
        if (trackGains.isEmpty()) return Triple(targetLoudness, 0f, 0f)

        val validGains = trackGains.filter {
            it.trackGain.isFinite() && it.trackGain > -100f && it.trackGain < 100f
        }
        if (validGains.isEmpty()) return Triple(targetLoudness, 0f, 0f)

        // Energy average of loudness: convert LUFS to linear, average, convert back
        val albumLoudness = if (validGains.all { it.trackLoudness != null }) {
            val linearLoudness = validGains.mapNotNull { it.trackLoudness }
                .map { 10.0.pow(it / 10.0) }
            10.0 * log10(linearLoudness.average())
        } else {
            targetLoudness.toDouble()
        }

        val albumRange = validGains.mapNotNull { it.trackRange }.maxOrNull() ?: 0f
        val albumPeak = validGains.maxOf { it.trackPeak }

        return Triple(albumLoudness.toFloat(), albumRange, albumPeak)
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
            Timber.w(
                "Clipping protection applied: gain=$gain -> $protectedGain (peak=$newPeak > $maxPeakLinear)"
            )
            return protectedGain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        return gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
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
            Timber.e(e, "saveReplayGainToFile exception: ${e.message}")
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
