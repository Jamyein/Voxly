package com.voxly.data.remote.itunes

import com.google.gson.annotations.SerializedName

/**
 * Response model for iTunes Search API.
 */
data class ITunesSearchResponse(
    @SerializedName("resultCount")
    val resultCount: Int,

    @SerializedName("results")
    val results: List<ITunesResult>
)

/**
 * Single result from iTunes Search API.
 */
data class ITunesResult(
    @SerializedName("wrapperType")
    val wrapperType: String? = null,

    @SerializedName("kind")
    val kind: String? = null,

    @SerializedName("artistId")
    val artistId: Long? = null,

    @SerializedName("collectionId")
    val collectionId: Long? = null,

    @SerializedName("trackId")
    val trackId: Long? = null,

    @SerializedName("artistName")
    val artistName: String? = null,

    @SerializedName("collectionName")
    val collectionName: String? = null,

    @SerializedName("trackName")
    val trackName: String? = null,

    @SerializedName("collectionCensoredName")
    val collectionCensoredName: String? = null,

    @SerializedName("trackCensoredName")
    val trackCensoredName: String? = null,

    @SerializedName("artistViewUrl")
    val artistViewUrl: String? = null,

    @SerializedName("collectionViewUrl")
    val collectionViewUrl: String? = null,

    @SerializedName("trackViewUrl")
    val trackViewUrl: String? = null,

    @SerializedName("previewUrl")
    val previewUrl: String? = null,

    @SerializedName("artworkUrl30")
    val artworkUrl30: String? = null,

    @SerializedName("artworkUrl60")
    val artworkUrl60: String? = null,

    @SerializedName("artworkUrl100")
    val artworkUrl100: String? = null,

    @SerializedName("collectionPrice")
    val collectionPrice: Double? = null,

    @SerializedName("trackPrice")
    val trackPrice: Double? = null,

    @SerializedName("releaseDate")
    val releaseDate: String? = null,

    @SerializedName("collectionExplicitness")
    val collectionExplicitness: String? = null,

    @SerializedName("trackExplicitness")
    val trackExplicitness: String? = null,

    @SerializedName("discCount")
    val discCount: Int? = null,

    @SerializedName("discNumber")
    val discNumber: Int? = null,

    @SerializedName("trackCount")
    val trackCount: Int? = null,

    @SerializedName("trackNumber")
    val trackNumber: Int? = null,

    @SerializedName("trackTimeMillis")
    val trackTimeMillis: Long? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("currency")
    val currency: String? = null,

    @SerializedName("primaryGenreName")
    val primaryGenreName: String? = null,

    @SerializedName("contentAdvisoryRating")
    val contentAdvisoryRating: String? = null,

    @SerializedName("isStreamable")
    val isStreamable: Boolean? = null,

    @SerializedName("collectionArtistId")
    val collectionArtistId: Long? = null,

    @SerializedName("collectionArtistName")
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
        return artworkUrl100 ?: artworkUrl60 ?: artworkUrl30
    }

    /**
     * Gets duration in seconds.
     */
    fun getDurationSeconds(): Int? {
        return trackTimeMillis?.let { (it / 1000).toInt() }
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
