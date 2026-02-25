package com.voxly.data.remote.wangy.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WangY Music error response model.
 * Used when API returns error responses (301, 302, 400, 403, 404, etc.)
 */
@Serializable
data class WangyErrorResponse(
    /** Error code */
    val code: Int = -1,
    /** Error message */
    val message: String? = null
)

/**
 * WangY Music search response model.
 * Response structure from WangY Music search API.
 */
@Serializable
data class WangySearchResponse(
    /** Request result code: 200 indicates success */
    @SerialName("code")
    val code: Int = -1,
    /** Search result data (for web APIs) */
    @SerialName("result")
    val result: WangySearchResult? = null
)

/**
 * WangY Music search result container.
 */
@Serializable
data class WangySearchResult(
    /** Whether there are more results */
    @SerialName("hasMore")
    val hasMore: Boolean = false,
    /** Search query */
    @SerialName("queryCorrected")
    val queryCorrected: List<String> = emptyList(),
    /** Song search results */
    @SerialName("songs")
    val songs: List<WangySong> = emptyList(),
    /** Album search results */
    @SerialName("albums")
    val albums: List<WangyAlbum> = emptyList(),
    /** Artist search results */
    @SerialName("artists")
    val artists: List<WangyArtist> = emptyList(),
    /** Total number of songs */
    @SerialName("songCount")
    val songCount: Int = 0,
    /** Total number of albums */
    @SerialName("albumCount")
    val albumCount: Int = 0,
    /** Total number of artists */
    @SerialName("artistCount")
    val artistCount: Int = 0
)

/**
 * WangY Music song item in search results.
 */
@Serializable
data class WangySong(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Artist information (JSON field: ar) */
    @SerialName("ar")
    val artists: List<WangyArtist> = emptyList(),
    /** Album information (JSON field: al) */
    @SerialName("al")
    val album: WangyAlbum? = null,
    /** Duration in milliseconds (JSON field: dt) */
    @SerialName("dt")
    val duration: Long = 0,
    /** Copyright ID */
    val copyrightId: Long = 0,
    /** Whether it's a paid song */
    val fee: Int = 0,
    /** Track number in album (JSON field: no) */
    @SerialName("no")
    val trackNumber: Int = 0,
    /** Song version */
    val version: Int = 0,
    /** 歌曲别名/注释 (JSON field: alias) */
    val alias: List<String> = emptyList(),
    /** 碟片编号 (JSON field: disc) */
    val disc: String = ""
)

/**
 * WangY Music album item in search results.
 */
@Serializable
data class WangyAlbum(
    /** Album ID */
    val id: Long,
    /** Album name (JSON field: name) */
    val name: String,
    /** Artist information (JSON field: artist) */
    val artist: WangyArtist? = null,
    /** Album publish date (format: "yyyy-MM-dd") (JSON field: publishTime) */
    @SerialName("publishTime")
    val publishDate: String = "",
    /** Album size (JSON field: size) */
    val size: Int = 0,
    /** Number of songs (JSON field: size) */
    @SerialName("size")
    val songsCount: Int = 0,
    /** Album cover image URL (JSON field: picUrl) */
    @SerialName("picUrl")
    val picUrl: String = "",
    /** 唱片公司 (JSON field: company) */
    val company: String = ""
)

/**
 * WangY Music artist item.
 */
@Serializable
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

// ============== New EAPI Search Response Models ==============

/**
 * WangY Music search response model for eapi endpoint.
 * Response structure from new eapi search endpoint `/api/search/song/list/page`.
 */
@Serializable
data class WangySearchResponseEapi(
    /** Request result code: 200 indicates success */
    val code: Int = 0,
    /** Search result data */
    val data: WangySearchDataEapi? = null
)

/**
 * WangY Music search data container for eapi response.
 */
@Serializable
data class WangySearchDataEapi(
    /** Total number of results */
    val totalCount: Int = 0,
    /** List of search resources */
    val resources: List<WangySearchResource> = emptyList()
)

/**
 * WangY Music search resource item in eapi response.
 */
@Serializable
data class WangySearchResource(
    /** Resource type (0 = song) */
    val resourceType: Int = 0,
    /** Base information containing song data */
    val baseInfo: WangyBaseInfo? = null
)

/**
 * WangY Music base info containing simple song data.
 */
@Serializable
data class WangyBaseInfo(
    /** Simplified song data */
    val simpleSongData: WangySimpleSong? = null
)

/**
 * WangY Music simplified song data in eapi search response.
 */
@Serializable
data class WangySimpleSong(
    /** Song ID */
    val id: Long = 0,
    /** Song name */
    val name: String = "",
    /** Artist information (JSON field: ar) */
    val ar: List<WangyArtist> = emptyList(),
    /** Album information (JSON field: al) */
    val al: WangyAlbum? = null,
    /** Duration in milliseconds (JSON field: dt) */
    val dt: Long = 0
)
