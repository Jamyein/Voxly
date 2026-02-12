package com.voxly.data.remote.tengx.model

/**
 * TengX Music song detail response model.
 * Response structure from TengX Music song detail API.
 */
data class TengxSongDetail(
    /** Request result code */
    val code: Int,
    /** Song detail data */
    val data: TengxSongDetailData? = null,
    /** Message from server */
    val message: String? = null
)

/**
 * TengX Music song detail container.
 */
data class TengxSongDetailData(
    /** List of song details */
    val track: List<TengxSongDetailItem> = emptyList(),
    /** Song playing info */
    val playing: TengxSongPlaying? = null
)

/**
 * TengX Music detailed song information.
 */
data class TengxSongDetailItem(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Song title */
    val title: String = "",
    /** Subtitle */
    val subtitle: String = "",
    /** Singer information */
    val singer: List<TengxSinger> = emptyList(),
    /** Album information */
    val album: TengxAlbum? = null,
    /** Duration in milliseconds */
    val interval: Int = 0,
    /** Music stream URL */
    val file: TengxSongFile? = null,
    /** Album mid for artwork */
    val albumMid: String = "",
    /** Track number */
    val trackNo: Int = 0
)

/**
 * TengX Music song file information.
 */
data class TengxSongFile(
    /** Media file ID */
    val mediaMid: String = "",
    /** Song file size */
    val size: Long = 0,
    /** Song bitrate */
    val bitrate: Int = 0,
    /** Song file extension */
    val ext: String = "",
    /** Song file URL */
    val url: String = ""
)

/**
 * TengX Music playing information.
 */
data class TengxSongPlaying(
    /** Current playing song ID */
    val id: Long = 0,
    /** Current playing progress */
    val progress: Int = 0
)
