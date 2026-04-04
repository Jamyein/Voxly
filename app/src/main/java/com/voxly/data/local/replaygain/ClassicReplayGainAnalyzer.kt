package com.voxly.data.local.replaygain

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Classic ReplayGain 1.0/2.0 analyzer using the original foobar2000 algorithm.
 *
 * Algorithm:
 * 1. Apply Yulewalk 10th-order filter (psychoacoustic compensation)
 * 2. Apply Butterworth 2nd-order highpass at 150 Hz
 * 3. Calculate RMS of filtered signal
 * 4. gain = -14 dB - 10 * log10(rms²)
 *
 * Reference: https://wiki.hydrogenaud.io/index.php?title=ReplayGain_1.0_specification
 */
class ClassicReplayGainAnalyzer(
    private val channels: Int,
    private val sampleRate: Int,
    private val targetLoudness: Double = -14.0,
    private val dualMono: Boolean = false
) {
    companion object {
        const val REFERENCE_LOUDNESS = -14.0  // dB (ReplayGain standard)
        private const val BUTTERWORTH_CUTOFF = 150.0  // Hz
    }

    private var sumSquaredFiltered = 0.0
    private var sampleCount = 0
    private var samplePeak = 0.0
    private val channelPeaks = DoubleArray(channels)

    // Yulewalk filter state: 10th order = 10 delay elements per channel
    private val yulewalkOrder = 10
    private val yulewalkState = DoubleArray(channels * yulewalkOrder)

    // Butterworth highpass filter state: 2nd order = 2 delay elements per channel
    private val butterworthState = DoubleArray(channels * 2)

    // Butterworth coefficients (computed for actual sample rate)
    private val butterB = DoubleArray(3)
    private val butterA = DoubleArray(3)

    init {
        require(channels > 0 && channels <= 8) { "Unsupported channel count: $channels" }
        require(sampleRate >= 8000) { "Sample rate too low: $sampleRate" }
        computeButterworthCoefficients()
    }

    private fun computeButterworthCoefficients() {
        val sr = sampleRate.toDouble()
        val fo = BUTTERWORTH_CUTOFF
        val pi = kotlin.math.PI
        val w0 = 2.0 * pi * fo / sr
        val cosW0 = kotlin.math.cos(w0)
        val sinW0 = kotlin.math.sin(w0)
        val alpha = sinW0 / sqrt(2.0)

        val b0 = (1.0 + cosW0) / 2.0
        val b1 = -(1.0 + cosW0)
        val b2 = (1.0 + cosW0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha

        butterB[0] = b0 / a0
        butterB[1] = b1 / a0
        butterB[2] = b2 / a0
        butterA[1] = a1 / a0
        butterA[2] = a2 / a0
    }

    private fun applyYulewalk(input: Double, ch: Int): Double {
        val offset = ch * yulewalkOrder
        val coeffs = ReplayGainFilter.yulewalkCoefficients
        val numA = 11  // number of a coefficients

        // Direct Form II Transposed (matches ReplayGainFilter.processYulewalk)
        var output = coeffs[numA] * input + yulewalkState[offset]
        for (j in 0 until yulewalkOrder) {
            yulewalkState[offset + j] = coeffs[numA + j + 1] * input - coeffs[j + 1] * output + yulewalkState[offset + j + 1]
        }
        return output
    }

    private fun applyButterworth(input: Double, ch: Int): Double {
        val offset = ch * 2
        val out = butterB[0] * input + butterworthState[offset]
        butterworthState[offset] = butterB[1] * input - butterA[1] * out + butterworthState[offset + 1]
        butterworthState[offset + 1] = butterB[2] * input - butterA[2] * out
        return out
    }

    /**
     * Process a block of interleaved audio samples.
     * @param samples Interleaved samples (L, R, L, R, ...) normalized to [-1.0, 1.0]
     * @param sampleCount Total number of samples (must be multiple of channels)
     */
    fun processBlock(samples: FloatArray, sampleCount: Int) {
        require(sampleCount % channels == 0) { "Sample count must be multiple of channels" }

        for (i in 0 until sampleCount) {
            val ch = i % channels
            val input = samples[i].toDouble()

            // Track sample peak (unfiltered, original samples)
            val absSample = kotlin.math.abs(input)
            if (absSample > samplePeak) samplePeak = absSample
            if (absSample > channelPeaks[ch]) channelPeaks[ch] = absSample

            // Apply ReplayGain filter chain: Yulewalk -> Butterworth highpass
            val afterYulewalk = applyYulewalk(input, ch)
            val filtered = applyButterworth(afterYulewalk, ch)

            // Accumulate squared filtered samples
            sumSquaredFiltered += filtered * filtered
            this.sampleCount++
        }
    }

    /**
     * Get the measured loudness in dB relative to full scale.
     */
    fun getMeasuredLoudness(): Double? {
        if (sampleCount == 0) return null
        val meanSquared = sumSquaredFiltered / sampleCount
        if (meanSquared <= 0) return null
        return 10.0 * log10(meanSquared)
    }

    /**
     * Calculate ReplayGain gain value.
     * gain = referenceLoudness - measuredLoudness
     * where referenceLoudness = -14 dB
     */
    fun calculateGain(targetLoudness: Double = this.targetLoudness): Double? {
        val measured = getMeasuredLoudness() ?: return null
        return targetLoudness - measured
    }

    /**
     * Get sample peak (maximum absolute value of original samples).
     */
    fun getSamplePeak(): Double = samplePeak

    /**
     * Get per-channel peak values.
     */
    fun getChannelPeaks(): DoubleArray = channelPeaks.copyOf()

    /**
     * Get the number of samples processed.
     */
    fun getBlockCount(): Int = sampleCount

    /**
     * Reset analyzer state.
     */
    fun reset() {
        sumSquaredFiltered = 0.0
        sampleCount = 0
        samplePeak = 0.0
        channelPeaks.fill(0.0)
        yulewalkState.fill(0.0)
        butterworthState.fill(0.0)
    }
}
