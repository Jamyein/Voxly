package com.voxly.data.local.replaygain.native

import dalvik.annotation.optimization.FastNative

/**
 * Native FLAC decoder using dr_flac for ReplayGain analysis.
 *
 * Decodes FLAC files natively and feeds PCM directly to libebur128,
 * bypassing Java MediaExtractor/MediaCodec. This avoids JNI batch-call
 * overhead and MediaExtractor pre-decode copy, significantly reducing
 * scan time for FLAC files on devices where the extractor reports
 * audio/raw (pre-decoded PCM).
 */
class NativeFlacDecoder {

    companion object {
        init {
            System.loadLibrary("ebur128-scanner")
        }
    }

    @FastNative
    external fun decodeFlacGain(
        filePath: String,
        targetLoudness: Double,
        truePeak: Boolean,
        dualMono: Boolean,
        maxSampleRate: Int
    ): DoubleArray?
}
