package com.voxly.data.remote.wangy.model

import kotlinx.serialization.Serializable

/**
 * WangY Music album detail response model.
 * Response structure from WangY Cloud Music album detail API.
 */
@Serializable
data class WangyAlbumDetail(
    /** Request result code: 200 indicates success */
    val code: Int,
    /** Album information */
    val album: WangyAlbumDetailInfo? = null,
    /** List of songs in the album */
    val songs: List<WangyAlbumSong> = emptyList(),
    /** Privileges information */
    val privileges: List<WangyPrivilege> = emptyList()
)

/**
 * WangY Music detailed album information.
 */
@Serializable
data class WangyAlbumDetailInfo(
    /** Album ID */
    val id: Long,
    /** Album name */
    val name: String,
    /** Artist information */
    val artist: WangyArtistBasic? = null,
    /** Publishing company */
    val company: String = "",
    /** Album cover image URL */
    val picUrl: String = "",
    /** Album publish timestamp */
    val publishTime: Long = 0,
    /** Album description */
    val description: String = "",
    /** Album tags */
    val tags: String = "",
    /** Number of songs */
    val size: Int = 0,
    /** Artists list (for albums with multiple artists) */
    val artists: List<WangyArtistBasic> = emptyList()
)

/**
 * WangY Music basic artist information.
 */
@Serializable
data class WangyArtistBasic(
    /** Artist ID */
    val id: Long,
    /** Artist name */
    val name: String,
    /** Artist image URL */
    val picUrl: String = ""
)

/**
 * WangY Music song in album.
 */
@Serializable
data class WangyAlbumSong(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Artist information */
    val ar: List<WangyArtistBasic> = emptyList(),
    /** Album information */
    val al: WangyAlbumBasic? = null,
    /** Duration in milliseconds */
    val dt: Long = 0,
    /** Track position in album */
    val position: Int? = null,
    /** Track number */
    val trackNo: Int = 0,
    /** Disc number */
    val cd: String = ""
)

/**
 * WangY Music basic album information.
 */
@Serializable
data class WangyAlbumBasic(
    /** Album ID */
    val id: Long,
    /** Album name */
    val name: String,
    /** Album cover image URL */
    val picUrl: String = ""
)
