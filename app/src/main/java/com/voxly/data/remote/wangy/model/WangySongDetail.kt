package com.voxly.data.remote.wangy.model

/**
 * WangY Music song detail response model.
 * Response structure from WangY Cloud Music song detail API.
 */
data class WangySongDetail(
    /** Request result code: 200 indicates success */
    val code: Int,
    /** List of song details */
    val songs: List<WangySongDetailItem> = emptyList(),
    /** Privileges information */
    val privileges: List<WangyPrivilege> = emptyList()
)

/**
 * WangY Music detailed song information.
 */
data class WangySongDetailItem(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Artist information */
    val ar: List<WangyArtistInfo> = emptyList(),
    /** Album information */
    val al: WangyAlbumInfo? = null,
    /** Duration in milliseconds */
    val dt: Long = 0,
    /** Publish timestamp */
    val publishTime: Long = 0,
    /** Song version */
    val version: Int = 0
)

/**
 * WangY Music artist information for song detail.
 */
data class WangyArtistInfo(
    /** Artist ID */
    val id: Long,
    /** Artist name */
    val name: String
)

/**
 * WangY Music album information for song detail.
 */
data class WangyAlbumInfo(
    /** Album ID */
    val id: Long,
    /** Album name */
    val name: String,
    /** Album cover image URL */
    val picUrl: String = ""
)

/**
 * WangY Music privilege information.
 */
data class WangyPrivilege(
    /** Song ID */
    val id: Long,
    /** Play status */
    val play: Boolean = true,
    /** Download status */
    val download: Boolean = true,
    /** Play status for subcode */
    val playMaxbr: Int = 0,
    /** Download status for subcode */
    val downloadMaxbr: Int = 0,
    /** Fee type */
    val fee: Int = 0,
    /** Experience level */
    val expLevel: Int = 0
)
