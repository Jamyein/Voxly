# ReplayGain Scanner Optimization Design

**Date:** 2026-03-22
**Author:** Claude
**Status:** Approved

## 1. Overview

Optimize the ReplayGain scanning functionality in the metadata editor to address:
- Performance: Slow scanning of large Hi-Res audio files
- Accuracy: Results that differ significantly from foobar2000 output
- Reliability: Files that return no results (no obvious pattern)

## 2. Architecture

### 2.1 Dual-Track Fallback Mechanism

```
MediaExtractor Decode (Primary Path)
        ↓ Failure
Fallback Retry (Level 1)
        ↓ 3 attempts with 100ms delay
Fallback Raw PCM Read (Level 2)
        ↓ via FileInputStream
Fallback Minimum Estimation (Level 3)
        ↓ based on file size/format
Graceful Degradation
```

### 2.2 Error Classification

```kotlin
enum class DecodeResult {
    SUCCESS,
    DECODER_INIT_FAILED,
    NO_AUDIO_TRACK,
    SAMPLE_COUNT_ZERO,
    PARTIAL_FAILURE
}
```

### 2.3 Files to Modify

| File | Changes |
|------|---------|
| `ReplayGainScanner.kt` | Add fallback decode mechanism, improve error handling |
| `ReplayGainFilter.kt` | Add sample validity checks |
| `MetadataEditorViewModel.kt` | Improve scan result state handling |

## 3. Fallback Levels

### Level 1: Retry
- **Trigger:** Decoder initialization failure
- **Behavior:** Wait 100ms, retry with same parameters, max 3 attempts
- **Use case:** Temporary resource contention

### Level 2: Raw PCM Read
- **Trigger:** All retries exhausted
- **Behavior:** Use `FileInputStream` to read file directly, convert to PCM FloatArray
- **Use case:** Corrupted decoder but intact file

### Level 3: Minimum Estimation
- **Trigger:** All decode attempts failed
- **Behavior:** Estimate RMS/Peak based on file size and format
- **Use case:** Extreme edge cases, provides reasoned default

## 4. Boundary Handling

| Scenario | Current | New Behavior |
|----------|---------|--------------|
| `sampleCount <= 0` | Return null | Trigger Level 2 fallback |
| `blockRmsValues` empty | Fallback RMS calc | Level 2 raw PCM read |
| Audio too short (<100ms) | Possible calc failure | Min sample validation, return null with warning |
| Peak > 1.0 (clipping) | Normal record | Warning log, suggest encoding issue |
| Gain value abnormal (>100dB) | Normal write | Filter abnormal values, use clamped value |

### Minimum Sample Validation

```kotlin
private fun validateSampleStats(stats: SampleStats): Boolean {
    val minSamples = 1000 // ~20ms @ 48kHz
    if (stats.sampleCount < minSamples) {
        Logger.w("Sample count too low: ${stats.sampleCount}", "ReplayGainScanner")
        return false
    }
    return true
}
```

## 5. Enhanced Logging

```kotlin
Logger.e(
    "ReplayGain decode failed: file=$fileName " +
    "errorType=${decodeResult.name} " +
    "mime=$mime " +
    "sampleRate=$sampleRate " +
    "channelCount=$channelCount " +
    "fallbackLevel=$fallbackAttempt",
    cause,
    "ReplayGainScanner"
)
```

## 6. MetadataEditorViewModel State Handling

### New State Types

```kotlin
private val _replayGainScanError = MutableStateFlow<ReplayGainScanError?>(null)
val replayGainScanError: StateFlow<ReplayGainScanError?> = _replayGainScanError.asStateFlow()

sealed class ReplayGainScanError {
    data class DecodeFailed(val reason: String) : ReplayGainScanError()
    data class NoAudioTrack(val filePath: String) : ReplayGainScanError()
    data class PermissionDenied(val filePath: String) : ReplayGainScanError()
    data class Unknown(val message: String) : ReplayGainScanError()
}
```

### UI Feedback by Error Type

| Error Type | User Message |
|------------|--------------|
| `DecodeFailed` | "该文件解码失败，尝试其他来源的版本" |
| `NoAudioTrack` | "未找到音频轨道，文件可能已损坏" |
| `PermissionDenied` | "无读取权限，请检查文件访问权限" |
| `Unknown` | "扫描失败：{具体错误信息}" |

## 7. Approval

- [x] Overall architecture (Section 2)
- [x] Fallback mechanism (Section 3)
- [x] Boundary handling (Section 4)
- [x] Logging enhancement (Section 5)
- [x] ViewModel state handling (Section 6)
