package com.voxly.data.remote.wangy.ne

import com.google.gson.annotations.SerializedName

/**
 * Netease Cloud Music API response models.
 * These models support the EAPI interface with anonymous login.
 */

// ============== Search Response ==============

/**
 * Netease search response from EAPI endpoint.
 */
data class NeSearchResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("data")
    val data: NeSearchData? = null
)

/**
 * Search data container.
 */
data class NeSearchData(
    @SerializedName("totalCount")
    val totalCount: Int = 0,
    @SerializedName("resources")
    val resources: List<NeSearchResource> = emptyList()
)

/**
 * Search resource item (song).
 */
data class NeSearchResource(
    @SerializedName("resourceType")
    val resourceType: Int = 0,
    @SerializedName("baseInfo")
    val baseInfo: NeBaseInfo? = null
)

/**
 * Base info containing simplified song data.
 */
data class NeBaseInfo(
    @SerializedName("simpleSongData")
    val simpleSongData: NeSimpleSong? = null
)

/**
 * Simplified song data in search results.
 */
data class NeSimpleSong(
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("ar")
    val artists: List<NeArtist> = emptyList(),
    @SerializedName("al")
    val album: NeAlbum? = null,
    @SerializedName("dt")
    val duration: Long = 0,
    @SerializedName("publishTime")
    val publishTime: Long = 0
)

// ============== Lyrics Response ==============

/**
 * Netease lyrics response from EAPI endpoint.
 */
data class NeLyricResponse(
    @SerializedName("code")
    val code: Int = 0,
    @SerializedName("lrc")
    val lrc: NeLrcContainer? = null,
    @SerializedName("tlyric")
    val tlyric: NeLrcContainer? = null,
    @SerializedName("romalrc")
    val romalrc: NeLrcContainer? = null,
    @SerializedName("yrc")
    val yrc: NeYrcContainer? = null
)

/**
 * Container for standard LRC lyrics.
 */
data class NeLrcContainer(
    @SerializedName("version")
    val version: Int = 0,
    @SerializedName("lyric")
    val lyric: String = ""
)

/**
 * Container for YRC (enhanced) lyrics.
 */
data class NeYrcContainer(
    @SerializedName("version")
    val version: Int = 0,
    @SerializedName("lyric")
    val lyric: String = ""
)

// ============== Anonymous Login Response ==============

/**
 * Anonymous login response.
 */
data class NeAnonLoginResponse(
    @SerializedName("code")
    val code: Int = -1,
    @SerializedName("userId")
    val userId: Long = 0,
    @SerializedName("nickname")
    val nickname: String? = null
)

// ============== Basic Models ==============

/**
 * Artist information.
 */
data class NeArtist(
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("name")
    val name: String = ""
)

/**
 * Album information.
 */
data class NeAlbum(
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("picUrl")
    val picUrl: String = ""
)
