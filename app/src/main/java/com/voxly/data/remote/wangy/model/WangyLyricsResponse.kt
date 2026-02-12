package com.voxly.data.remote.wangy.model

/**
 * WangY Music lyrics response model.
 * Response structure from WangY Cloud Music lyrics API.
 */
data class WangyLyricsResponse(
    /** Request result code: 200 indicates success */
    val code: Int,
    /** Lyrics data */
    val lrc: WangyLrcContainer? = null,
    /** Translated lyrics data */
    val tlyric: WangyLrcContainer? = null,
    /** Romaji lyrics data */
    val romalrc: WangyLrcContainer? = null,
    /** Additional lyric information */
    val yrc: WangyYrcContainer? = null
)

/**
 * Container for standard lyrics.
 */
data class WangyLrcContainer(
    /** Lyrics version */
    val version: Int = 0,
    /** Lyrics content */
    val lyric: String = ""
)

/**
 * Container for YRC (synced) lyrics.
 */
data class WangyYrcContainer(
    /** Lyrics version */
    val version: Int = 0,
    /** YRC format lyrics content */
    val lyric: String = ""
)

/**
 * Represents a single lyric line with timestamp.
 */
data class WangyLyricLine(
    /** Timestamp in milliseconds */
    val timestamp: Long,
    /** Lyric text */
    val text: String,
    /** Translated text (if available) */
    val translatedText: String? = null
)
