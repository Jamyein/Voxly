#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <cmath>
#include <cstdio>
#include "libebur128/ebur128.h"

#define LOG_TAG "Ebur128Jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Initialize EBU R128 scanner
 * @param env JNI environment
 * @param thiz Java object
 * @param channels Number of audio channels
 * @param sampleRate Sample rate in Hz
 * @param mode Measurement mode (bitmask)
 * @return Native handle (pointer to ebur128_state cast to jlong)
 */
JNIEXPORT jlong JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeInit(
        JNIEnv* env,
        jobject /* thiz */,
        jint channels,
        jint sampleRate,
        jint mode) {
    
    LOGD("Initializing EBU R128 scanner: channels=%d, sampleRate=%d, mode=%d",
         channels, sampleRate, mode);
    
    ebur128_state* state = ebur128_init(
        static_cast<unsigned int>(channels),
        static_cast<unsigned long>(sampleRate),
        mode
    );
    
    if (!state) {
        LOGE("Failed to initialize ebur128 state");
        return 0;
    }
    
    LOGD("EBU R128 scanner initialized successfully");
    return reinterpret_cast<jlong>(state);
}

/**
 * Add float audio frames for processing
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 * @param samples Float array of interleaved audio samples
 * @param frames Number of frames (not samples)
 * @return Error code (0 = success)
 */
JNIEXPORT jint JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeAddFramesFloat(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jfloatArray samples,
        jint frames) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeAddFramesFloat");
        return -1;
    }
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    
    jfloat* buffer = env->GetFloatArrayElements(samples, nullptr);
    if (!buffer) {
        LOGE("Failed to get float array elements");
        return -1;
    }
    
    int result = ebur128_add_frames_float(state, buffer, static_cast<size_t>(frames));
    
    env->ReleaseFloatArrayElements(samples, buffer, JNI_ABORT);
    
    return result;
}

/**
 * Add short audio frames for processing
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 * @param samples Short array of interleaved audio samples
 * @param frames Number of frames (not samples)
 * @return Error code (0 = success)
 */
JNIEXPORT jint JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeAddFramesShort(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jshortArray samples,
        jint frames) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeAddFramesShort");
        return -1;
    }
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    
    jshort* buffer = env->GetShortArrayElements(samples, nullptr);
    if (!buffer) {
        LOGE("Failed to get short array elements");
        return -1;
    }
    
    int result = ebur128_add_frames_short(state, buffer, static_cast<size_t>(frames));
    
    env->ReleaseShortArrayElements(samples, buffer, JNI_ABORT);
    
    return result;
}

/**
 * Get global integrated loudness
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 * @return Loudness in LUFS, or -HUGE_VAL if not available
 */
JNIEXPORT jdouble JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeGetLoudnessGlobal(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeGetLoudnessGlobal");
        return -HUGE_VAL;
    }
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    
    double loudness;
    int result = ebur128_loudness_global(state, &loudness);
    
    if (result != EBUR128_SUCCESS) {
        LOGE("Failed to get global loudness: %d", result);
        return -HUGE_VAL;
    }
    
    return loudness;
}

/**
 * Get maximum sample peak across all channels
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 * @return Maximum sample peak (1.0 = 0 dBFS)
 */
JNIEXPORT jdouble JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeGetSamplePeak(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeGetSamplePeak");
        return 1.0;
    }
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    
    double maxPeak = 0.0;
    
    // Iterate through all channels and find the maximum peak
    for (unsigned int i = 0; i < state->channels; i++) {
        double peak;
        int result = ebur128_sample_peak(state, i, &peak);
        if (result == EBUR128_SUCCESS && peak > maxPeak) {
            maxPeak = peak;
        }
    }
    
    return maxPeak;
}

/**
 * Get maximum true peak across all channels
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 * @return Maximum true peak (1.0 = 0 dBTP)
 */
JNIEXPORT jdouble JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeGetTruePeak(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeGetTruePeak");
        return 1.0;
    }
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    
    double maxTruePeak = 0.0;
    
    // Iterate through all channels and find the maximum true peak
    for (unsigned int i = 0; i < state->channels; i++) {
        double truePeak;
        int result = ebur128_true_peak(state, i, &truePeak);
        if (result == EBUR128_SUCCESS && truePeak > maxTruePeak) {
            maxTruePeak = truePeak;
        }
    }
    
    return maxTruePeak;
}

/**
 * Destroy EBU R128 scanner and free resources
 * @param env JNI environment
 * @param thiz Java object
 * @param handle Native handle
 */
JNIEXPORT void JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_nativeDestroy(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle) {
    
    if (!handle) {
        LOGE("Invalid handle in nativeDestroy");
        return;
    }
    
    LOGD("Destroying EBU R128 scanner");
    
    ebur128_state* state = reinterpret_cast<ebur128_state*>(handle);
    ebur128_destroy(&state);
    
    LOGD("EBU R128 scanner destroyed");
}

/**
 * Get library version
 * @param env JNI environment
 * @param thiz Java object
 * @return Version string (e.g., "1.2.6")
 */
JNIEXPORT jstring JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_getVersion(
        JNIEnv* env,
        jclass /* clazz */) {
    
    int major, minor, patch;
    ebur128_get_version(&major, &minor, &patch);
    
    char version[32];
    snprintf(version, sizeof(version), "%d.%d.%d", major, minor, patch);
    
    return env->NewStringUTF(version);
}

/**
 * Calculate global loudness across multiple states (for album gain)
 * Uses ebur128_loudness_global_multiple from libebur128.
 * 
 * @param env JNI environment
 * @param thiz Java object
 * @param handlesArray Array of native handles (jlongArray)
 * @return Combined loudness in LUFS, or -HUGE_VAL on error
 */
JNIEXPORT jdouble JNICALL
Java_com_voxly_data_local_replaygain_Ebur128Scanner_getLoudnessGlobalMultiple(
        JNIEnv* env,
        jclass /* clazz */,
        jlongArray handlesArray) {
    
    if (!handlesArray) {
        LOGE("Null handles array in getLoudnessGlobalMultiple");
        return -HUGE_VAL;
    }
    
    jsize count = env->GetArrayLength(handlesArray);
    if (count == 0) {
        LOGD("Empty handles array");
        return -HUGE_VAL;
    }
    
    if (count > 1000) {
        LOGE("Too many handles: %d (max 1000)", count);
        return -HUGE_VAL;
    }
    
    jlong* handles = env->GetLongArrayElements(handlesArray, nullptr);
    if (!handles) {
        LOGE("Failed to get handles array elements");
        return -HUGE_VAL;
    }
    
    // Allocate array of state pointers
    ebur128_state** states = new ebur128_state*[count];
    bool valid = true;
    
    for (jsize i = 0; i < count; i++) {
        if (handles[i] == 0) {
            LOGE("Invalid handle at index %d", i);
            valid = false;
            break;
        }
        states[i] = reinterpret_cast<ebur128_state*>(handles[i]);
    }
    
    double loudness = -HUGE_VAL;
    
    if (valid) {
        int result = ebur128_loudness_global_multiple(states, static_cast<size_t>(count), &loudness);
        if (result != EBUR128_SUCCESS) {
            LOGE("ebur128_loudness_global_multiple failed: %d", result);
            loudness = -HUGE_VAL;
        } else {
            LOGD("Album loudness calculated: %.2f LUFS for %d tracks", loudness, count);
        }
    }
    
    delete[] states;
    env->ReleaseLongArrayElements(handlesArray, handles, JNI_ABORT);
    
    return loudness;
}

} // extern "C"
