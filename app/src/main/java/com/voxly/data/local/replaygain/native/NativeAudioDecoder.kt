package com.voxly.data.local.replaygain.native

import dalvik.annotation.optimization.FastNative

/**
 * Unified native audio decoder + ReplayGain scanner.
 *
 * Uses NDK AMediaExtractor + AMediaCodec to decode audio natively,
 * feeding PCM directly to libebur128 — single JNI call for the entire file.
 * Covers both raw PCM (audio/raw) and compressed formats (FLAC, MP3, etc.).
 */
class NativeAudioDecoder {

    companion object {
        init {
            System.loadLibrary("ebur128-scanner")
        }
    }

    @FastNative
    external fun decodeFileGain(
        filePath: String,
        targetLoudness: Double,
        truePeak: Boolean,
        dualMono: Boolean,
        maxSampleRate: Int
    ): DoubleArray?
}
