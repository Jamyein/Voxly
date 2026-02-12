package com.voxly.data.remote.tengx.model

/**
 * TengX Music lyrics response model.
 * Response structure from TengX Music lyrics API.
 * Lyrics content is Base64 encoded and needs to be decoded.
 */
data class TengxLyricsResponse(
    /** Request result code */
    val code: Int,
    /** Lyrics data container */
    val lyric: TengxLyricContainer? = null,
    /** Translated lyrics data container */
    val trans: TengxLyricContainer? = null,
    /** Message from server */
    val message: String? = null
)

/**
 * Container for TengX Music lyrics.
 * Lyric content is Base64 encoded and needs to be decoded.
 */
data class TengxLyricContainer(
    /** Lyrics content - Base64 encoded, needs decode */
    val lyric: String = "",
    /** Lyrics version */
    val version: Int = 0
)

/**
 * Represents a single lyric line with timestamp.
 */
data class TengxLyricLine(
    /** Timestamp in milliseconds */
    val timestamp: Long,
    /** Lyric text */
    val text: String,
    /** Translated text (if available) */
    val translatedText: String? = null
)
