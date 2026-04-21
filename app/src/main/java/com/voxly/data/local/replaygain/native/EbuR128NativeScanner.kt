package com.voxly.data.local.replaygain.native

import dalvik.annotation.optimization.CriticalNative
import dalvik.annotation.optimization.FastNative
import timber.log.Timber
import com.voxly.domain.model.ReplayGainInfo
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
 * Performance optimizations:
 * - RegisterNatives explicit registration (JNI_OnLoad)
 * - @FastNative for faster JNI transition
 * - Direct ByteBuffer for zero-copy large data transfer
 * - Pre-allocated result array to avoid heap allocation per call
 * - GetShortArrayRegion (1 JNI call vs 2 with Get/Release)
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

    private val resultArray = DoubleArray(6)

    init {
        nativePtr = nativeCreate(channels, sampleRate, truePeak, dualMono, targetLoudness)
        if (nativePtr == 0L) {
            throw IllegalStateException("Failed to create native ebur128 scanner")
        }
    }

    fun processFrames(samples: ShortArray, frameCount: Int) {
        if (nativePtr == 0L) return
        nativeProcessFrames(nativePtr, samples, frameCount)
    }

    fun processBuffer(buffer: ByteBuffer, size: Int): Int {
        if (nativePtr == 0L) return 0
        return nativeProcessBuffer(nativePtr, buffer, size)
    }

    fun getResult(): ReplayGainInfo? {
        if (nativePtr == 0L) return null

        if (!nativeGetResult(nativePtr, resultArray)) return null

        return ReplayGainInfo(
            trackGain = resultArray[0].toFloat(),
            trackPeak = resultArray[1].toFloat(),
            albumGain = null,
            albumPeak = null,
            truePeak = resultArray[4].toFloat().takeIf { it > 0f },
            trackLoudness = resultArray[2].toFloat(),
            albumLoudness = null,
            trackRange = resultArray[3].toFloat(),
            albumRange = null,
            referenceLoudness = resultArray[5].toFloat()
        )
    }

    fun getVersion(): String = nativeGetVersion()

    override fun close() {
        if (nativePtr != 0L) {
            nativeDestroy(nativePtr)
            nativePtr = 0
        }
    }

    @FastNative
    private external fun nativeCreate(
        channels: Int,
        sampleRate: Int,
        truePeak: Boolean,
        dualMono: Boolean,
        targetLoudness: Double
    ): Long

    @CriticalNative
    private external fun nativeProcessFrames(
        scannerPtr: Long,
        samples: ShortArray,
        frameCount: Int
    )

    @CriticalNative
    private external fun nativeProcessBuffer(
        scannerPtr: Long,
        buffer: ByteBuffer,
        size: Int
    ): Int

    @FastNative
    private external fun nativeGetResult(scannerPtr: Long, result: DoubleArray): Boolean

    @FastNative
    private external fun nativeDestroy(scannerPtr: Long)

    @FastNative
    private external fun nativeGetVersion(): String
}
