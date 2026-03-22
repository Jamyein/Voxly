package com.voxly.data.local.replaygain

import com.voxly.core.util.Logger

/**
 * IIR Filter implementations for ReplayGain psychoacoustic compensation.
 *
 * Based on foobar2000 ReplayGain analysis algorithm:
 * - Yulewalk 10th order filter: approximates Fletcher-Munson equal-loudness curves
 * - Butterworth 2nd order highpass: removes DC offset and subsonic content below 150Hz
 *
 * These filters make the loudness measurement match human perception better.
 */
object ReplayGainFilter {

    // New validation constants
    const val MIN_VALID_SAMPLES = 1000L

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

        if (clippingCount > samples.size * 0.1) { // >10% clipping
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

    /**
     * Yulewalk 10th order filter coefficients for psychoacoustic compensation.
     * These coefficients approximate the ISO 226 equal-loudness curves (Fletcher-Munson).
     *
     * Format: [a0, a1, a2, ..., a10, b0, b1, b2, ..., b10]
     * Where a[] are feedback (denominator) and b[] are feedforward (numerator) coefficients.
     */
    val yulewalkCoefficients: DoubleArray = doubleArrayOf(
        // Denominator coefficients (a) - feedback terms
        1.0, -4.1586711839388704, 8.5059062509991185, -10.732774262442024,
        8.747295215757407, -4.627481255322061, 1.6009145175088847,
        -0.53339042813442234, 0.17822260087985804, -0.050068739721612736,
        0.010471998997433137,
        // Numerator coefficients (b) - feedforward terms
        0.002884937999946808, 0.015419499826159935, 0.065903618826159618,
        0.11971681409515991, 0.13451743232365993, 0.11971681409515991,
        0.065903618826159618, 0.015419499826159935, 0.002884937999946808,
        // b9 = 0.0 (implicit), b10 = 0.0 (for 10th-order filter indexing)
        0.0, 0.0
    )

    /**
     * Butterworth 2nd order highpass filter coefficients at 150Hz.
     * Removes DC offset and subsonic frequencies below the threshold.
     *
     * Coefficients computed for 48kHz sample rate.
     */
    val butterworthHighpassCoefficients: DoubleArray = doubleArrayOf(
        // Denominator coefficients (a)
        1.0, -1.9122309442467402, 0.9150378313221641,
        // Numerator coefficients (b)
        0.9569400371160840, -1.913880074232168, 0.9569400371160840
    )

    /**
     * Processes audio samples through the Yulewalk filter.
     * Uses Direct Form II transposed structure for IIR filtering.
     * Each channel is processed independently (matches foobar2000 behavior).
     *
     * @param samples Input samples (mono or interleaved stereo)
     * @param channelCount Number of channels (for stereo, process each channel separately)
     * @return Filtered samples
     */
    fun processYulewalk(samples: FloatArray, channelCount: Int): FloatArray {
        val output = FloatArray(samples.size)
        val coeffs = yulewalkCoefficients

        // 11th order filter: 11 numerator (b) and 11 denominator (a) coefficients
        val order = 10
        val numB = order + 1  // 11
        val numA = order + 1  // 11

        // State variables for each channel (maintain independence between channels)
        val state = DoubleArray((order + 1) * channelCount)

        // Process each sample
        for (i in samples.indices) {
            val channel = i % channelCount
            val stateOffset = channel * (order + 1)

            val input = samples[i].toDouble()

            // Accumulate feedforward part
            var outputSample = coeffs[numA] * input + state[stateOffset]

            // Shift state for this channel
            for (j in 0 until order) {
                state[stateOffset + j] = coeffs[numA + j + 1] * input - coeffs[j + 1] * outputSample + state[stateOffset + j + 1]
            }

            output[i] = outputSample.toFloat()
        }

        return output
    }

    /**
     * Processes audio samples through the Butterworth highpass filter.
     * Each channel is processed independently (matches foobar2000 behavior).
     *
     * @param samples Input samples (mono or interleaved stereo)
     * @param channelCount Number of channels
     * @return Filtered samples
     */
    fun processButterworthHighpass(samples: FloatArray, channelCount: Int): FloatArray {
        val output = FloatArray(samples.size)
        val coeffs = butterworthHighpassCoefficients

        // 2nd order filter: 3 b coefficients and 3 a coefficients
        val order = 2

        // State for each channel: (order + 1) states per channel for Direct Form II Transposed
        val state = DoubleArray((order + 1) * channelCount)

        for (i in samples.indices) {
            val channel = i % channelCount
            val stateOffset = channel * (order + 1)

            val input = samples[i].toDouble()

            // Direct Form II Transposed implementation
            // coeffs: [a0, a1, a2, b0, b1, b2] where a0=1
            var outputSample = coeffs[3] * input + state[stateOffset]

            // Update state for this channel
            state[stateOffset] = coeffs[4] * input - coeffs[1] * outputSample + state[stateOffset + 1]
            state[stateOffset + 1] = coeffs[5] * input - coeffs[2] * outputSample

            output[i] = outputSample.toFloat()
        }

        return output
    }

    /**
     * Combined filter processing: Yulewalk + Butterworth highpass.
     * This matches the foobar2000 ReplayGain analysis chain.
     *
     * @param samples Input audio samples
     * @param channelCount Number of channels
     * @return Psychoacoustically compensated samples
     */
    fun processFilters(samples: FloatArray, channelCount: Int): FloatArray {
        when (val validation = validateSamples(samples)) {
            is ValidationResult.Invalid -> {
                Logger.e("Filter validation failed: ${validation.message}", null, "ReplayGainFilter")
                return samples // Return unfiltered on invalid input
            }
            is ValidationResult.Warning -> {
                Logger.w("Filter validation warning: ${validation.message}", "ReplayGainFilter")
                // Continue processing with warning
            }
            ValidationResult.Valid -> { /* Continue */ }
        }

        // First apply Yulewalk (psychoacoustic compensation)
        var filtered = processYulewalk(samples, channelCount)

        // Then apply Butterworth highpass (remove subsonic)
        filtered = processButterworthHighpass(filtered, channelCount)

        return filtered
    }

    /**
     * Resets filter state between files to prevent cross-contamination.
     * Note: Current implementation uses stateless processing per file.
     */
    fun reset() {
        // No persistent state to reset in current implementation
        // State is maintained per-block during processing
    }
}
