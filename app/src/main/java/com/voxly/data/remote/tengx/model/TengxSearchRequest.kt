package com.voxly.data.remote.tengx.model

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
data class TengxSearchRequest(
    /** Common request parameters */
    val comm: TengxCommParams,
    /** Search request parameters */
    val req: TengxSearchReqParams
)

/**
 * Common request parameters for TengX Music API.
 */
data class TengxCommParams(
    /** Client type: 11 for mobile */
    val ct: String = "11",
    /** Client version: TengX Music Android version */
    val cv: String = "14090508",
    /** Auth salt */
    val authq: String = "",
    /** Login Uin (0 for guest) */
    val loginUin: Int = 0,
    /** Device info */
    val deviceInfo: String = "",
    /** Platform */
    val platform: String = ""
)

/**
 * Search request parameters.
 */
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
data class TengxSearchParam(
    /** Search type: 0=song, 2=album, 3=singer */
    val search_type: Int = 0,
    /** Search query/keyword */
    val query: String = "",
    /** Page number (0-indexed) */
    val page_num: Int = 0,
    /** Number of results per page */
    val num_per_page: Int = 20
)
