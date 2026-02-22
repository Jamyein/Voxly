package com.voxly.data.remote.wangy.model

import com.google.gson.annotations.SerializedName

/**
 * WangY Music song detail response model.
 * Response structure from WangY Cloud Music song detail API.
 */
data class WangySongDetail(
    /** Request result code: 200 indicates success */
    @SerializedName("code")
    val code: Int,
    /** List of song details */
    @SerializedName("songs")
    val songs: List<WangySongDetailItem> = emptyList(),
    /** Privileges information */
    @SerializedName("privileges")
    val privileges: List<WangyPrivilege> = emptyList()
)

/**
 * WangY Music detailed song information.
 */
data class WangySongDetailItem(
    /** Song ID */
    @SerializedName("id")
    val id: Long,
    /** Song name */
    @SerializedName("name")
    val name: String,
    /** Artist information (also "ar") */
    @SerializedName("artists")
    val artists: List<WangyArtistInfo> = emptyList(),
    @SerializedName("ar")
    val ar: List<WangyArtistInfo> = emptyList(),
    /** Album information (also "al") */
    @SerializedName("album")
    val album: WangyAlbumInfo? = null,
    @SerializedName("al")
    val al: WangyAlbumInfo? = null,
    /** Duration in milliseconds */
    @SerializedName("dt")
    val dt: Long = 0,
    /** Publish timestamp */
    @SerializedName("publishTime")
    val publishTime: Long = 0,
    /** Song version */
    @SerializedName("version")
    val version: Int = 0
)

/**
 * WangY Music artist information for song detail.
 */
data class WangyArtistInfo(
    /** Artist ID */
    @SerializedName("id")
    val id: Long,
    /** Artist name */
    @SerializedName("name")
    val name: String
)

/**
 * WangY Music album information for song detail.
 */
data class WangyAlbumInfo(
    /** Album ID */
    @SerializedName("id")
    val id: Long,
    /** Album name */
    @SerializedName("name")
    val name: String,
    /** Album cover image URL */
    @SerializedName("picUrl")
    val picUrl: String = ""
)

/**
 * WangY Music privilege information.
 */
data class WangyPrivilege(
    /** Song ID */
    @SerializedName("id")
    val id: Long,
    /** Play status */
    @SerializedName("play")
    val play: Boolean = true,
    /** Download status */
    @SerializedName("download")
    val download: Boolean = true,
    /** Play status for subcode */
    @SerializedName("playMaxbr")
    val playMaxbr: Int = 0,
    /** Download status for subcode */
    @SerializedName("downloadMaxbr")
    val downloadMaxbr: Int = 0,
    /** Fee type */
    @SerializedName("fee")
    val fee: Int = 0,
    /** Experience level */
    @SerializedName("expLevel")
    val expLevel: Int = 0
)
