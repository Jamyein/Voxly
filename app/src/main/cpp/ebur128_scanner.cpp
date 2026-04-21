/*
 * ebur128-scanner: JNI bridge for Android
 *
 * Pure computation engine using libebur128.
 * Audio decoding is handled by Android MediaCodec on the Kotlin side.
 *
 * Data flow: Kotlin MediaCodec -> PCM short[] -> JNI -> libebur128 -> LUFS/peak
 *
 * Best practices followed:
 * - RegisterNatives for explicit method registration (faster, earlier error detection)
 * - GetShortArrayRegion instead of GetShortArrayElements (1 JNI call vs 2)
 * - Direct ByteBuffer for zero-copy large data transfer
 * - Pre-allocated result array to avoid heap allocation per call
 */

#include <jni.h>
#include <string>
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
    void* cached_buffer_addr;
    size_t cached_buffer_size;
};

// Forward declarations
static jlong nativeCreate(JNIEnv* env, jobject thiz, jint channels, jint sample_rate,
                          jboolean true_peak, jboolean dual_mono, jdouble target_loudness);
static void nativeProcessFrames(JNIEnv* env, jobject thiz, jlong scannerPtr,
                                jshortArray samples, jint frameCount);
static jint nativeProcessBuffer(JNIEnv* env, jobject thiz, jlong scannerPtr,
                                jobject buffer, jint size);
static jboolean nativeGetResult(JNIEnv* env, jobject thiz, jlong scannerPtr, jdoubleArray result);
static void nativeDestroy(JNIEnv* env, jobject thiz, jlong scannerPtr);
static jstring nativeGetVersion(JNIEnv* env, jobject thiz);

static const JNINativeMethod gMethods[] = {
    {"nativeCreate", "(IIZZD)J", (void*)nativeCreate},
    {"nativeProcessFrames", "(J[SI)V", (void*)nativeProcessFrames},
    {"nativeProcessBuffer", "(JLjava/nio/ByteBuffer;I)I", (void*)nativeProcessBuffer},
    {"nativeGetResult", "(J[D)Z", (void*)nativeGetResult},
    {"nativeDestroy", "(J)V", (void*)nativeDestroy},
    {"nativeGetVersion", "()Ljava/lang/String;", (void*)nativeGetVersion},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/voxly/data/local/replaygain/native/EbuR128NativeScanner");
    if (clazz == nullptr) {
        LOGE("JNI_OnLoad: FindClass failed for EbuR128NativeScanner");
        return JNI_ERR;
    }

    jint rc = env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
    if (rc != JNI_OK) {
        LOGE("JNI_OnLoad: RegisterNatives failed with code %d", rc);
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: Registered %zu native methods", sizeof(gMethods) / sizeof(gMethods[0]));
    return JNI_VERSION_1_6;
}

static jlong nativeCreate(JNIEnv* env, jobject thiz, jint channels, jint sample_rate,
                          jboolean true_peak, jboolean dual_mono, jdouble target_loudness) {
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
    state->cached_buffer_addr = nullptr;
    state->cached_buffer_size = 0;

    LOGD("Scanner created: ch=%d sr=%d tp=%d dm=%d target=%.1f",
         channels, sample_rate, true_peak, dual_mono, target_loudness);

    return reinterpret_cast<jlong>(state);
}

static void nativeProcessFrames(JNIEnv* env, jobject thiz, jlong scannerPtr,
                                jshortArray samples, jint frameCount) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (!state || !state->ebur) return;

    // Use GetShortArrayRegion instead of GetShortArrayElements + ReleaseShortArrayElements
    // This reduces 2 JNI calls to 1 and avoids copy
    jshort buffer[8192];
    jsize totalSamples = frameCount * state->channels;

    for (jsize offset = 0; offset < totalSamples; offset += 8192) {
        jsize chunkSize = std::min((jsize)8192, totalSamples - offset);
        env->GetShortArrayRegion(samples, offset, chunkSize, buffer);
        ebur128_add_frames_short(state->ebur, buffer, chunkSize / state->channels);
    }
}

static jint nativeProcessBuffer(JNIEnv* env, jobject thiz, jlong scannerPtr,
                                jobject buffer, jint size) {
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

static jboolean nativeGetResult(JNIEnv* env, jobject thiz, jlong scannerPtr, jdoubleArray result) {
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

    LOGD("Result: gain=%.2f peak=%.6f loudness=%.2f range=%.2f",
         gain, peak, loudness, range);

    return JNI_TRUE;
}

static void nativeDestroy(JNIEnv* env, jobject thiz, jlong scannerPtr) {
    auto* state = reinterpret_cast<ScannerState*>(scannerPtr);
    if (state) {
        if (state->ebur) {
            ebur128_destroy(&state->ebur);
        }
        delete state;
    }
}

static jstring nativeGetVersion(JNIEnv* env, jobject thiz) {
    char version[128];
    snprintf(version, sizeof(version),
             "ebur128-scanner v1.0 (libebur128 %d.%d.%d)",
             EBUR128_VERSION_MAJOR, EBUR128_VERSION_MINOR, EBUR128_VERSION_PATCH);
    return env->NewStringUTF(version);
}
