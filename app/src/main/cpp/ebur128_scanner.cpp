/*
 * ebur128-scanner: JNI bridge for Android
 *
 * Pure computation engine using libebur128.
 * Audio decoding is handled by Android MediaCodec on the Kotlin side.
 *
 * Data flow: Kotlin MediaCodec -> PCM short[] -> JNI -> libebur128 -> LUFS/peak
 *
 * Best practices followed:
 * - Explicit Java_* JNI exports (avoid classloader-sensitive JNI_OnLoad registration)
 * - GetShortArrayRegion instead of GetShortArrayElements (1 JNI call vs 2)
 * - Direct ByteBuffer for zero-copy large data transfer
 * - Pre-allocated result array to avoid heap allocation per call
 */

#include <jni.h>
#include <string>
#include <cmath>
#include <algorithm>
#include <cstdio>

extern "C" {
#include <ebur128.h>
}

#include <android/log.h>

#define LOG_TAG "EbuR128Scanner"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Scanner state object managed by native code.
 * Created by nativeCreate, destroyed by nativeDestroy.
 */
struct ScannerState {
    ebur128_state* ebur;
    int channels;
    int sample_rate;
    bool true_peak;
    bool dual_mono;
    double target_loudness;
    void* cached_buffer_addr;
    size_t cached_buffer_size;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeCreate(
    JNIEnv* env, jobject thiz,
    jint channels, jint sample_rate,
    jboolean true_peak, jboolean dual_mono, jdouble target_loudness
) {
    int mode = EBUR128_MODE_I | EBUR128_MODE_SAMPLE_PEAK | EBUR128_MODE_HISTOGRAM;
    if (true_peak) mode |= EBUR128_MODE_TRUE_PEAK;

    int ebur_channels = (channels == 1 && dual_mono) ? 2 : channels;

    ebur128_state* ebur = ebur128_init(ebur_channels, sample_rate, mode);
    if (!ebur) {
        LOGE("Failed to initialize ebur128 state");
        return 0;
    }

    if (channels == 1 && dual_mono) {
        ebur128_set_channel(ebur, 0, EBUR128_DUAL_MONO);
        ebur128_set_channel(ebur, 1, EBUR128_DUAL_MONO);
    }

    auto* state = new ScannerState();
    state->ebur = ebur;
    state->channels = channels;
    state->sample_rate = sample_rate;
    state->true_peak = true_peak;
    state->dual_mono = dual_mono;
    state->target_loudness = target_loudness;
    state->cached_buffer_addr = nullptr;
    state->cached_buffer_size = 0;

    LOGD("Scanner created: ch=%d sr=%d tp=%d dm=%d target=%.1f",
         channels, sample_rate, true_peak, dual_mono, target_loudness);

    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT void JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeProcessFrames(
    JNIEnv* env, jobject thiz, jlong scannerPtr, jshortArray samples, jint frameCount
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return;

    // Use GetShortArrayRegion instead of GetShortArrayElements + ReleaseShortArrayElements
    // This reduces 2 JNI calls to 1 and avoids copy
    jshort buffer[8192];
    jsize totalSamples = frameCount * state->channels;

    for (jsize offset = 0; offset < totalSamples; offset += 8192) {
        jsize chunkSize = std::min((jsize)8192, totalSamples - offset);
        env->GetShortArrayRegion(samples, offset, chunkSize, buffer);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("GetShortArrayRegion failed");
            return;
        }
        ebur128_add_frames_short(state->ebur, buffer, chunkSize / state->channels);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeProcessBuffer(
    JNIEnv* env, jobject thiz, jlong scannerPtr, jobject buffer, jint size
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return 0;

    jbyte* data = static_cast<jbyte*>(env->GetDirectBufferAddress(buffer));
    if (!data) return 0;

    state->cached_buffer_addr = data;
    state->cached_buffer_size = size;

    size_t frameCount = size / (state->channels * sizeof(short));
    ebur128_add_frames_short(state->ebur, reinterpret_cast<short*>(data), frameCount);

    return static_cast<jint>(frameCount);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeGetResult(
    JNIEnv* env, jobject thiz, jlong scannerPtr, jdoubleArray result
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return JNI_FALSE;

    double loudness = -HUGE_VAL;
    int rc = ebur128_loudness_global(state->ebur, &loudness);
    if (rc != EBUR128_SUCCESS) {
        loudness = state->target_loudness;
    }

    double range = 0.0;
    ebur128_loudness_range(state->ebur, &range);

    double peak = 0.0;
    if (state->true_peak) {
        for (int ch = 0; ch < state->channels; ch++) {
            double tp = 0.0;
            ebur128_true_peak(state->ebur, ch, &tp);
            if (tp > peak) peak = tp;
        }
    } else {
        for (int ch = 0; ch < state->channels; ch++) {
            double sp = 0.0;
            ebur128_sample_peak(state->ebur, ch, &sp);
            if (sp > peak) peak = sp;
        }
    }

    double gain = state->target_loudness - loudness;

    jdouble values[6];
    values[0] = gain;
    values[1] = peak;
    values[2] = loudness;
    values[3] = range;
    values[4] = peak;
    values[5] = state->target_loudness;

    env->SetDoubleArrayRegion(result, 0, 6, values);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("SetDoubleArrayRegion failed in nativeGetResult");
        return JNI_FALSE;
    }

    LOGD("Result: gain=%.2f peak=%.6f loudness=%.2f range=%.2f",
         gain, peak, loudness, range);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_00024Companion_nativeGetAlbumGain(
    JNIEnv* env, jobject thiz,
    jlongArray scannerPtrs
) {
    jsize count = env->GetArrayLength(scannerPtrs);
    if (count <= 0) {
        jdoubleArray empty = env->NewDoubleArray(3);
        return empty;
    }

    jlong* ptrs = env->GetLongArrayElements(scannerPtrs, nullptr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("GetLongArrayElements failed");
        jdoubleArray empty = env->NewDoubleArray(3);
        return empty;
    }
    if (!ptrs) {
        jdoubleArray empty = env->NewDoubleArray(3);
        return empty;
    }

    auto** states = new ScannerState*[count];
    auto** sts = new ebur128_state*[count];
    jsize validCount = 0;
    for (jsize i = 0; i < count; i++) {
        if (ptrs[i] == 0) continue;
        auto* s = reinterpret_cast<ScannerState*>(ptrs[i]);
        if (!s || !s->ebur) continue;
        states[validCount] = s;
        sts[validCount] = s->ebur;
        validCount++;
    }
    if (validCount == 0) {
        env->ReleaseLongArrayElements(scannerPtrs, ptrs, JNI_ABORT);
        delete[] states;
        delete[] sts;
        jdoubleArray empty = env->NewDoubleArray(3);
        return empty;
    }
    count = validCount;

    double albumLoudness = -HUGE_VAL;
    int rc = ebur128_loudness_global_multiple(sts, count, &albumLoudness);
    if (rc != EBUR128_SUCCESS) {
        albumLoudness = states[0]->target_loudness;
    }

    double albumRange = 0.0;
    int rcRange = ebur128_loudness_range_multiple(sts, count, &albumRange);
    if (rcRange != EBUR128_SUCCESS) {
        albumRange = 0.0;
    }

    double albumPeak = 0.0;
    for (jsize i = 0; i < count; i++) {
        int ch_count = states[i]->channels;
        for (int ch = 0; ch < ch_count; ch++) {
            double p = 0.0;
            if (states[i]->true_peak) {
                ebur128_true_peak(states[i]->ebur, ch, &p);
            } else {
                // Sample peak is sufficient when true_peak mode is disabled
                ebur128_sample_peak(states[i]->ebur, ch, &p);
            }
            if (p > albumPeak) albumPeak = p;
        }
    }

    delete[] sts;
    delete[] states;
    env->ReleaseLongArrayElements(scannerPtrs, ptrs, JNI_ABORT);

    jdouble values[3] = {albumLoudness, albumRange, albumPeak};
    jdoubleArray result = env->NewDoubleArray(3);
    env->SetDoubleArrayRegion(result, 0, 3, values);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("SetDoubleArrayRegion failed in nativeGetAlbumGain");
        return result;
    }

    LOGD("Album gain: loudness=%.2f range=%.2f peak=%.6f tracks=%d",
         albumLoudness, albumRange, albumPeak, count);

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeDestroy(
    JNIEnv* env, jobject thiz,
    jlong scannerPtr
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (state) {
        if (state->ebur) {
            ebur128_destroy(&state->ebur);
        }
        delete state;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeGetVersion(
    JNIEnv* env, jobject thiz
) {
    char version[128];
    snprintf(version, sizeof(version),
             "ebur128-scanner v1.0 (libebur128 %d.%d.%d)",
             EBUR128_VERSION_MAJOR, EBUR128_VERSION_MINOR, EBUR128_VERSION_PATCH);
    return env->NewStringUTF(version);
}
