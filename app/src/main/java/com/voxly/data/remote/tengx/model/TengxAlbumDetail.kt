package com.voxly.data.remote.tengx.model

/**
 * TengX Music album detail response model.
 * Response structure from TengX Music album detail API.
 */
data class TengxAlbumDetail(
    /** Request result code */
    val code: Int,
    /** Album detail data */
    val data: TengxAlbumDetailData? = null,
    /** Message from server */
    val message: String? = null
)

/**
 * TengX Music album detail container.
 */
data class TengxAlbumDetailData(
    /** Album information */
    val album: TengxAlbumDetailInfo? = null,
    /** List of songs in the album */
    val list: List<TengxAlbumSong> = emptyList()
)

/**
 * TengX Music detailed album information.
 */
data class TengxAlbumDetailInfo(
    /** Album ID */
    val id: Long,
    /** Album name */
    val name: String,
    /** Album title */
    val title: String = "",
    /** Main singer information */
    val singer: TengxSinger? = null,
    /** Publishing company */
    val company: String = "",
    /** Album publish date */
    val publicTime: String = "",
    /** Album genre */
    val genre: String = "",
    /** Album type */
    val type: String = "",
    /** Album cover image URL */
    val pic: String = "",
    /** Album description */
    val desc: String = ""
)

/**
 * TengX Music song in album.
 */
data class TengxAlbumSong(
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
    /** Album mid for artwork */
    val albumMid: String = "",
    /** Duration in milliseconds */
    val interval: Int = 0,
    /** Track number */
    val trackNo: Int = 0,
    /** Volume number */
    val volume: Int = 0
)
