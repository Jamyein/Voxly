package com.mp3tag.android.data.remote.musicbrainz.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for MusicBrainz search API.
 */
data class MusicBrainzSearchResponse(
    @SerializedName("created")
    val created: String? = null,

    @SerializedName("count")
    val count: Int = 0,

    @SerializedName("offset")
    val offset: Int = 0,

    @SerializedName("release-groups")
    val releaseGroups: List<MusicBrainzReleaseGroup>? = null,

    @SerializedName("recordings")
    val recordings: List<MusicBrainzRecording>? = null
)

/**
 * Model representing a MusicBrainz release group (album).
 */
data class MusicBrainzReleaseGroup(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("first-release-date")
    val firstReleaseDate: String? = null,

    @SerializedName("primary-type")
    val primaryType: String? = null,

    @SerializedName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerializedName("releases")
    val releases: List<MusicBrainzReleaseInfo>? = null,

    @SerializedName("tags")
    val tags: List<MusicBrainzTag>? = null,

    @SerializedName("genres")
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
data class MusicBrainzRecording(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("length")
    val length: Long? = null,

    @SerializedName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerializedName("releases")
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
data class MusicBrainzReleaseResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("date")
    val date: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("barcode")
    val barcode: String? = null,

    @SerializedName("asin")
    val asin: String? = null,

    @SerializedName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerializedName("release-group")
    val releaseGroup: MusicBrainzReleaseGroupInfo? = null,

    @SerializedName("media")
    val media: List<MusicBrainzMedia>? = null,

    @SerializedName("genres")
    val genres: List<MusicBrainzGenre>? = null,

    @SerializedName("tags")
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
data class MusicBrainzArtistCredit(
    @SerializedName("artist")
    val artist: MusicBrainzArtist? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("joinphrase")
    val joinPhrase: String? = null
)

/**
 * Model representing a MusicBrainz artist.
 */
data class MusicBrainzArtist(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("sort-name")
    val sortName: String? = null
)

/**
 * Model representing a MusicBrainz release group info.
 */
data class MusicBrainzReleaseGroupInfo(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("primary-type")
    val primaryType: String? = null
)

/**
 * Model representing a MusicBrainz release info.
 */
data class MusicBrainzReleaseInfo(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("date")
    val date: String? = null
)

/**
 * Model representing media (e.g., CD, digital) in a release.
 */
data class MusicBrainzMedia(
    @SerializedName("position")
    val position: Int? = null,

    @SerializedName("format")
    val format: String? = null,

    @SerializedName("track-count")
    val trackCount: Int = 0,

    @SerializedName("tracks")
    val tracks: List<MusicBrainzTrack>? = null
)

/**
 * Model representing a track in a release.
 */
data class MusicBrainzTrack(
    @SerializedName("id")
    val id: String,

    @SerializedName("number")
    val number: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("length")
    val length: Long? = null,

    @SerializedName("artist-credit")
    val artistCredit: List<MusicBrainzArtistCredit>? = null,

    @SerializedName("recording")
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
}

/**
 * Model representing a MusicBrainz tag.
 */
data class MusicBrainzTag(
    @SerializedName("name")
    val name: String,

    @SerializedName("count")
    val count: Int = 0
)

/**
 * Model representing a MusicBrainz genre.
 */
data class MusicBrainzGenre(
    @SerializedName("name")
    val name: String,

    @SerializedName("count")
    val count: Int = 0
)
