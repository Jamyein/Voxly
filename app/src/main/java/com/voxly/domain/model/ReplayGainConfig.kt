package com.voxly.domain.model

/**
 * ReplayGain scan configuration inspired by rsgain.
 * 
 * This configuration follows rsgain's approach with the following features:
 * - Clip mode: protection against clipping
 * - True peak: 4x oversampling for inter-sample peak detection
 * - Dual mono: treat mono files as dual-mono
 * - Album AES77: use loudest track as album loudness (AES77-2011 recommendation)
 * - Skip existing: skip files with existing ReplayGain tags
 * - Max peak level: maximum peak level for clipping protection
 * 
 * @param clipMode Clipping protection mode ('n' = none, 'p' = positive gain only, 'a' = always)
 * @param truePeak Enable true peak measurement (4x oversampling)
 * @param dualMono Treat mono files as dual-mono
 * @param albumAsAes77 Use AES77 method for album loudness (loudest track)
 * @param skipExisting Skip files with existing ReplayGain tags
 * @param maxPeakLevel Maximum peak level in dB for clipping protection (default 0.0)
 */
data class ReplayGainConfig(
    val clipMode: ClipMode = ClipMode.POSITIVE,
    val truePeak: Boolean = false,
    val dualMono: Boolean = false,
    val albumAsAes77: Boolean = false,
    val skipExisting: Boolean = false,
    val maxPeakLevel: Double = 0.0
) {
    companion object {
        /** Default configuration following rsgain recommended settings */
        val DEFAULT = ReplayGainConfig()
        
        /** Configuration optimized for streaming services */
        val STREAMING = ReplayGainConfig(
            clipMode = ClipMode.ALWAYS,
            truePeak = true,
            maxPeakLevel = -1.0
        )
        
        /** Configuration for high-quality archival */
        val ARCHIVAL = ReplayGainConfig(
            clipMode = ClipMode.ALWAYS,
            truePeak = true,
            dualMono = true
        )
    }
}
}

/**
 * Clipping protection mode.
 * Following rsgain's implementation:
 * - NONE: No clipping protection
 * - POSITIVE: Only apply clipping protection when gain is positive (default)
 * - ALWAYS: Always apply clipping protection
 */
enum class ClipMode(val code: Char, val displayName: String) {
    NONE('n', "None"),
    POSITIVE('p', "Positive Gain Only"),
    ALWAYS('a', "Always");
    
    companion object {
        fun fromCode(code: Char): ClipMode = values().find { it.code == code } ?: POSITIVE
        fun fromString(value: String): ClipMode = when (value.uppercase()) {
            "NONE" -> NONE
            "POSITIVE" -> POSITIVE
            "ALWAYS" -> ALWAYS
            else -> POSITIVE
        }
    }
}

/**
 * Scan mode constants for replay gain scanning.
 * Compatible with foobar2000 scan modes.
 */
object ScanModeConstants {
    const val TRACK_ONLY = "TRACK_ONLY"
    const val SINGLE_ALBUM = "SINGLE_ALBUM"
    const val ALBUMS = "ALBUMS"

    val VALID_MODES = setOf(TRACK_ONLY, SINGLE_ALBUM, ALBUMS)
}
