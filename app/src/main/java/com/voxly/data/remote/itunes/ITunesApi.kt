package com.voxly.data.remote.itunes

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for iTunes Search API.
 * Provides access to Apple's music catalog metadata.
 * 
 * Base URL: https://itunes.apple.com
 * Documentation: https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/
 */
interface ITunesApi {

    companion object {
        const val BASE_URL = "https://itunes.apple.com/"
    }

    /**
     * Searches the iTunes Store for content.
     * 
     * @param term The URL-encoded text string you want to search for
     * @param entity The type of results you want returned (song, album, etc.)
     * @param limit The number of search results you want the iTunes Store to return (1-200)
     * @param country The two-letter country code for the store you want to search
     * @param lang The language code (en_us, ja_jp, etc.)
     * @return Search response with results
     */
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String? = null,
        @Query("limit") limit: Int = 25,
        @Query("country") country: String? = null,
        @Query("lang") lang: String? = null
    ): Response<ITunesSearchResponse>

    /**
     * Searches for songs by title and artist.
     * 
     * @param title Song title
     * @param artist Artist name (optional)
     * @param limit Maximum number of results
     * @param country Country code
     * @return Search response with song results
     */
    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String,
        @Query("entity") entity: String = ITunesEntity.MUSIC_TRACK.value,
        @Query("limit") limit: Int = 25,
        @Query("country") country: String? = null
    ): Response<ITunesSearchResponse>

    /**
     * Searches for albums by artist and album name.
     * 
     * @param artist Artist name
     * @param album Album name (optional)
     * @param limit Maximum number of results
     * @param country Country code
     * @return Search response with album results
     */
    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("entity") entity: String = ITunesEntity.ALBUM.value,
        @Query("limit") limit: Int = 25,
        @Query("country") country: String? = null
    ): Response<ITunesSearchResponse>

    /**
     * Searches for artists.
     * 
     * @param artist Artist name
     * @param limit Maximum number of results
     * @param country Country code
     * @return Search response with artist results
     */
    @GET("search")
    suspend fun searchArtists(
        @Query("term") term: String,
        @Query("entity") entity: String = ITunesEntity.MUSIC_ARTIST.value,
        @Query("limit") limit: Int = 25,
        @Query("country") country: String? = null
    ): Response<ITunesSearchResponse>

    /**
     * Looks up content by ID.
     * 
     * @param id The iTunes ID of the item
     * @param entity The type of related items to return
     * @return Search response with the item
     */
    @GET("lookup")
    suspend fun lookup(
        @Query("id") id: Long,
        @Query("entity") entity: String? = null
    ): Response<ITunesSearchResponse>
}
