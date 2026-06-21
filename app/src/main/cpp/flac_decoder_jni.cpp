/*
 * flac_decoder_jni.cpp: Native FLAC decoder using dr_flac.
 *
 * Decodes FLAC files natively, bypassing Java MediaExtractor/MediaCodec.
 * Feeds decoded PCM directly to libebur128 for ReplayGain analysis.
 *
 * Advantages over Java-side decode + JNI feed:
 * - No JNI batch calls per chunk (single JNI call for the whole file)
 * - No MediaExtractor pre-decode overhead
 * - No intermediate buffer copies
 */

#include <jni.h>
#include <string>
#include <cmath>
#include <android/log.h>

#define DR_FLAC_IMPLEMENTATION
#include "external/dr_flac/dr_flac.h"

extern "C" {
#include <ebur128.h>
}

#define LOG_TAG "FlacDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_voxly_data_local_replaygain_native_NativeFlacDecoder_decodeFlacGain(
    JNIEnv* env, jobject thiz,
    jstring filePath,
    jdouble targetLoudness,
    jboolean truePeak,
    jboolean dualMono,
    jint maxSampleRate
) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    unsigned int channels;
    unsigned int sampleRate;
    drflac_uint64 totalPCMFrames;
    drflac_int32* samples = drflac_open_file_and_read_pcm_frames_s32(
        path, &channels, &sampleRate, &totalPCMFrames, nullptr);

    if (!samples) {
        LOGE("Failed to open/decode FLAC: %s", path);
        env->ReleaseStringUTFChars(filePath, path);
        return nullptr;
    }

    env->ReleaseStringUTFChars(filePath, path);

    int decimationFactor = 1;
    if (maxSampleRate > 0 && (int)sampleRate > maxSampleRate) {
        decimationFactor = (int)sampleRate / maxSampleRate;
        sampleRate = sampleRate / decimationFactor;
    }

    int mode = EBUR128_MODE_I | EBUR128_MODE_SAMPLE_PEAK | EBUR128_MODE_HISTOGRAM;
    if (truePeak) mode |= EBUR128_MODE_TRUE_PEAK;

    int eburChannels = (channels == 1 && dualMono) ? 2 : channels;
    ebur128_state* ebur = ebur128_init(eburChannels, sampleRate, mode);
    if (!ebur) {
        LOGE("Failed to initialize ebur128 state");
        drflac_free(samples, nullptr);
        return nullptr;
    }

    if (channels == 1 && dualMono) {
        ebur128_set_channel(ebur, 0, EBUR128_DUAL_MONO);
        ebur128_set_channel(ebur, 1, EBUR128_DUAL_MONO);
    }

    LOGD("Decoding: ch=%u rate=%u frames=%llu decimation=%d",
         channels, sampleRate, (unsigned long long)totalPCMFrames, decimationFactor);

    // dr_flac reads s32 frames interleaved. Convert to short as we feed ebur128.
    const int BATCH = 8192;
    auto* pcm16 = new drflac_int16[BATCH * eburChannels];

    drflac_uint64 frameIndex = 0;
    while (frameIndex < totalPCMFrames) {
        drflac_uint64 remaining = totalPCMFrames - frameIndex;
        drflac_uint64 batchFrames = (remaining < BATCH) ? remaining : BATCH;

        // Convert s32 to s16
        for (drflac_uint64 f = 0; f < batchFrames; f++) {
            for (unsigned int ch = 0; ch < channels; ch++) {
                drflac_int32 s32 = samples[(frameIndex + f) * channels + ch];
                pcm16[f * eburChannels + ch] = (drflac_int16)(s32 >> 16);
            }
        }
        // Fill extra channel for dual-mono
        if (channels == 1 && dualMono) {
            for (drflac_uint64 f = 0; f < batchFrames; f++) {
                pcm16[f * 2 + 1] = pcm16[f * 2];
            }
        }

        if (decimationFactor > 1) {
            for (drflac_uint64 f = 0; f < batchFrames; f += decimationFactor) {
                ebur128_add_frames_short(ebur, pcm16 + f * eburChannels, 1);
            }
        } else {
            ebur128_add_frames_short(ebur, pcm16, batchFrames);
        }

        frameIndex += batchFrames;
    }

    delete[] pcm16;
    drflac_free(samples, nullptr);

    double loudness = -HUGE_VAL;
    int rc = ebur128_loudness_global(ebur, &loudness);
    if (rc != EBUR128_SUCCESS) loudness = targetLoudness;

    double range = 0.0;
    ebur128_loudness_range(ebur, &range);

    double peak = 0.0;
    if (truePeak) {
        for (int ch = 0; ch < eburChannels; ch++) {
            double tp = 0.0;
            ebur128_true_peak(ebur, ch, &tp);
            if (tp > peak) peak = tp;
        }
    } else {
        for (int ch = 0; ch < eburChannels; ch++) {
            double sp = 0.0;
            ebur128_sample_peak(ebur, ch, &sp);
            if (sp > peak) peak = sp;
        }
    }

    double gain = targetLoudness - loudness;

    ebur128_destroy(&ebur);

    LOGI("Native FLAC: gain=%.2f loudness=%.2f range=%.2f peak=%.6f frames=%llu",
         gain, loudness, range, peak, (unsigned long long)totalPCMFrames);

    jdouble values[6] = {gain, peak, loudness, range, peak, targetLoudness};
    jdoubleArray result = env->NewDoubleArray(6);
    env->SetDoubleArrayRegion(result, 0, 6, values);
    return result;
}
