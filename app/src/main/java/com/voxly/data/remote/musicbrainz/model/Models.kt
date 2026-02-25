package com.voxly.data.remote.musicbrainz.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for MusicBrainz search API.
 */
@Serializable
data class MusicBrainzSearchResponse(
    @SerialName("created")
    val created: String? = null,

    @SerialName("count")
    val count: Int = 0,

    @SerialName("offset")
    val offset: Int = 0,

    @SerialName("release-groups")
    val releaseGroups: List<MusicBrainzReleaseGroup>? = null,

    @SerialName("recordings")
    val recordings: List<MusicBrainzRecording>? = null
)

/**
 * Model representing a MusicBrainz release group (album).
 */
@Serializable
data class MusicBrainzReleaseGroup(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("title")
    val title: String,

    @SerialName("first-release-date")
    val firstReleaseDate: String? = null,

    @SerialName("primary-type")
    val primaryType: String? = null,

    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerialName("releases")
    val releases: List<MusicBrainzReleaseInfo>? = null,

    @SerialName("tags")
    val tags: List<MusicBrainzTag>? = null,

    @SerialName("genres")
    val genres: List<MusicBrainzGenre>? = null
) {
    /**
     * Gets the release year from the first release date.
     */
    fun getReleaseYear(): Int? {
        return firstReleaseDate?.take(4)?.toIntOrNull()
    }

    /**
     * Gets the primary artist name.
     */
    fun getArtistName(): String? {
        return artistCredit?.joinToString(" & ") { it.artist?.name ?: "" }
    }
}

/**
 * Model representing a MusicBrainz recording (track).
 */
@Serializable
data class MusicBrainzRecording(
    @SerialName("id")
    val id: String,

    @SerialName("title")
    val title: String,

    @SerialName("length")
    val length: Long? = null,

    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerialName("releases")
    val releases: List<MusicBrainzReleaseInfo>? = null
) {
    /**
     * Gets the primary artist name.
     */
    fun getArtistName(): String? {
        return artistCredit?.joinToString(" & ") { it.artist?.name ?: "" }
    }

    /**
     * Gets duration in milliseconds.
     */
    fun getDurationMs(): Long? = length
}

/**
 * Model representing detailed release information.
 */
@Serializable
data class MusicBrainzReleaseResponse(
    @SerialName("id")
    val id: String,

    @SerialName("title")
    val title: String,

    @SerialName("status")
    val status: String? = null,

    @SerialName("date")
    val date: String? = null,

    @SerialName("country")
    val country: String? = null,

    @SerialName("barcode")
    val barcode: String? = null,

    @SerialName("asin")
    val asin: String? = null,

    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerialName("release-group")
    val releaseGroup: MusicBrainzReleaseGroupInfo? = null,

    @SerialName("media")
    val media: List<MusicBrainzMedia>? = null,

    @SerialName("genres")
    val genres: List<MusicBrainzGenre>? = null,

    @SerialName("tags")
    val tags: List<MusicBrainzTag>? = null
) {
    /**
     * Gets the release year.
     */
    fun getReleaseYear(): Int? {
        return date?.take(4)?.toIntOrNull()
    }

    /**
     * Gets the primary artist name.
     */
    fun getArtistName(): String? {
        return artistCredit?.joinToString(" & ") { it.artist?.name ?: "" }
    }

    /**
     * Gets all tracks from the release.
     */
    fun getAllTracks(): List<MusicBrainzTrack> {
        return media?.flatMap { it.tracks ?: emptyList() } ?: emptyList()
    }
}

/**
 * Model representing an artist credit.
 */
@Serializable
data class MusicBrainzArtistCredit(
    @SerialName("artist")
    val artist: MusicBrainzArtist? = null,

    @SerialName("name")
    val name: String? = null,

    @SerialName("joinphrase")
    val joinPhrase: String? = null
)

/**
 * Model representing a MusicBrainz artist.
 */
@Serializable
data class MusicBrainzArtist(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("sort-name")
    val sortName: String? = null
)

/**
 * Model representing a MusicBrainz release group info.
 */
@Serializable
data class MusicBrainzReleaseGroupInfo(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("primary-type")
    val primaryType: String? = null
)

/**
 * Model representing a MusicBrainz release info.
 */
@Serializable
data class MusicBrainzReleaseInfo(
    @SerialName("id")
    val id: String,

    @SerialName("title")
    val title: String? = null,

    @SerialName("date")
    val date: String? = null
)

/**
 * Model representing media (e.g., CD, digital) in a release.
 */
@Serializable
data class MusicBrainzMedia(
    @SerialName("position")
    val position: Int? = null,

    @SerialName("format")
    val format: String? = null,

    @SerialName("track-count")
    val trackCount: Int = 0,

    @SerialName("tracks")
    val tracks: List<MusicBrainzTrack>? = null
)

/**
 * Model representing a track in a release.
 */
@Serializable
data class MusicBrainzTrack(
    @SerialName("id")
    val id: String,

    @SerialName("number")
    val number: String? = null,

    @SerialName("title")
    val title: String,

    @SerialName("length")
    val length: Long? = null,

    @SerialName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerialName("recording")
    val recording: MusicBrainzRecording? = null
) {
    /**
     * Gets the track number as an integer.
     */
    fun getTrackNumber(): Int? {
        return number?.toIntOrNull()
    }

    /**
     * Gets duration in milliseconds.
     */
    fun getDurationMs(): Long? = length ?: recording?.length

    /**
     * Gets the primary artist name.
     */
    fun getArtistName(): String? {
        return artistCredit?.joinToString(" & ") { it.artist?.name ?: "" }
    }
}

/**
 * Model representing a MusicBrainz tag.
 */
@Serializable
data class MusicBrainzTag(
    @SerialName("name")
    val name: String,

    @SerialName("count")
    val count: Int = 0
)

/**
 * Model representing a MusicBrainz genre.
 */
@Serializable
data class MusicBrainzGenre(
    @SerialName("name")
    val name: String,

    @SerialName("count")
    val count: Int = 0
)
