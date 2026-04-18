package com.voxly.domain.usecase

import timber.log.Timber
import com.voxly.domain.model.AudioMetadata
import javax.inject.Inject

/**
 * Use case for applying online metadata to audio files.
 * Consolidates the logic for merging online metadata with existing metadata.
 */
class ApplyOnlineMetadataUseCase @Inject constructor() {
    private val TAG = "ApplyOnlineMetadata"

    /**
     * Applies online metadata to existing metadata.
     * Validates values and tracks which fields were modified.
     *
     * @param currentMetadata The current metadata
     * @param onlineMetadata The online metadata to apply
     * @return Pair of (updated metadata, set of modified field names)
     */
    operator fun invoke(
        currentMetadata: AudioMetadata,
        onlineMetadata: AudioMetadata
    ): ApplyMetadataResult {
        Timber.d(TAG, "Applying online metadata: current title=${currentMetadata.title}, new title=${onlineMetadata.title}")

        val modifiedFields = mutableSetOf<String>()

        fun String?.isValidValue(): Boolean {
            if (this.isNullOrBlank()) return false
            val lower = this.trim().lowercase()
            return lower !in setOf(
                "unknown", "unknown artist", "unknown album", "unknown track",
                "0", "null", "n/a", "tbd", "-"
            )
        }

        fun String?.isMeaningfulLyrics(): Boolean {
            if (this.isNullOrBlank()) return false
            val cleaned = this.replace(Regex("""\[\d{2}:\d{2}\.\d{2,3}\]"""), "")
                .replace(Regex("""\[\d{2}:\d{2}\]"""), "")
                .replace(Regex("""\[ti:.*?\]|\[ar:.*?\]|\[al:.*?\]"""), "")
                .trim()
            return cleaned.isNotBlank()
        }

        val updatedMetadata = currentMetadata.copy(
            title = onlineMetadata.title.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.title) modifiedFields.add("TITLE") }
                ?: currentMetadata.title,
            artist = onlineMetadata.artist.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.artist) modifiedFields.add("ARTIST") }
                ?: currentMetadata.artist,
            album = onlineMetadata.album.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.album) modifiedFields.add("ALBUM") }
                ?: currentMetadata.album,
            albumArtist = onlineMetadata.albumArtist.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.albumArtist) modifiedFields.add("ALBUM_ARTIST") }
                ?: currentMetadata.albumArtist,
            year = onlineMetadata.year.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.year) modifiedFields.add("YEAR") }
                ?: currentMetadata.year,
            genre = onlineMetadata.genre.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.genre) modifiedFields.add("GENRE") }
                ?: currentMetadata.genre,
            trackNumber = onlineMetadata.trackNumber?.takeIf { it > 0 } ?: currentMetadata.trackNumber,
            totalTracks = onlineMetadata.totalTracks?.takeIf { it > 0 } ?: currentMetadata.totalTracks,
            discNumber = onlineMetadata.discNumber?.takeIf { it > 0 } ?: currentMetadata.discNumber,
            totalDiscs = onlineMetadata.totalDiscs?.takeIf { it > 0 } ?: currentMetadata.totalDiscs,
            comment = onlineMetadata.comment.takeIf { it.isValidValue() }
                ?.also { if (it != currentMetadata.comment) modifiedFields.add("COMMENT") }
                ?: currentMetadata.comment,
            lyrics = onlineMetadata.lyrics.takeIf { it.isValidValue() && it.isMeaningfulLyrics() }
                ?.also { if (it != currentMetadata.lyrics) modifiedFields.add("LYRICS") }
                ?: currentMetadata.lyrics,
            albumArt = onlineMetadata.albumArt ?: currentMetadata.albumArt
        ).also {
            if (onlineMetadata.albumArt != null && !onlineMetadata.albumArt.contentEquals(currentMetadata.albumArt)) {
                modifiedFields.add("ALBUM_ART")
            }
        }

        Timber.d(TAG, "Applied metadata: title=${updatedMetadata.title}, modifiedFields=$modifiedFields")

        return ApplyMetadataResult(updatedMetadata, modifiedFields)
    }
}

/**
 * Result of applying online metadata.
 */
data class ApplyMetadataResult(
    val metadata: AudioMetadata,
    val modifiedFields: Set<String>
)
