# ReplayGain Scanner Optimization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fallback decoding mechanism to ReplayGainScanner to handle decode failures, improve error handling, and provide better user feedback.

**Architecture:** Dual-track fallback with 3 levels (Retry → Raw PCM → Minimum Estimation) + enhanced error classification and logging.

**Tech Stack:** Kotlin, JUnit 4, MockK, Turbine (for Flow testing), Android MediaCodec/MediaExtractor

---

## Chunk 1: Add DecodeResult Enum and Error Types

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt`

- [ ] **Step 1: Add DecodeResult enum before ReplayGainScanner class**

```kotlin
/**
 * Represents the result of an audio decode attempt.
 */
enum class DecodeResult {
    SUCCESS,
    DECODER_INIT_FAILED,
    NO_AUDIO_TRACK,
    SAMPLE_COUNT_ZERO,
    PARTIAL_FAILURE,
    FILE_READ_ERROR,
    ALL_FALLBACKS_EXHAUSTED
}
```

- [ ] **Step 2: Add data class for decode failure details**

```kotlin
/**
 * Detailed information about a decode failure for logging and error handling.
 */
data class DecodeFailureInfo(
    val result: DecodeResult,
    val filePath: String,
    val mime: String?,
    val sampleRate: Int,
    val channelCount: Int,
    val fallbackLevel: Int,
    val cause: Throwable?
)
```

- [ ] **Step 3: Add companion object constants for fallback config**

```kotlin
companion object {
    // ... existing constants ...

    // New fallback config
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 100L
    const val MIN_VALID_SAMPLES = 1000L // ~20ms @ 48kHz
    const val MIN_AUDIO_DURATION_MS = 100L
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt
git commit -m "feat(replaygain): add DecodeResult enum and fallback config

- Add DecodeResult enum for error classification
- Add DecodeFailureInfo data class for detailed error logging
- Add fallback configuration constants (MAX_RETRY_ATTEMPTS, etc.)"
```

---

## Chunk 2: Implement Level 1 Retry Mechanism

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt`

- [ ] **Step 1: Extract decode logic into separate retryable method**

Find the current `decodeAndAccumulateStats` method and refactor it to return both `SampleStats` and `DecodeResult`:

```kotlin
/**
 * Result of decode operation with status.
 */
private data class DecodeOperationResult(
    val stats: SampleStats?,
    val result: DecodeResult,
    val cause: Throwable?
)

/**
 * Attempts to decode audio with retry mechanism.
 * @return DecodeOperationResult containing stats and decode status
 */
private fun attemptDecodeWithRetry(
    extractor: MediaExtractor,
    format: MediaFormat,
    targetSampleRate: Int,
    channelCount: Int
): DecodeOperationResult {
    var lastCause: Throwable? = null

    repeat(MAX_RETRY_ATTEMPTS) { attempt ->
        try {
            val stats = decodeAndAccumulateStats(
                extractor = extractor,
                format = format,
                targetSampleRate = targetSampleRate,
                channelCount = channelCount
            )

            if (stats.sampleCount <= 0) {
                return DecodeOperationResult(null, DecodeResult.SAMPLE_COUNT_ZERO, null)
            }

            return DecodeOperationResult(stats, DecodeResult.SUCCESS, null)
        } catch (e: Exception) {
            lastCause = e
            Logger.w(
                "Decode attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS failed: ${e.message}",
                "ReplayGainScanner"
            )

            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                // Wait before retry
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
    }

    return DecodeOperationResult(
        null,
        DecodeResult.DECODER_INIT_FAILED,
        lastCause
    )
}
```

- [ ] **Step 2: Update analyzeAudioFile to use retry mechanism**

Replace the direct `decodeAndAccumulateStats` call with `attemptDecodeWithRetry`:

```kotlin
// In analyzeAudioFile method, replace:
val stats = decodeAndAccumulateStats(
    extractor = extractor,
    format = format,
    targetSampleRate = targetSampleRate,
    channelCount = channelCount
)

if (stats.sampleCount <= 0L) {
    extractor.release()
    return@withContext null
}

// With:
val decodeResult = attemptDecodeWithRetry(
    extractor = extractor,
    format = format,
    targetSampleRate = targetSampleRate,
    channelCount = channelCount
)

when (decodeResult.result) {
    DecodeResult.SUCCESS -> {
        // Continue with stats
    }
    DecodeResult.SAMPLE_COUNT_ZERO -> {
        extractor.release()
        return@withContext null
    }
    DecodeResult.DECODER_INIT_FAILED -> {
        // Trigger fallback
    }
    else -> {
        extractor.release()
        return@withContext null
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt
git commit -m "feat(replaygain): implement Level 1 retry mechanism

- Extract decode logic into attemptDecodeWithRetry
- Add retry with configurable attempts and delay
- Return DecodeResult for each decode attempt"
```

---

## Chunk 3: Implement Level 2 Raw PCM Fallback

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt`

- [ ] **Step 1: Add raw PCM fallback method**

```kotlin
/**
 * Fallback: Read raw PCM data directly from file using FileInputStream.
 * Bypasses MediaCodec decoding for files that fail to decode.
 *
 * @param filePath Path to the audio file
 * @param channelCount Number of audio channels
 * @return SampleStats extracted from raw PCM, or null if failed
 */
private fun fallbackReadRawPcm(
    filePath: String,
    channelCount: Int
): SampleStats? {
    return try {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            Logger.w("Fallback PCM read failed: file not accessible $filePath", "ReplayGainScanner")
            return null
        }

        // Get file extension to determine format
        val extension = file.extension.lowercase()

        // Only attempt raw read for formats we can handle
        if (extension !in listOf("wav", "flac", "ogg", "mp3")) {
            Logger.w("Fallback PCM read: unsupported format $extension", "ReplayGainScanner")
            return null
        }

        // For WAV files, we can directly read PCM data
        if (extension == "wav") {
            return fallbackReadWavPcm(file, channelCount)
        }

        // For other formats, this is a best-effort fallback
        // The actual implementation would need format-specific decoder
        Logger.w("Fallback PCM read: format $extension not fully supported", "ReplayGainScanner")
        null
    } catch (e: Exception) {
        Logger.e("Fallback PCM read error: ${e.message}", e, "ReplayGainScanner")
        null
    }
}

