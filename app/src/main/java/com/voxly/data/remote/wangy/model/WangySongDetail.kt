package com.voxly.data.remote.wangy.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WangY Music song detail response model.
 * Response structure from WangY Cloud Music song detail API.
 */
@Serializable
data class WangySongDetail(
    /** Request result code: 200 indicates success */
    @SerialName("code")
    val code: Int,
    /** List of song details */
    @SerialName("songs")
    val songs: List<WangySongDetailItem> = emptyList(),
    /** Privileges information */
    @SerialName("privileges")
    val privileges: List<WangyPrivilege> = emptyList()
)

/**
 * WangY Music detailed song information.
 */
@Serializable
data class WangySongDetailItem(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Artist information (also "ar") */
    @SerialName("artists")
    val artists: List<WangyArtistInfo> = emptyList(),
    @SerialName("ar")
    val ar: List<WangyArtistInfo> = emptyList(),
    /** Album information (also "al") */
    @SerialName("album")
    val album: WangyAlbumInfo? = null,
    @SerialName("al")
    val al: WangyAlbumInfo? = null,
    /** Duration in milliseconds */
    @SerialName("dt")
    val dt: Long = 0,
    /** Publish timestamp */
    @SerialName("publishTime")
    val publishTime: Long = 0,
    /** Song version */
    @SerialName("version")
    val version: Int = 0
)

/**
 * WangY Music artist information for song detail.
 */
@Serializable
data class WangyArtistInfo(
    /** Artist ID */
    val id: Long,
    /** Artist name */
    val name: String
)

/**
 * WangY Music album information for song detail.
 */
@Serializable
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
@Serializable
data class WangyPrivilege(
    /** Song ID */
    val id: Long,
    /** Play status */
    val play: Boolean = true,
    /** Download status */
    val download: Boolean = true,
    /** Play status for subcode */
    @SerialName("playMaxbr")
    val playMaxbr: Int = 0,
    /** Download status for subcode */
    @SerialName("downloadMaxbr")
    val downloadMaxbr: Int = 0,
    /** Fee type */
    val fee: Int = 0,
    /** Experience level */
    @SerialName("expLevel")
    val expLevel: Int = 0
)
