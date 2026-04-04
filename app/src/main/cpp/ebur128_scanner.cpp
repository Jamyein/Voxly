/*
 * ebur128-scanner: JNI bridge for Android
 *
 * Pure computation engine using libebur128.
 * Audio decoding is handled by Android MediaCodec on the Kotlin side.
 *
 * Data flow: Kotlin MediaCodec -> PCM short[] -> JNI -> libebur128 -> LUFS/peak
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>

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
};

/**
 * Create a new scanner instance.
 * Returns a native pointer (as jlong) to be passed to subsequent calls.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeCreate(
    JNIEnv* env, jobject thiz,
    jint channels,
    jint sample_rate,
    jboolean true_peak,
    jboolean dual_mono,
    jdouble target_loudness
) {
    int mode = EBUR128_MODE_I | EBUR128_MODE_SAMPLE_PEAK;
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

    LOGD("Scanner created: ch=%d sr=%d tp=%d dm=%d target=%.1f",
         channels, sample_rate, true_peak, dual_mono, target_loudness);

    return reinterpret_cast<jlong>(state);
}

/**
 * Process a block of PCM samples (16-bit signed little-endian).
 *
 * @param scannerPtr Native pointer from nativeCreate
 * @param samples    PCM samples as short array (interleaved: L, R, L, R, ...)
 * @param frameCount Number of frames (NOT sample count). For stereo, samples.length = frameCount * 2
 */
extern "C" JNIEXPORT void JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeProcessFrames(
    JNIEnv* env, jobject thiz,
    jlong scannerPtr,
    jshortArray samples,
    jint frameCount
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return;

    jshort* data = env->GetShortArrayElements(samples, nullptr);
    ebur128_add_frames_short(state->ebur, data, static_cast<size_t>(frameCount));
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);
}

/**
 * Process a block of PCM samples from a Direct ByteBuffer.
 * This is the most efficient method for large data transfers.
 *
 * @param scannerPtr Native pointer from nativeCreate
 * @param buffer     Direct ByteBuffer containing S16 PCM data
 * @param size       Buffer size in bytes
 * @return Number of frames processed
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeProcessBuffer(
    JNIEnv* env, jobject thiz,
    jlong scannerPtr,
    jobject buffer,
    jint size
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return 0;

    jbyte* data = static_cast<jbyte*>(env->GetDirectBufferAddress(buffer));
    if (!data) return 0;

    size_t frameCount = size / (state->channels * sizeof(short));
    ebur128_add_frames_short(state->ebur, reinterpret_cast<short*>(data), frameCount);

    return static_cast<jint>(frameCount);
}

/**
 * Get scan results.
 *
 * Returns double[6]:
 *   [0] track_gain (dB)
 *   [1] track_peak
 *   [2] track_loudness (LUFS)
 *   [3] track_range (LU)
 *   [4] true_peak (or sample peak if true_peak=false)
 *   [5] reference_loudness
 */
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_voxly_data_local_replaygain_native_EbuR128NativeScanner_nativeGetResult(
    JNIEnv* env, jobject thiz,
    jlong scannerPtr
) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return nullptr;

    double loudness = -HUGE_VAL;
    int rc = ebur128_loudness_global(state->ebur, &loudness);
    if (rc != EBUR128_SUCCESS) {
        loudness = state->target_loudness;
    }

    double range = 0.0;
    ebur128_loudness_range(state->ebur, &range);

    double peak = 0.0;
    for (int ch = 0; ch < state->channels; ch++) {
        double ch_peak = 0.0;
        ebur128_sample_peak(state->ebur, ch, &ch_peak);
        if (ch_peak > peak) peak = ch_peak;
    }

    // Calculate gain
    double gain = state->target_loudness - loudness;

    jdoubleArray result = env->NewDoubleArray(6);
    jdouble values[6] = {
        gain,               // [0] track_gain
        peak,               // [1] track_peak
        loudness,           // [2] track_loudness
        range,              // [3] track_range
        peak,               // [4] true_peak (same as sample peak for now)
        state->target_loudness  // [5] reference_loudness
    };
    env->SetDoubleArrayRegion(result, 0, 6, values);

    LOGD("Result: gain=%.2f peak=%.6f loudness=%.2f range=%.2f",
         gain, peak, loudness, range);

    return result;
}

/**
 * Destroy the scanner and free native resources.
 */
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

/**
 * Get library version info.
 */
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