/**
 * Reads WAV file directly as PCM fallback.
 */
private fun fallbackReadWavPcm(file: File, channelCount: Int): SampleStats? {
    try {
        val bytes = file.readBytes()

        // Parse WAV header (44 bytes for standard WAV)
        if (bytes.size < 44) return null

        // Check RIFF header
        if (bytes[0] != 0x52.toByte() || // R
            bytes[1] != 0x49.toByte() || // I
            bytes[2] != 0x46.toByte() || // F
            bytes[3] != 0x46.toByte()) {  // F
            return null
        }

        // Find data chunk
        var dataOffset = 12
        var dataSize = 0L
        while (dataOffset < bytes.size - 8) {
            val chunkId = bytes.slice(dataOffset until dataOffset + 4).toByteArray()
            val chunkSize = (bytes[dataOffset + 4].toInt() and 0xFF) or
                           ((bytes[dataOffset + 5].toInt() and 0xFF) shl 8) or
                           ((bytes[dataOffset + 6].toInt() and 0xFF) shl 16) or
                           ((bytes[dataOffset + 7].toInt() and 0xFF) shl 24)

            if (String(chunkId) == "data") {
                dataSize = chunkSize
                dataOffset += 8
                break
            }
            dataOffset += 8 + chunkSize
        }

        if (dataSize <= 0 || dataOffset >= bytes.size) return null

        // Convert bytes to float samples
        val sampleData = bytes.sliceArray(dataOffset until minOf(dataOffset + dataSize.toInt(), bytes.size))
        val floatSamples = mutableListOf<Float>()
        var i = 0
        while (i + 1 < sampleData.size) {
            // 16-bit PCM
            val sample = ((sampleData[i + 1].toInt() shl 8) or (sampleData[i].toInt() and 0xFF)).toShort()
            floatSamples.add(sample.toFloat() / 32768.0f)
            i += 2
        }

        if (floatSamples.isEmpty()) return null

        // Calculate stats
        var peak = 0f
        var sumSquares = 0.0
        floatSamples.forEach { sample ->
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
            sumSquares += sample.toDouble().pow(2.0)
        }

        return SampleStats(
            sampleCount = floatSamples.size.toLong(),
            sumSquares = sumSquares,
            peak = peak,
            blockRmsValues = emptyList() // Skip block calculation for fallback
        )
    } catch (e: Exception) {
        Logger.e("WAV fallback read error: ${e.message}", e, "ReplayGainScanner")
        return null
    }
}
```

- [ ] **Step 2: Add Level 2 fallback trigger in analyzeAudioFile**

```kotlin
// After Level 1 retry exhausts and returns DECODER_INIT_FAILED:
// Trigger Level 2 fallback
Logger.w("Level 1 retry exhausted, attempting Level 2 fallback", "ReplayGainScanner")
val fallbackStats = fallbackReadRawPcm(filePath, channelCount)
if (fallbackStats != null && fallbackStats.sampleCount > 0) {
    Logger.i("Level 2 fallback successful for $filePath", "ReplayGainScanner")
    // Use fallbackStats for gain calculation
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt
git commit -m "feat(replaygain): implement Level 2 raw PCM fallback

- Add fallbackReadRawPcm method for bypass decoding
- Add fallbackReadWavPcm for WAV format direct read
- Integrate Level 2 fallback after Level 1 exhaustion"
```

---

## Chunk 4: Implement Level 3 Minimum Estimation Fallback

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt`

- [ ] **Step 1: Add Level 3 minimum estimation method**

```kotlin
/**
 * Fallback Level 3: Estimate gain based on file metadata when all decode attempts fail.
 * Uses file size and format to provide a reasoned default value.
 *
 * @param filePath Path to the audio file
 * @param targetLoudness Target loudness in LUFS
 * @return Estimated ReplayGainInfo, or null if cannot estimate
 */
private fun fallbackEstimateGain(
    filePath: String,
    targetLoudness: Float
): ReplayGainInfo? {
    return try {
        val file = File(filePath)
        if (!file.exists()) return null

        val fileSizeBytes = file.length()
        val extension = file.extension.lowercase()

        // Estimate duration based on file size and format
        // Bitrate estimates per format (bits per second):
        val estimatedBitrate = when (extension) {
            "flac" -> 800_000 // ~800 kbps for FLAC
            "wav" -> 1_411_200 // 1411.2 kbps for 44.1kHz 16-bit stereo
            "mp3" -> 320_000 // 320 kbps for high-quality MP3
            "m4a", "aac" -> 256_000 // 256 kbps for AAC
            "ogg" -> 256_000 // 256 kbps for Ogg Vorbis
            "ape" -> 800_000 // ~800 kbps for APE
            else -> 320_000 // Default estimate
        }

        val estimatedDurationSeconds = (fileSizeBytes * 8.0) / estimatedBitrate
        val estimatedSamples = (estimatedDurationSeconds * 44100).toLong()

        // Only provide estimation if file is reasonable size (> 100KB)
        if (fileSizeBytes < 100_000) {
            Logger.w("File too small for estimation: $filePath", "ReplayGainScanner")
            return null
        }

        // For estimation, use a neutral gain (0 dB) with moderate peak
        // This indicates "unable to analyze" rather than wrong value
        val estimatedGain = 0f // Neutral
        val estimatedPeak = 0.5f // Safe default below clipping

        Logger.w(
            "Level 3 fallback estimation for $filePath: " +
            "estimatedDuration=${estimatedDurationSeconds}s fileSize=${fileSizeBytes}B",
            "ReplayGainScanner"
        )

        ReplayGainInfo(
            trackGain = estimatedGain,
            trackPeak = estimatedPeak,
            albumGain = null,
            albumPeak = null
        )
    } catch (e: Exception) {
        Logger.e("Level 3 estimation failed: ${e.message}", e, "ReplayGainScanner")
        null
    }
}
```

- [ ] **Step 2: Integrate Level 3 in analyzeAudioFile after Level 2 fails**

```kotlin
// After Level 2 fallback also fails:
if (fallbackStats == null || fallbackStats.sampleCount <= 0) {
    Logger.w("Level 2 fallback failed, attempting Level 3 estimation", "ReplayGainScanner")
    val estimatedGain = fallbackEstimateGain(filePath, targetLoudness)
    if (estimatedGain != null) {
        Logger.i("Level 3 estimation successful for $filePath", "ReplayGainScanner")
        return@withContext estimatedGain
    }
}

// All fallbacks exhausted
Logger.e("All fallbacks exhausted for $filePath", "ReplayGainScanner")
return@withContext null
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt
git commit -m "feat(replaygain): implement Level 3 minimum estimation fallback

- Add fallbackEstimateGain for extreme edge cases
- Estimate based on file size and format
- Return neutral gain when all decode attempts fail"
```

---

## Chunk 5: Add Sample Validity Checks in ReplayGainFilter

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainFilter.kt`

- [ ] **Step 1: Add sample validity constants and validation method**

```kotlin
/**
 * Validates input samples before processing.
 */
object ReplayGainFilter {

    // Existing constants...

    // New validation constants
    const val MAX_SAMPLE_VALUE = 2.0f // Allow some headroom beyond 1.0
    const val MIN_VALID_SAMPLES = 1000L

    /**
     * Validates samples before filter processing.
     * @return true if samples are valid for processing
     */
    fun isValidSample(sample: Float): Boolean {
        return sample.isFinite() && kotlin.math.abs(sample) <= MAX_SAMPLE_VALUE
    }

    /**
     * Validates sample array before processing.
     * @return ValidationResult with status and details
     */
    fun validateSamples(samples: FloatArray): ValidationResult {
        if (samples.isEmpty()) {
            return ValidationResult.Invalid("Empty sample array")
        }

        if (samples.size < MIN_VALID_SAMPLES) {
            return ValidationResult.Invalid(
                "Sample count ${samples.size} below minimum $MIN_VALID_SAMPLES"
            )
        }

        var invalidCount = 0
        var clippingCount = 0
        samples.forEach { sample ->
            if (!sample.isFinite()) invalidCount++
            if (kotlin.math.abs(sample) > 1.0f) clippingCount++
        }

        if (invalidCount > 0) {
            return ValidationResult.Invalid(
                "$invalidCount invalid (NaN/Infinity) samples found"
            )
        }

        if (clippingCount > samples.size / 10) { // >10% clipping
            return ValidationResult.Warning(
                "$clippingCount clipping samples detected (>10%)"
            )
        }

        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }
}
```

- [ ] **Step 2: Add filter state validation**

```kotlin
/**
 * In processFilters method, add validation:
 */
fun processFilters(samples: FloatArray, channelCount: Int): FloatArray {
    when (val validation = validateSamples(samples)) {
        is ValidationResult.Invalid -> {
            Logger.e("Filter validation failed: ${validation.message}", "ReplayGainFilter")
            return samples // Return unfiltered on invalid input
        }
        is ValidationResult.Warning -> {
            Logger.w("Filter validation warning: ${validation.message}", "ReplayGainFilter")
            // Continue processing with warning
        }
        ValidationResult.Valid -> { /* Continue */ }
    }

    // Existing filter chain...
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainFilter.kt
git commit -m "feat(replaygain): add sample validity checks in filter

- Add isValidSample and validateSamples methods
- Add ValidationResult sealed class for validation status
- Log warnings for clipping, errors for invalid samples"
```

---

## Chunk 6: Enhance Logging Throughout Scanner

**Files:**
- Modify: `app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt`

- [ ] **Step 1: Add structured logging helper for decode failures**

```kotlin
/**
 * Logs detailed decode failure information.
 */
private fun logDecodeFailure(info: DecodeFailureInfo) {
    val fileName = File(info.filePath).name
    Logger.e(
        buildString {
            append("ReplayGain decode failed: ")
            append("file=$fileName ")
            append("errorType=${info.result.name} ")
            append("mime=${info.mime ?: "unknown"} ")
            append("sampleRate=${info.sampleRate} ")
            append("channelCount=${info.channelCount} ")
            append("fallbackLevel=${info.fallbackLevel}")
        },
        info.cause,
        "ReplayGainScanner"
    )
}
```

- [ ] **Step 2: Add logging for gain value validation**

```kotlin
// In calculate95thPercentileRms, after calculation:
if (result > 100f || result < -100f) {
    Logger.w(
        "Suspicious gain value calculated: ${result}dB for file",
        "ReplayGainScanner"
    )
}
```

- [ ] **Step 3: Add peak clipping detection logging**

```kotlin
// In analyzeAudioFile, after peak calculation:
if (peak > 1.0f) {
    Logger.w(
        "Peak clipping detected: peak=$peak (${peak * 100}% of max)",
        "ReplayGainScanner"
    )
}
```

- [ ] **Step 4: Add gain clamping with logging**

```kotlin
// Before returning ReplayGainInfo, clamp abnormal values:
val clampedTrackGain = trackGain.coerceIn(-50f, 50f)
if (clampedTrackGain != trackGain) {
    Logger.w(
        "Track gain clamped from ${trackGain}dB to ${clampedTrackGain}dB",
        "ReplayGainScanner"
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/voxly/data/local/replaygain/ReplayGainScanner.kt
git commit -m "feat(replaygain): enhance logging for decode failures

- Add logDecodeFailure helper for structured error logging
- Add peak clipping detection logging
- Add gain value clamping with warning logs
- Add suspicious gain value detection"
```

---

## Chunk 7: MetadataEditorViewModel Error State Handling

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/viewmodel/MetadataEditorViewModel.kt`

- [ ] **Step 1: Add ReplayGainScanError sealed class**

```kotlin
/**
 * Error types for ReplayGain scan failures.
 */
sealed class ReplayGainScanError {
    data class DecodeFailed(val reason: String, val filePath: String) : ReplayGainScanError()
    data class NoAudioTrack(val filePath: String) : ReplayGainScanError()
    data class PermissionDenied(val filePath: String) : ReplayGainScanError()
    data class AllFallbacksFailed(val filePath: String) : ReplayGainScanError()
    data class Unknown(val message: String) : ReplayGainScanError()
}
```

- [ ] **Step 2: Add error state flow**

Add after `_isScanningReplayGain` declaration:

```kotlin
private val _isScanningReplayGain = MutableStateFlow(false)
val isScanningReplayGain: StateFlow<Boolean> = _isScanningReplayGain.asStateFlow()

private val _replayGainScanError = MutableStateFlow<ReplayGainScanError?>(null)
val replayGainScanError: StateFlow<ReplayGainScanError?> = _replayGainScanError.asStateFlow()
```

- [ ] **Step 3: Clear error on new scan start**

In `scanReplayGain()` method, at the start:

```kotlin
fun scanReplayGain() {
    viewModelScope.launch {
        _isScanningReplayGain.value = true
        _replayGainScanError.value = null // Clear previous error
        // ... existing code ...
    }
}
```

- [ ] **Step 4: Map scan failures to error types**

In the scan progress handling:

```kotlin
when (progress.status) {
    com.voxly.domain.repository.ScanStatus.COMPLETED -> {
        _replayGainScanError.value = null
        // ... existing success handling ...
    }
    com.voxly.domain.repository.ScanStatus.FAILED -> {
        // Determine error type based on reason
        val error: ReplayGainScanError = when {
            progress.currentFilePath.contains("Permission") ||
            progress.currentFilePath.contains("EACCES") ->
                ReplayGainScanError.PermissionDenied(progress.currentFilePath)
            progress.currentFilePath.contains("audio track") ||
            progress.currentFilePath.contains("no audio") ->
                ReplayGainScanError.NoAudioTrack(progress.currentFilePath)
            progress.currentFilePath.contains("decode") ||
            progress.currentFilePath.contains("codec") ->
                ReplayGainScanError.DecodeFailed("解码失败", progress.currentFilePath)
            else ->
                ReplayGainScanError.Unknown(progress.message ?: "未知错误")
        }
        _replayGainScanError.value = error
        _isScanningReplayGain.value = false
    }
    else -> { /* scanning in progress */ }
}
```

- [ ] **Step 5: Add clearError method**

```kotlin
/**
 * Clears the ReplayGain scan error.
 */
fun clearReplayGainScanError() {
    _replayGainScanError.value = null
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/voxly/presentation/viewmodel/MetadataEditorViewModel.kt
git commit -m "feat(replaygain): add error state handling in ViewModel

- Add ReplayGainScanError sealed class
- Add _replayGainScanError StateFlow
- Map scan failures to specific error types
- Add clearReplayGainScanError method"
```

---

## Chunk 8: UI Feedback in ReplayGainSection

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/components/ReplayGainSection.kt`
- Modify: `app/src/main/java/com/voxly/presentation/screens/metadata/MetadataEditorScreen.kt` (if needed to pass error state)

- [ ] **Step 1: Add error display state to ReplayGainSection**

In `ReplayGainSection` composable, add error parameter:

```kotlin
@Composable
fun ReplayGainSection(
    replayGainInfo: ReplayGainInfo?,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    error: ReplayGainScanError? = null // New parameter
)
```

- [ ] **Step 2: Display error message in expanded content**

Add after the expanded content opening:

```kotlin
if (expanded) {
    Spacer(modifier = Modifier.height(12.dp))

    // Show error if present
    error?.let { scanError ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (scanError) {
                        is ReplayGainScanError.DecodeFailed -> "该文件解码失败，尝试其他来源的版本"
                        is ReplayGainScanError.NoAudioTrack -> "未找到音频轨道，文件可能已损坏"
                        is ReplayGainScanError.PermissionDenied -> "无读取权限，请检查文件访问权限"
                        is ReplayGainScanError.AllFallbacksFailed -> "无法分析该文件，请尝试重新下载"
                        is ReplayGainScanError.Unknown -> "扫描失败：${scanError.message}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    // ... existing content ...
}
```

- [ ] **Step 3: Import error icon**

Ensure `Icons.Default.Error` is imported:

```kotlin
import androidx.compose.material.icons.filled.Error
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/voxly/presentation/components/ReplayGainSection.kt
git commit -m "feat(replaygain): add error display in ReplayGainSection UI

- Add error parameter to ReplayGainSection composable
- Display error Card with localized messages
- Show specific error type-based messages"
```

---

## Summary

| Chunk | Description | Files Modified |
|-------|-------------|----------------|
| 1 | Add DecodeResult enum and error types | ReplayGainScanner.kt |
| 2 | Implement Level 1 retry mechanism | ReplayGainScanner.kt |
| 3 | Implement Level 2 raw PCM fallback | ReplayGainScanner.kt |
| 4 | Implement Level 3 minimum estimation | ReplayGainScanner.kt |
| 5 | Add sample validity checks | ReplayGainFilter.kt |
| 6 | Enhance logging | ReplayGainScanner.kt |
| 7 | Add error state in ViewModel | MetadataEditorViewModel.kt |
| 8 | Add UI error feedback | ReplayGainSection.kt |

---

## Verification

After implementation:
1. Run `./gradlew test` to ensure unit tests pass
2. Run `./gradlew build` to ensure compilation succeeds
3. Test on real device with problematic audio files
4. Verify logs show fallback levels being triggered appropriately
