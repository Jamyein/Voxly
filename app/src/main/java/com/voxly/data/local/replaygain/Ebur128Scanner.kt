package com.voxly.data.local.replaygain

/**
 * EBU R128 loudness scanner JNI wrapper.
 * 
 * This class provides a Kotlin interface to the libebur128 library,
 * implementing the EBU R128 standard for loudness measurement.
 * 
 * Target loudness: -18 LUFS (foobar2000 modern default)
 * Supports True Peak measurement.
 */
class Ebur128Scanner(
    channels: Int,
    sampleRate: Int,
    mode: Int = MODE_I
) : AutoCloseable {
    private var nativeHandle: Long = 0
    private var isClosed: Boolean = false
    
    companion object {
        // Mode flags - can be combined with bitwise OR
        const val MODE_M = 1              // Momentary loudness (400ms)
        const val MODE_S = 3              // Short-term loudness (3s) + MODE_M
        const val MODE_I = 7              // Integrated loudness + MODE_M
        const val MODE_LRA = 11           // Loudness Range + MODE_S
        const val MODE_SAMPLE_PEAK = 17   // Sample peak + MODE_M
        const val MODE_TRUE_PEAK = 55     // True peak + MODE_M + MODE_SAMPLE_PEAK
        const val MODE_HISTOGRAM = 64     // Use histogram algorithm
        
        // Standard target loudness levels
        const val LUFS_EBU_R128 = -23.0f   // Standard EBU R128
        const val LUFS_FOOBAR2000_MODERN = -18.0f  // foobar2000 1.1.6+ default
        const val LUFS_REPLAYGAIN_CLASSIC = -14.0f // Classic ReplayGain
        
        init {
            System.loadLibrary("ebur128-jni")
        }
        
        /**
         * Get the version of the underlying libebur128 library.
         */
        @JvmStatic
        external fun getVersion(): String
        
        /**
         * Calculate global loudness across multiple scanners (for album gain).
         * Uses ebur128_loudness_global_multiple internally.
         * 
         * @param handles Array of native handles from Ebur128Scanner instances
         * @return Combined loudness in LUFS
         */
        @JvmStatic
        external fun getLoudnessGlobalMultiple(handles: LongArray): Double
    }
    
    init {
        require(channels > 0) { "Channels must be positive" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        
        nativeHandle = nativeInit(channels, sampleRate, mode)
        if (nativeHandle == 0L) {
            throw RuntimeException("Failed to initialize EBU R128 scanner")
        }
    }
    
    /**
     * Get the native handle for this scanner instance.
     * Used for album scanning with multiple scanners.
     */
    fun getHandle(): Long = nativeHandle
    
    /**
     * Add float audio frames for processing.
     * 
     * @param samples Interleaved float audio samples (-1.0 to 1.0)
     * @param frames Number of frames (not samples). For stereo, samples.size / 2
     * @return Error code (0 = success)
     */
    fun addFrames(samples: FloatArray, frames: Int): Int {
        check(!isClosed) { "Scanner has been closed" }
        return nativeAddFramesFloat(nativeHandle, samples, frames)
    }
    
    /**
     * Add short audio frames for processing.
     * 
     * @param samples Interleaved short audio samples
     * @param frames Number of frames (not samples)
     * @return Error code (0 = success)
     */
    fun addFrames(samples: ShortArray, frames: Int): Int {
        check(!isClosed) { "Scanner has been closed" }
        return nativeAddFramesShort(nativeHandle, samples, frames)
    }
    
    /**
     * Get the global integrated loudness.
     * 
     * @return Loudness in LUFS, or -Infinity if not enough data
     */
    fun getLoudnessGlobal(): Double {
        check(!isClosed) { "Scanner has been closed" }
        return nativeGetLoudnessGlobal(nativeHandle)
    }
    
    /**
     * Get the maximum sample peak across all channels.
     * 
     * @return Peak level (1.0 = 0 dBFS)
     */
    fun getSamplePeak(): Double {
        check(!isClosed) { "Scanner has been closed" }
        return nativeGetSamplePeak(nativeHandle)
    }
    
    /**
     * Get the maximum true peak across all channels.
     * True peak uses oversampling to detect inter-sample peaks.
     * 
     * @return True peak level (1.0 = 0 dBTP)
     */
    fun getTruePeak(): Double {
        check(!isClosed) { "Scanner has been closed" }
        return nativeGetTruePeak(nativeHandle)
    }
    
    /**
     * Calculate the gain adjustment needed to reach target loudness.
     * 
     * @param targetLoudness Target loudness in LUFS (default: -18 LUFS for foobar2000 modern)
     * @return Gain adjustment in dB (positive = increase volume, negative = decrease)
     */
    fun calculateGainAdjustment(targetLoudness: Float = LUFS_FOOBAR2000_MODERN): Float {
        val currentLoudness = getLoudnessGlobal()
        return if (currentLoudness.isFinite()) {
            targetLoudness - currentLoudness.toFloat()
        } else {
            0f  // Return 0 if loudness measurement failed
        }
    }
    
    /**
     * Close the scanner and release native resources.
     */
    override fun close() {
        if (!isClosed) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
            isClosed = true
        }
    }
    
    protected fun finalize() {
        close()
    }
    
    // Native methods
    private external fun nativeInit(channels: Int, sampleRate: Int, mode: Int): Long
    private external fun nativeAddFramesFloat(handle: Long, samples: FloatArray, frames: Int): Int
    private external fun nativeAddFramesShort(handle: Long, samples: ShortArray, frames: Int): Int
    private external fun nativeGetLoudnessGlobal(handle: Long): Double
    private external fun nativeGetSamplePeak(handle: Long): Double
    private external fun nativeGetTruePeak(handle: Long): Double
    private external fun nativeDestroy(handle: Long)
}
