package com.voxly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheChangeKeysTest {

    private fun audioFile(artist: String?, artistId: Long? = null) = AudioFile(
        path = "/storage/music/test.mp3",
        name = "test.mp3",
        size = 0L,
        duration = 0L,
        format = AudioFormat.MP3,
        bitrate = 0,
        sampleRate = 0,
        channels = 0,
        metadata = AudioMetadata(artist = artist),
        mediaStoreArtistId = artistId
    )

    @Test
    fun `id-backed file with separators splits by name, not by artist id`() {
        val file = audioFile(artist = "A & B", artistId = 5L)

        assertEquals(
            listOf("A", "B"),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, setOf("&"))
        )
    }

    @Test
    fun `id-backed file with empty separators keeps the raw name`() {
        val file = audioFile(artist = "A & B", artistId = 5L)

        assertEquals(
            listOf("A & B"),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, emptySet())
        )
    }

    @Test
    fun `name-only file with separators splits and trims`() {
        val file = audioFile(artist = "A / B & C", artistId = null)

        assertEquals(
            listOf("A", "B", "C"),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, setOf("&", "/"))
        )
    }

    @Test
    fun `name-only file with empty separators keeps the raw name`() {
        val file = audioFile(artist = "A & B", artistId = null)

        assertEquals(
            listOf("A & B"),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, emptySet())
        )
    }

    @Test
    fun `longest separator wins when one is a prefix of another`() {
        val file = audioFile(artist = "A&&B&C", artistId = null)

        assertEquals(
            listOf("A", "B", "C"),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, setOf("&", "&&"))
        )
    }

    @Test
    fun `file without artist yields empty keys`() {
        val file = audioFile(artist = null)

        assertEquals(
            emptyList<String>(),
            CacheChangeKeys.extractArtistKeysWithSeparators(file, setOf("&"))
        )
    }
}
