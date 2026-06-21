/*
 * native_decoder_jni.cpp: Unified native audio decoder + ReplayGain scanner.
 *
 * Uses NDK AMediaExtractor + AMediaCodec to decode all audio formats natively,
 * feeding PCM directly to libebur128. Single JNI call for the entire file.
 *
 * Paths covered:
 * - audio/raw: read PCM directly from extractor (no codec needed)
 * - compressed (audio/flac, audio/mpeg, etc.): AMediaCodec decode → PCM → ebur128
 */

#include <jni.h>
#include <string>
#include <cmath>
#include <android/log.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

extern "C" {
#include <ebur128.h>
}

#define LOG_TAG "NativeDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static constexpr int TIMEOUT_US = 10000;  // 10ms
static constexpr int BATCH_FRAMES = 8192;

/**
 * Initialize ebur128 state with given parameters.
 */
static ebur128_state* create_ebur_state(int channels, int sampleRate,
                                         bool truePeak, bool dualMono,
                                         double targetLoudness) {
    int mode = EBUR128_MODE_I | EBUR128_MODE_SAMPLE_PEAK | EBUR128_MODE_HISTOGRAM;
    if (truePeak) mode |= EBUR128_MODE_TRUE_PEAK;

    int eburChannels = (channels == 1 && dualMono) ? 2 : channels;
    ebur128_state* ebur = ebur128_init(eburChannels, sampleRate, mode);
    if (!ebur) return nullptr;

    if (channels == 1 && dualMono) {
        ebur128_set_channel(ebur, 0, EBUR128_DUAL_MONO);
        ebur128_set_channel(ebur, 1, EBUR128_DUAL_MONO);
    }
    return ebur;
}

/**
 * Feed PCM frames (16-bit interleaved) to ebur128 with optional decimation.
 */
static void feed_pcm_short(ebur128_state* ebur, const short* pcm,
                            int channels, int frameCount, int decimationFactor) {
    if (decimationFactor > 1) {
        for (int i = 0; i < frameCount; i += decimationFactor) {
            ebur128_add_frames_short(ebur, pcm + i * channels, 1);
        }
    } else {
        ebur128_add_frames_short(ebur, pcm, frameCount);
    }
}

/**
 * Fill result array from ebur128 state.
 */
static jdoubleArray make_result(JNIEnv* env, ebur128_state* ebur,
                                 double targetLoudness) {
    double loudness = -HUGE_VAL;
    int rc = ebur128_loudness_global(ebur, &loudness);
    if (rc != EBUR128_SUCCESS) loudness = targetLoudness;

    double range = 0.0;
    ebur128_loudness_range(ebur, &range);

    double peak = 0.0;
    for (unsigned int ch = 0; ch < ebur->channels; ch++) {
        double p = 0.0;
        ebur128_sample_peak(ebur, ch, &p);
        if (p > peak) peak = p;
    }

    double gain = targetLoudness - loudness;

    jdouble values[6] = {gain, peak, loudness, range, peak, targetLoudness};
    jdoubleArray result = env->NewDoubleArray(6);
    env->SetDoubleArrayRegion(result, 0, 6, values);
    return result;
}

// ============================================================
// Path 1: Raw PCM via AMediaExtractor (no codec needed)
// ============================================================
static jdoubleArray decode_raw_pcm(JNIEnv* env, AMediaExtractor* extractor,
                                    int channels, int sampleRate,
                                    int decimationFactor, double targetLoudness) {
    LOGI("Raw PCM direct read: ch=%d rate=%d decimation=%d",
         channels, sampleRate, decimationFactor);

    ebur128_state* ebur = create_ebur_state(channels, sampleRate, false, false, targetLoudness);
    if (!ebur) return nullptr;

    short batchBuf[BATCH_FRAMES * 2];  // max 2 channels

    while (true) {
        ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, (uint8_t*)batchBuf, sizeof(batchBuf));
        if (sampleSize <= 0) break;

        int frames = (int)sampleSize / (channels * 2);
        feed_pcm_short(ebur, batchBuf, channels, frames, decimationFactor);
        AMediaExtractor_advance(extractor);
    }

    jdoubleArray result = make_result(env, ebur, targetLoudness);
    ebur128_destroy(&ebur);
    return result;
}

