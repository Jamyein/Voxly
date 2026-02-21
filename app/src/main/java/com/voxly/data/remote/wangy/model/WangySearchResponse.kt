package com.voxly.data.remote.wangy.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * WangY Music error response model.
 * Used when API returns error responses (301, 302, 400, 403, 404, etc.)
 */
data class WangyErrorResponse(
    /** Error code */
    val code: Int = -1,
    /** Error message */
    val message: String? = null
)

/**
 * Flexible search response model that can handle multiple API formats.
 * Uses JsonObject for flexible parsing of different response structures.
 */
data class WangySearchResponse(
    /** Request result code: 200 indicates success */
    @SerializedName("code")
    val code: Int = -1,
    /** Search result data (for web APIs) */
    @SerializedName("result")
    val result: WangySearchResult? = null,
    /** Search data (for EAPI) */
    @SerializedName("data")
    val data: JsonObject? = null,
    /** Raw JSON for flexible parsing */
    @SerializedName("raw")
    val raw: JsonObject? = null
) {
    companion object {
        fun fromJsonObject(jsonObject: JsonObject): WangySearchResponse {
            return WangySearchResponse(
                code = jsonObject.get("code")?.asInt ?: -1,
                result = null,
                data = jsonObject.get("data")?.asJsonObject,
                raw = jsonObject
            )
        }
    }
}

/**
 * WangY Music search result container.
 */
data class WangySearchResult(
    /** Whether there are more results */
    @SerializedName("hasMore")
    val hasMore: Boolean = false,
    /** Search query */
    @SerializedName("queryCorrected")
    val queryCorrected: List<String> = emptyList(),
    /** Song search results */
    @SerializedName("songs")
    val songs: List<WangySong> = emptyList(),
    /** Album search results */
    @SerializedName("albums")
    val albums: List<WangyAlbum> = emptyList(),
    /** Artist search results */
    @SerializedName("artists")
    val artists: List<WangyArtist> = emptyList(),
    /** Total number of songs */
    @SerializedName("songCount")
    val songCount: Int = 0,
    /** Total number of albums */
    @SerializedName("albumCount")
    val albumCount: Int = 0,
    /** Total number of artists */
    @SerializedName("artistCount")
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

// ============== New EAPI Search Response Models ==============

/**
 * WangY Music search response model for eapi endpoint.
 * Response structure from new eapi search endpoint `/api/search/song/list/page`.
 */
data class WangySearchResponseEapi(
    /** Request result code: 200 indicates success */
    val code: Int = 0,
    /** Search result data */
    val data: WangySearchDataEapi? = null
)

/**
 * WangY Music search data container for eapi response.
 */
data class WangySearchDataEapi(
    /** Total number of results */
    val totalCount: Int = 0,
    /** List of search resources */
    val resources: List<WangySearchResource> = emptyList()
)

/**
 * WangY Music search resource item in eapi response.
 */
data class WangySearchResource(
    /** Resource type (0 = song) */
    val resourceType: Int = 0,
    /** Base information containing song data */
    val baseInfo: WangyBaseInfo? = null
)

/**
 * WangY Music base info containing simple song data.
 */
data class WangyBaseInfo(
    /** Simplified song data */
    val simpleSongData: WangySimpleSong? = null
)

/**
 * WangY Music simplified song data in eapi search response.
 */
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
