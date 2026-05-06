package com.voxly.data.remote.tengx.model

import kotlinx.serialization.Serializable

/**
 * TengX Music search request body for POST request.
 * Structure matches the music.search.SearchCgiService API.
 *
 * Based on any-listen-extension-online-metadata:
 * https://github.com/any-listen/any-listen-extension-online-metadata
 * Reference: src/qq_music/index.ts (search request body structure)
 *
 * Uses zzcSign signature for authentication.
 */
@Serializable
data class TengxSearchRequest(
    /** Search request parameters */
    val req: TengxSearchReqParams,
    /** Common request parameters */
    val comm: TengxCommParams
)

/**
 * Common request parameters for TengX Music API.
 *
 * Matches any-listen QQ Music mobile client configuration.
 */
@Serializable
data class TengxCommParams(
    /** Client type: 11 for mobile */
    val ct: String = "11",
    /** Client version */
    val cv: String = "14090508",
    /** API version (required, same as cv) */
    val v: String = "14090508",
    /** User ID (0 for guest) */
    val uin: String = "0"
)

/**
 * Search request parameters.
 */
@Serializable
data class TengxSearchReqParams(
    /** Module name */
    val module: String = "music.search.SearchCgiService",
    /** Method name */
    val method: String = "DoSearchForQQMusicMobile",
    /** Search parameters */
    val param: TengxSearchParam
)

/**
 * Search parameter details.
 */
@Serializable
data class TengxSearchParam(
    /** Search type: 0=song, 2=album, 3=singer */
    val search_type: Int = 0,
    /** Search query/keyword */
    val query: String = "",
    /** Page number (0-indexed, matching QQ Music API convention) */
    val page_num: Int = 0,
    /** Number of results per page (QQ Music uses num_perpage without underscore) */
    val num_perpage: Int = 20
)