// ============================================================
// Path 2: Compressed audio via AMediaExtractor + AMediaCodec
// ============================================================
static jdoubleArray decode_compressed(JNIEnv* env, AMediaExtractor* extractor,
                                       AMediaFormat* format,
                                       const char* mime,
                                       int channels, int sampleRate,
                                       int decimationFactor, double targetLoudness) {
    LOGI("Compressed decode: mime=%s ch=%d rate=%d decimation=%d",
         mime, channels, sampleRate, decimationFactor);

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
    if (!codec) {
        LOGE("Failed to create decoder for %s", mime);
        return nullptr;
    }

    media_status_t status = AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
    if (status != AMEDIA_OK) {
        LOGE("Failed to configure decoder: %d", status);
        AMediaCodec_delete(codec);
        return nullptr;
    }

    AMediaCodec_start(codec);

    ebur128_state* ebur = create_ebur_state(channels, sampleRate, false, false, targetLoudness);
    if (!ebur) {
        AMediaCodec_stop(codec);
        AMediaCodec_delete(codec);
        return nullptr;
    }

    bool inputDone = false;
    bool outputDone = false;

    int consecutiveErrors = 0;

    while (!outputDone) {
        // Feed input
        if (!inputDone) {
            ssize_t inputIdx = AMediaCodec_dequeueInputBuffer(codec, TIMEOUT_US);
            if (inputIdx >= 0) {
                size_t bufSize;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, inputIdx, &bufSize);
                if (buf) {
                    ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, buf, bufSize);
                    if (sampleSize < 0) {
                        AMediaCodec_queueInputBuffer(codec, inputIdx, 0, 0, 0,
                                                     AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        int64_t timeUs = AMediaExtractor_getSampleTime(extractor);
                        AMediaCodec_queueInputBuffer(codec, inputIdx, 0, sampleSize, timeUs, 0);
                        AMediaExtractor_advance(extractor);
                    }
                }
            }
        }

        // Drain output
        AMediaCodecBufferInfo info;
        ssize_t outputIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, TIMEOUT_US);

        if (outputIdx >= 0) {
            if (info.size > 0) {
                size_t bufSize;
                uint8_t* buf = AMediaCodec_getOutputBuffer(codec, outputIdx, &bufSize);
                if (buf) {
                    short* pcm = (short*)(buf + info.offset);
                    int frames = info.size / (channels * 2);

                    // Copy to batch buffer to handle decimation
                    if (decimationFactor > 1) {
                        for (int i = 0; i < frames; i += decimationFactor) {
                            ebur128_add_frames_short(ebur, pcm + i * channels, 1);
                        }
                    } else {
                        ebur128_add_frames_short(ebur, pcm, frames);
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outputIdx, false);
            if ((info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
                outputDone = true;
            }
        } else if (outputIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFmt = AMediaCodec_getOutputFormat(codec);
            if (newFmt) {
                int32_t newCh;
                if (AMediaFormat_getInt32(newFmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &newCh)) {
                    if (newCh != channels) {
                        LOGE("Unexpected channel count change: %d -> %d. Aborting decode.", channels, newCh);
                        AMediaFormat_delete(newFmt);
                        outputDone = true;
                        continue;
                    }
                }
                AMediaFormat_delete(newFmt);
            }
        } else if (outputIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            // No output available yet — keep waiting
        } else {
            LOGE("dequeueOutputBuffer error: %zd", outputIdx);
            if (++consecutiveErrors > 100) {
                LOGE("Too many consecutive errors, aborting decode");
                break;
            }
        }
    }

    jdoubleArray result = make_result(env, ebur, targetLoudness);
    ebur128_destroy(&ebur);
    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    return result;
}

// ============================================================
// JNI entry point
// ============================================================
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_voxly_data_local_replaygain_native_NativeAudioDecoder_decodeFileGain(
    JNIEnv* env, jobject thiz,
    jstring filePath,
    jdouble targetLoudness,
    jboolean truePeak,
    jboolean dualMono,
    jint maxSampleRate
) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    if (!path) return nullptr;

    // Open file with AMediaExtractor
    AMediaExtractor* extractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSource(extractor, path);
    env->ReleaseStringUTFChars(filePath, path);

    if (status != AMEDIA_OK) {
        LOGE("Failed to set data source: %d", status);
        AMediaExtractor_delete(extractor);
        return nullptr;
    }

    // Find audio track
    int audioTrack = -1;
    AMediaFormat* trackFormat = nullptr;

    for (size_t i = 0; i < AMediaExtractor_getTrackCount(extractor); i++) {
        AMediaFormat* fmt = AMediaExtractor_getTrackFormat(extractor, i);
        if (fmt) {
            const char* trackMimeCheck = nullptr;
            if (AMediaFormat_getString(fmt, AMEDIAFORMAT_KEY_MIME, &trackMimeCheck)) {
                if (strncmp(trackMimeCheck, "audio/", 6) == 0) {
                    audioTrack = i;
                    trackFormat = fmt;
                    break;
                }
            }
            AMediaFormat_delete(fmt);
        }
    }

    if (audioTrack < 0) {
        LOGE("No audio track found");
        AMediaExtractor_delete(extractor);
        return nullptr;
    }

    AMediaExtractor_selectTrack(extractor, audioTrack);

    // Copy mime string before deleting format
    const char* trackMime = nullptr;
    AMediaFormat_getString(trackFormat, AMEDIAFORMAT_KEY_MIME, &trackMime);

    char mime[64];
    mime[0] = '\0';
    if (trackMime) {
        strncpy(mime, trackMime, sizeof(mime) - 1);
    }

    int32_t sampleRate = 0, channels = 0;
    AMediaFormat_getInt32(trackFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
    AMediaFormat_getInt32(trackFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);

    int decimationFactor = 1;
    int effectiveRate = sampleRate;
    if (maxSampleRate > 0 && sampleRate > maxSampleRate) {
        decimationFactor = sampleRate / maxSampleRate;
        effectiveRate = sampleRate / decimationFactor;
    }

    LOGI("Track: mime=%s ch=%d rate=%d effective=%d decimation=%d",
         mime, channels, sampleRate, effectiveRate, decimationFactor);

    jdoubleArray result = nullptr;

    if (strcmp(mime, "audio/raw") == 0) {
        result = decode_raw_pcm(env, extractor, channels, effectiveRate,
                                 decimationFactor, targetLoudness);
    } else {
        // Pass format to decode_compressed which needs it for codec config
        result = decode_compressed(env, extractor, trackFormat, mime,
                                    channels, effectiveRate,
                                    decimationFactor, targetLoudness);
    }

    AMediaFormat_delete(trackFormat);
    AMediaExtractor_delete(extractor);
    return result;
}
