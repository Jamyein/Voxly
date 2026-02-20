package com.voxly.data.remote.wangy.model

import com.google.gson.annotations.SerializedName

/**
 * WangY Music search response model.
 * Response structure from WangY Cloud Music search API.
 */
data class WangySearchResponse(
    /** Request result code: 200 indicates success */
    val code: Int,
    /** Search result data */
    val result: WangySearchResult? = null
)

/**
 * WangY Music search result container.
 */
data class WangySearchResult(
    /** Whether there are more results */
    val hasMore: Boolean = false,
    /** Search query */
    val queryCorrected: List<String> = emptyList(),
    /** Song search results */
    val songs: List<WangySong> = emptyList(),
    /** Album search results */
    val albums: List<WangyAlbum> = emptyList(),
    /** Artist search results */
    val artists: List<WangyArtist> = emptyList(),
    /** Total number of songs */
    val songCount: Int = 0,
    /** Total number of albums */
    val albumCount: Int = 0,
    /** Total number of artists */
    val artistCount: Int = 0
)

/**
 * WangY Music song item in search results.
 */
data class WangySong(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Artist information (JSON field: ar) */
    @SerializedName("ar")
    val artists: List<WangyArtist> = emptyList(),
    /** Album information (JSON field: al) */
    @SerializedName("al")
    val album: WangyAlbum? = null,
    /** Duration in milliseconds (JSON field: dt) */
    @SerializedName("dt")
    val duration: Long = 0,
    /** Copyright ID */
    val copyrightId: Long = 0,
    /** Whether it's a paid song */
    val fee: Int = 0,
    /** Track number in album (JSON field: no) */
    @SerializedName("no")
    val trackNumber: Int = 0,
    /** Song version */
    val version: Int = 0
)

/**
 * WangY Music album item in search results.
 */
data class WangyAlbum(
    /** Album ID */
    val id: Long,
    /** Album name (JSON field: name) */
    val name: String,
    /** Artist information (JSON field: artist) */
    val artist: WangyArtist? = null,
    /** Album publish date (format: "yyyy-MM-dd") (JSON field: publishTime) */
    @SerializedName("publishTime")
    val publishDate: String = "",
    /** Album size (JSON field: size) */
    val size: Int = 0,
    /** Number of songs (JSON field: size) */
    @SerializedName("size")
    val songsCount: Int = 0,
    /** Album cover image URL (JSON field: picUrl) */
    @SerializedName("picUrl")
    val picUrl: String = ""
)

/**
 * WangY Music artist item.
 */
data class WangyArtist(
    /** Artist ID */
    val id: Long,
    /** Artist name */
    val name: String,
    /** Artist image URL */
    val picUrl: String = "",
    /** Number of albums */
    val albumSize: Int = 0,
    /** Number of fans */
    val fans: Long = 0
)
