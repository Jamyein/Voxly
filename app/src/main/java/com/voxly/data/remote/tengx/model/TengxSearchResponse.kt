package com.voxly.data.remote.tengx.model

/**
 * TengX Music search response model.
 * Response structure from TengX Music search API.
 */
data class TengxSearchResponse(
    /** Request result code */
    val code: Int,
    /** Search result data */
    val data: TengxSearchData? = null,
    /** Message from server */
    val message: String? = null
)

/**
 * TengX Music search result container.
 */
data class TengxSearchData(
    /** Song search results */
    val song: TengxSongResult? = null,
    /** Album search results */
    val album: TengxAlbumResult? = null,
    /** Artist search results */
    val singer: TengxSingerResult? = null
)

/**
 * TengX Music song search result.
 */
data class TengxSongResult(
    /** List of songs */
    val list: List<TengxSong> = emptyList(),
    /** Total number of songs */
    val totalnum: Int = 0
)

/**
 * TengX Music song item.
 */
data class TengxSong(
    /** Song ID */
    val id: Long,
    /** Song name */
    val name: String,
    /** Song title (often same as name) */
    val title: String,
    /** Subtitle */
    val subtitle: String = "",
    /** Singer information */
    val singer: List<TengxSinger> = emptyList(),
    /** Album information */
    val album: TengxAlbum? = null,
    /** Duration in milliseconds */
    val interval: Int = 0,
    /** Song version */
    val version: Int = 0
)

/**
 * TengX Music album search result.
 */
data class TengxAlbumResult(
    /** List of albums */
    val list: List<TengxAlbum> = emptyList(),
    /** Total number of albums */
    val totalnum: Int = 0
)

/**
 * TengX Music album item.
 */
data class TengxAlbum(
    /** Album ID */
    val id: Long,
    /** Album name */
    val name: String,
    /** Album title */
    val title: String = "",
    /** Singer information */
    val singer: TengxSinger? = null,
    /** Album publish date */
    val publicTime: String = "",
    /** Album cover image URL */
    val pic: String = ""
)

/**
 * TengX Music singer search result.
 */
data class TengxSingerResult(
    /** List of singers */
    val list: List<TengxSinger> = emptyList(),
    /** Total number of singers */
    val totalnum: Int = 0
)

/**
 * TengX Music singer/artist item.
 */
data class TengxSinger(
    /** Singer ID */
    val id: Long,
    /** Singer name */
    val name: String,
    /** Singer name for display */
    val title: String = "",
    /** Singer type */
    val type: Int = 0,
    /** Singer gender */
    val gender: Int = 0,
    /** Singer image URL */
    val pic: String = ""
)
