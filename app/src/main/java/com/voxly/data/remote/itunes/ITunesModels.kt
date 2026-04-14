package com.voxly.data.remote.itunes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for iTunes Search API.
 */
@Serializable
data class ITunesSearchResponse(
    @SerialName("resultCount")
    val resultCount: Int,

    @SerialName("results")
    val results: List<ITunesResult>
)

/**
 * Single result from iTunes Search API.
 */
@Serializable
data class ITunesResult(
    @SerialName("wrapperType")
    val wrapperType: String? = null,

    @SerialName("kind")
    val kind: String? = null,

    @SerialName("artistId")
    val artistId: Long? = null,

    @SerialName("collectionId")
    val collectionId: Long? = null,

    @SerialName("trackId")
    val trackId: Long? = null,

    @SerialName("artistName")
    val artistName: String? = null,

    @SerialName("collectionName")
    val collectionName: String? = null,

    @SerialName("trackName")
    val trackName: String? = null,

    @SerialName("collectionCensoredName")
    val collectionCensoredName: String? = null,

    @SerialName("trackCensoredName")
    val trackCensoredName: String? = null,

    @SerialName("artistViewUrl")
    val artistViewUrl: String? = null,

    @SerialName("collectionViewUrl")
    val collectionViewUrl: String? = null,

    @SerialName("trackViewUrl")
    val trackViewUrl: String? = null,

    @SerialName("previewUrl")
    val previewUrl: String? = null,

    @SerialName("artworkUrl30")
    val artworkUrl30: String? = null,

    @SerialName("artworkUrl60")
    val artworkUrl60: String? = null,

    @SerialName("artworkUrl100")
    val artworkUrl100: String? = null,

    @SerialName("artworkUrl512")
    val artworkUrl512: String? = null,

    @SerialName("artworkUrl600")
    val artworkUrl600: String? = null,

    @SerialName("collectionPrice")
    val collectionPrice: Double? = null,

    @SerialName("trackPrice")
    val trackPrice: Double? = null,

    @SerialName("releaseDate")
    val releaseDate: String? = null,

    @SerialName("collectionExplicitness")
    val collectionExplicitness: String? = null,

    @SerialName("trackExplicitness")
    val trackExplicitness: String? = null,

    @SerialName("discCount")
    val discCount: Int? = null,

    @SerialName("discNumber")
    val discNumber: Int? = null,

    @SerialName("trackCount")
    val trackCount: Int? = null,

    @SerialName("trackNumber")
    val trackNumber: Int? = null,

    @SerialName("trackTimeMillis")
    val trackTimeMillis: Long? = null,

    @SerialName("country")
    val country: String? = null,

    @SerialName("currency")
    val currency: String? = null,

    @SerialName("primaryGenreName")
    val primaryGenreName: String? = null,

    @SerialName("contentAdvisoryRating")
    val contentAdvisoryRating: String? = null,

    @SerialName("isStreamable")
    val isStreamable: Boolean? = null,

    @SerialName("collectionArtistId")
    val collectionArtistId: Long? = null,

    @SerialName("collectionArtistName")
    val collectionArtistName: String? = null
) {
    /**
     * Gets the release year from release date.
     */
    fun getReleaseYear(): Int? {
        return releaseDate?.take(4)?.toIntOrNull()
    }

    /**
     * Gets the highest resolution artwork URL available.
     */
    fun getBestArtworkUrl(): String? {
        return artworkUrl600 ?: artworkUrl512 ?: artworkUrl100 ?: artworkUrl60 ?: artworkUrl30
    }

    /**
     * Gets duration in seconds.
     */
    fun getDurationSeconds(): Int? {
        return trackTimeMillis?.let { (it / com.voxly.core.util.Constants.MS_PER_SECOND).toInt() }
    }
}

/**
 * Enum for iTunes search entities.
 */
enum class ITunesEntity(val value: String) {
    MUSIC_TRACK("song"),
    ALBUM("album"),
    MUSIC_ARTIST("musicArtist"),
    MUSIC_VIDEO("musicVideo"),
    MIX("mix"),
    ALL_TRACK("allTrack")
}

/**
 * Enum for iTunes countries (common ones).
 */
enum class ITunesCountry(val code: String) {
    UNITED_STATES("us"),
    CHINA("cn"),
    HONG_KONG("hk"),
    JAPAN("jp"),
    UNITED_KINGDOM("gb"),
    GERMANY("de"),
    FRANCE("fr"),
    CANADA("ca"),
    AUSTRALIA("au")
}
