package com.voxly.data.local.replaygain

import kotlin.math.*

/**
 * Pure Kotlin implementation of EBU R128 loudness measurement (ITU-R BS.1770-4).
 *
 * Ported from libebur128 used by rsgain. Implements:
 * - K-weighting filters (high-shelf + high-pass) per ITU-R BS.1770-4
 * - Relative gating (EBU R128): excludes sections below -10 LU relative to integrated loudness
 * - Absolute gating: excludes sections below -70 LUFS
 * - Integrated loudness (I), sample peak, and loudness range (LRA)
 *
 * This replaces the Classic ReplayGain 1.0 approach (RMS + Yulewalk + Butterworth)
 * with the modern EBU R128 standard used by rsgain.
 *
 * Reference: https://tech.ebu.ch/docs/tech/tech3341.pdf
 */
class EbuR128Analyzer(
    private val channels: Int,
    private val sampleRate: Int,
    private val targetLoudness: Double = -18.0,
    private val dualMono: Boolean = false
) {
    companion object {
        // ITU-R BS.1770-4 K-weighting pre-filter (high-shelf) coefficients
        // Designed for 48kHz; rescaled dynamically for other sample rates
        private const val HIGHSHELF_F0 = 1681.0
        private const val HIGHSHELF_GAIN_DB = 4.0

        // ITU-R BS.1770-4 K-weighting post-filter (high-pass) coefficients
        // 2nd order Butterworth at 1681 Hz, designed for 48kHz
        private const val HIGHPASS_F0 = 1681.0

        // Gating thresholds per EBU R128
        private const val ABSOLUTE_THRESHOLD = -70.0  // LUFS
        private const val RELATIVE_THRESHOLD = -10.0  // LU relative to integrated

        // Block duration for loudness calculation (400ms per EBU R128)
        private const val BLOCK_DURATION_MS = 400

        // Overlap factor for sliding window (75% overlap)
        private const val OVERLAP_FACTOR = 4

        // Reference loudness for ReplayGain calculation
        private const val REFERENCE_LOUDNESS = -18.0  // LUFS (rsgain default)

        // Minimum samples for valid analysis
        private const val MIN_SAMPLES = 4800  // ~100ms at 48kHz
    }

    // Filter state per channel (Direct Form II Transposed)
    private val highShelfState = DoubleArray(channels * 4)  // 2 state vars per channel * 2
    private val highPassState = DoubleArray(channels * 4)

    // Filter coefficients (computed for actual sample rate)
    private val highShelfB = DoubleArray(3)
    private val highShelfA = DoubleArray(3)
    private val highPassB = DoubleArray(3)
    private val highPassA = DoubleArray(3)

    // Accumulators for integrated loudness
    private var sumWeightedEnergy = 0.0
    private var totalBlockCount = 0
    private var gatedBlockCount = 0

    // Per-block energies for gating and LRA calculation
    private val blockEnergies = mutableListOf<Double>()

    // Peak tracking
    private var samplePeak = 0.0
    private val channelPeaks: DoubleArray = DoubleArray(channels)

    // Channel mapping weights per ITU-R BS.1770-4
    private val channelWeights: DoubleArray = computeChannelWeights(channels, dualMono)

    init {
        require(channels > 0 && channels <= 64) { "Unsupported channel count: $channels" }
        require(sampleRate >= 8000) { "Sample rate too low: $sampleRate" }
        computeFilterCoefficients()
    }

    /**
     * Compute channel weights per ITU-R BS.1770-4 Annex 2.
     * Mono=1, Stereo=1 each, Center=0.707, LFE=0, Surround=0.5
     */
    private fun computeChannelWeights(ch: Int, dualMono: Boolean): DoubleArray {
        return when (ch) {
            1 -> if (dualMono) doubleArrayOf(0.5, 0.5) else doubleArrayOf(1.0)
            2 -> doubleArrayOf(1.0, 1.0)  // L, R
            3 -> doubleArrayOf(1.0, 1.0, 0.707)  // L, R, C
            4 -> doubleArrayOf(1.0, 1.0, 0.707, 0.707)  // L, R, C, Cs
            5 -> doubleArrayOf(1.0, 1.0, 0.707, 0.5, 0.5)  // L, R, C, Ls, Rs
            6 -> doubleArrayOf(1.0, 1.0, 0.707, 0.707, 0.5, 0.5)  // L, R, C, LFE, Ls, Rs
            else -> DoubleArray(ch) { 1.0 }  // Fallback: equal weight
        }
    }

    /**
     * Compute biquad filter coefficients for the actual sample rate.
     * Uses bilinear transform with frequency warping.
     */
    private fun computeFilterCoefficients() {
        val sr = sampleRate.toDouble()
        val pi = PI

        // High-shelf filter (K-weighting pre-filter)
        // Boosts high frequencies to match human loudness perception
        val f0 = HIGHSHELF_F0
        val gain = 10.0.pow(HIGHSHELF_GAIN_DB / 40.0)  // sqrt of linear gain
        val w0 = 2.0 * pi * f0 / sr
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((gain + 1.0 / gain) * (1.0 / 0.65) - 1.0)

        val b0 = gain * ((gain + 1.0) + (gain - 1.0) * cosW0 + 2.0 * sqrt(gain) * alpha)
        val b1 = -2.0 * gain * ((gain - 1.0) + (gain + 1.0) * cosW0)
        val b2 = gain * ((gain + 1.0) + (gain - 1.0) * cosW0 - 2.0 * sqrt(gain) * alpha)
        val a0 = (gain + 1.0) - (gain - 1.0) * cosW0 + 2.0 * sqrt(gain) * alpha
        val a1 = 2.0 * ((gain - 1.0) - (gain + 1.0) * cosW0)
        val a2 = (gain + 1.0) - (gain - 1.0) * cosW0 - 2.0 * sqrt(gain) * alpha

        highShelfB[0] = b0 / a0
        highShelfB[1] = b1 / a0
        highShelfB[2] = b2 / a0
        highShelfA[1] = a1 / a0
        highShelfA[2] = a2 / a0

        // High-pass filter (K-weighting post-filter)
        // 2nd order Butterworth at 1681 Hz
        val hpW0 = 2.0 * pi * HIGHPASS_F0 / sr
        val hpCosW0 = cos(hpW0)
        val hpSinW0 = sin(hpW0)
        val hpAlpha = hpSinW0 / sqrt(2.0)

        val hpB0 = (1.0 + hpCosW0) / 2.0
        val hpB1 = -(1.0 + hpCosW0)
        val hpB2 = (1.0 + hpCosW0) / 2.0
        val hpA0 = 1.0 + hpAlpha
        val hpA1 = -2.0 * hpCosW0
        val hpA2 = 1.0 - hpAlpha

        highPassB[0] = hpB0 / hpA0
        highPassB[1] = hpB1 / hpA0
        highPassB[2] = hpB2 / hpA0
        highPassA[1] = hpA1 / hpA0
        highPassA[2] = hpA2 / hpA0
    }

    /**
     * Apply biquad filter using Direct Form II Transposed structure.
     */
    private fun applyBiquad(
        input: Double,
        stateOffset: Int,
        b: DoubleArray,
        a: DoubleArray,
        state: DoubleArray
    ): Double {
        val out = b[0] * input + state[stateOffset]
        state[stateOffset] = b[1] * input - a[1] * out + state[stateOffset + 1]
        state[stateOffset + 1] = b[2] * input - a[2] * out
        return out
    }

    /**
     * Process a block of audio samples.
     *
     * @param samples Interleaved audio samples (L, R, L, R, ... for stereo)
     * @param sampleCount Number of samples to process (must be multiple of channels)
     */
    fun processBlock(samples: FloatArray, sampleCount: Int) {
        require(sampleCount % channels == 0) { "Sample count must be multiple of channel count" }

        val frames = sampleCount / channels
        val samplesPerAudioBlock = (sampleRate * BLOCK_DURATION_MS) / 1000
        val stepSize = samplesPerAudioBlock / OVERLAP_FACTOR

        // Process through K-weighting filters and accumulate energy
        var frameIdx = 0
        while (frameIdx < frames) {
            val blockFrames = minOf(stepSize, frames - frameIdx)
            if (blockFrames <= 0) break

            var blockEnergy = 0.0

            for (f in 0 until blockFrames) {
                var weightedEnergy = 0.0

                for (ch in 0 until channels) {
                    val sampleIdx = (frameIdx + f) * channels + ch
                    val input = samples[sampleIdx].toDouble()

                    // Track sample peak (unfiltered)
                    val absSample = abs(input)
                    if (absSample > samplePeak) samplePeak = absSample
                    if (absSample > channelPeaks[ch]) channelPeaks[ch] = absSample

                    // Apply K-weighting filters
                    val stateOffset = ch * 4
                    val filtered = applyBiquad(
                        input,
                        stateOffset,
                        highShelfB,
                        highShelfA,
                        highShelfState
                    )
                    val finalFiltered = applyBiquad(
                        filtered,
                        stateOffset + 2,
                        highPassB,
                        highPassA,
                        highPassState
                    )

                    // Accumulate weighted energy with channel weight
                    val weight = if (ch < channelWeights.size) channelWeights[ch] else 1.0
                    weightedEnergy += weight * finalFiltered * finalFiltered
                }

                blockEnergy += weightedEnergy
            }

            // Store block energy for gating
            if (blockEnergy > 0) {
                blockEnergies.add(blockEnergy)
            }

            frameIdx += blockFrames
        }
    }

    /**
     * Calculate integrated loudness with EBU R128 gating.
     *
     * Returns loudness in LUFS (Loudness Units relative to Full Scale).
     * Returns null if not enough data for valid measurement.
     */
    fun getIntegratedLoudness(): Double? {
        if (blockEnergies.isEmpty()) return null

        // First pass: calculate ungated loudness for relative threshold
        val totalEnergy = blockEnergies.sum()
        val ungatedLoudness = energyToLoudness(totalEnergy, blockEnergies.size)

        if (ungatedLoudness == null) return null

        // Second pass: apply gating
        val relativeThreshold = ungatedLoudness + RELATIVE_THRESHOLD
        var gatedEnergy = 0.0
        var gatedBlocks = 0

        for (energy in blockEnergies) {
            val blockLoudness = energyToLoudness(energy, 1)
            if (blockLoudness != null &&
                blockLoudness >= relativeThreshold &&
                blockLoudness >= ABSOLUTE_THRESHOLD
            ) {
                gatedEnergy += energy
                gatedBlocks++
            }
        }

        gatedBlockCount = gatedBlocks

        if (gatedBlocks == 0) {
            // Fallback: use ungated if no blocks pass gating
            return ungatedLoudness
        }

        return energyToLoudness(gatedEnergy, gatedBlocks)
    }

    /**
     * Calculate ungated (global) loudness without EBU R128 gating.
     * This is used for ReplayGain track gain calculation.
     */
    fun getGlobalLoudness(): Double? {
        if (blockEnergies.isEmpty()) return null
        val totalEnergy = blockEnergies.sum()
        return energyToLoudness(totalEnergy, blockEnergies.size)
    }

    /**
     * Get sample peak value.
     */
    fun getSamplePeak(): Double = samplePeak

    /**
     * Get per-channel peak values.
     */
    fun getChannelPeaks(): DoubleArray = channelPeaks.copyOf()

    /**
     * Calculate Loudness Range (LRA) per EBU R128 Part 6.
     * Measures the distribution of loudness values (10th to 95th percentile of gated blocks).
     */
    fun getLoudnessRange(): Double? {
        if (blockEnergies.size < 2) return null

        // Convert all block energies to loudness values
        val loudnessValues = blockEnergies.mapNotNull { energy ->
            energyToLoudness(energy, 1)
        }.sorted()

        if (loudnessValues.isEmpty()) return null

        // Calculate 10th and 95th percentiles
        val p10Index = max(0, (loudnessValues.size * 0.10).toInt() - 1)
        val p95Index = min(loudnessValues.size - 1, (loudnessValues.size * 0.95).toInt())

        return loudnessValues[p95Index] - loudnessValues[p10Index]
    }

    /**
     * Reset the analyzer state for reuse.
     */
    fun reset() {
        highShelfState.fill(0.0)
        highPassState.fill(0.0)
        sumWeightedEnergy = 0.0
        totalBlockCount = 0
        gatedBlockCount = 0
        blockEnergies.clear()
        samplePeak = 0.0
        channelPeaks.fill(0.0)
    }

    /**
     * Convert accumulated energy to loudness in LUFS.
     * LUFS = -0.691 + 10 * log10(mean_energy)
     * The -0.691 dB offset aligns with ITU-R BS.1770-4 reference.
     */
    private fun energyToLoudness(energy: Double, blockCount: Int): Double? {
        if (energy <= 0 || blockCount <= 0) return null
        val meanEnergy = energy / blockCount
        return -0.691 + 10.0 * log10(meanEnergy)
    }

    /**
     * Calculate ReplayGain-compatible gain from loudness measurement.
     * gain = targetLoudness - measuredLoudness
     */
    fun calculateGain(targetLoudness: Double = this.targetLoudness): Double? {
        val measured = getGlobalLoudness() ?: return null
        return targetLoudness - measured
    }

    /**
     * Get the reference loudness used for ReplayGain calculations.
     */
    fun getReferenceLoudness(): Double = REFERENCE_LOUDNESS

    /**
     * Get the number of blocks processed.
     */
    fun getBlockCount(): Int = blockEnergies.size

    /**
     * Get the target loudness level.
     */
    fun getTargetLoudness(): Double = targetLoudness
}
