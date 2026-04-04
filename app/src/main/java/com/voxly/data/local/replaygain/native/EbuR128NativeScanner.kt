package com.voxly.data.local.replaygain.native

import com.voxly.core.util.Logger
import com.voxly.domain.model.ReplayGainInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Native EBU R128 loudness scanner using libebur128.
 *
 * This is a pure computation engine - audio decoding is handled by
 * Android MediaCodec on the Kotlin side. PCM data is fed to this
 * scanner via JNI for EBU R128 analysis.
 *
 * Data flow: MediaCodec -> PCM short[] -> nativeProcessFrames -> nativeGetResult
 *
 * Matches rsgain's behavior exactly since it uses the same libebur128 library.
 */
class EbuR128NativeScanner(
    private val channels: Int,
    private val sampleRate: Int,
    private val targetLoudness: Double = -18.0,
    private val truePeak: Boolean = false,
    private val dualMono: Boolean = false
) : AutoCloseable {

    companion object {
        private const val TAG = "EbuR128NativeScanner"

        init {
            System.loadLibrary("ebur128-scanner")
        }
    }

    private var nativePtr: Long = 0

    init {
        nativePtr = nativeCreate(channels, sampleRate, truePeak, dualMono, targetLoudness)
        if (nativePtr == 0L) {
            throw IllegalStateException("Failed to create native ebur128 scanner")
        }
    }

    /**
     * Process a block of PCM samples (16-bit signed, interleaved).
     *
     * @param samples PCM samples as ShortArray
     * @param frameCount Number of audio frames (for stereo: samples.size / 2)
     */
    fun processFrames(samples: ShortArray, frameCount: Int) {
        if (nativePtr == 0L) return
        nativeProcessFrames(nativePtr, samples, frameCount)
    }

    /**
     * Process a block of PCM samples from a Direct ByteBuffer.
     * This is the most efficient method for large data transfers.
     *
     * @param buffer Direct ByteBuffer containing S16 PCM data
     * @param size Buffer size in bytes
     * @return Number of frames processed
     */
    fun processBuffer(buffer: ByteBuffer, size: Int): Int {
        if (nativePtr == 0L) return 0
        return nativeProcessBuffer(nativePtr, buffer, size)
    }

    /**
     * Get scan results.
     *
     * @return ReplayGainInfo or null if insufficient data
     */
    fun getResult(): ReplayGainInfo? {
        if (nativePtr == 0L) return null

        val result = nativeGetResult(nativePtr) ?: return null

        return ReplayGainInfo(
            trackGain = result[0].toFloat(),
            trackPeak = result[1].toFloat(),
            albumGain = null,
            albumPeak = null,
            truePeak = result[4].toFloat().takeIf { it > 0f },
            trackLoudness = result[2].toFloat(),
            albumLoudness = null,
            trackRange = result[3].toFloat(),
            albumRange = null,
            referenceLoudness = result[5].toFloat()
        )
    }

    /**
     * Get library version string.
     */
    fun getVersion(): String = nativeGetVersion()

    override fun close() {
        if (nativePtr != 0L) {
            nativeDestroy(nativePtr)
            nativePtr = 0
        }
    }

    // Native methods
    private external fun nativeCreate(
        channels: Int,
        sampleRate: Int,
        truePeak: Boolean,
        dualMono: Boolean,
        targetLoudness: Double
    ): Long

    private external fun nativeProcessFrames(
        scannerPtr: Long,
        samples: ShortArray,
        frameCount: Int
    )

    private external fun nativeProcessBuffer(
        scannerPtr: Long,
        buffer: ByteBuffer,
        size: Int
    ): Int

    private external fun nativeGetResult(scannerPtr: Long): DoubleArray?

    private external fun nativeDestroy(scannerPtr: Long)

    private external fun nativeGetVersion(): String
}
