package com.voxly.domain.util

import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSearchSorterTest {

    private val sourcePriority = listOf("MusicBrainz", "iTunes", "NetEase", "QQ_Music")

    // region sortRecordings tests

    @Test
    fun `sortRecordings - empty list returns empty`() {
        val result = OnlineSearchSorter.sortRecordings(
            recordings = emptyList(),
            title = "Test",
            artist = "Artist",
            sourcePriority = sourcePriority
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sortRecordings - exact match scores higher than partial match`() {
        val recordings = listOf(
            createRecording("Test Song", "Artist A", OnlineSource.ITUNES),
            createRecording("Test Song", "Artist A", OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Test Song",
            artist = "Artist A",
            sourcePriority = sourcePriority
        )

        // Both exact match, but MusicBrainz has higher priority -> should be first
        assertEquals(OnlineSource.MUSICBRAINZ, result[0].source)
        assertEquals(OnlineSource.ITUNES, result[1].source)
    }

    @Test
    fun `sortRecordings - same relevance respects source priority`() {
        val recordings = listOf(
            createRecording("Song", "Artist", OnlineSource.QQ_MUSIC),
            createRecording("Song", "Artist", OnlineSource.NETEASE),
            createRecording("Song", "Artist", OnlineSource.ITUNES),
            createRecording("Song", "Artist", OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Song",
            artist = "Artist",
            sourcePriority = sourcePriority
        )

        // All results have same relevance, sorted by priority
        assertEquals(OnlineSource.MUSICBRAINZ, result[0].source)
        assertEquals(OnlineSource.ITUNES, result[1].source)
        assertEquals(OnlineSource.NETEASE, result[2].source)
        assertEquals(OnlineSource.QQ_MUSIC, result[3].source)
    }

    @Test
    fun `sortRecordings - high relevance from low priority beats low relevance from high priority`() {
        val recordings = listOf(
            createRecording("Wrong Song", "Wrong Artist", OnlineSource.MUSICBRAINZ),  // High priority, no match
            createRecording("Target Song", "Target Artist", OnlineSource.QQ_MUSIC)     // Low priority, exact match
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Target Song",
            artist = "Target Artist",
            sourcePriority = listOf("MusicBrainz", "QQ_Music")
        )

        // Exact match should rank first despite lower source priority
        assertEquals("Target Song", result[0].title)
        assertEquals("Wrong Song", result[1].title)
    }

    @Test
    fun `sortRecordings - unknown source should be ranked lower than known sources`() {
        val recordings = listOf(
            createRecording("Exact Match", "Artist", OnlineSource.UNKNOWN),
            createRecording("Exact Match", "Artist", OnlineSource.QQ_MUSIC)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Exact Match",
            artist = "Artist",
            sourcePriority = sourcePriority
        )

        // UNKNOWN source should be ranked lower even with exact match
        assertEquals(OnlineSource.QQ_MUSIC, result[0].source)
        assertEquals(OnlineSource.UNKNOWN, result[1].source)
    }

    @Test
    fun `sortRecordings - partial title match scores lower than exact match`() {
        val samePriority = listOf("MusicBrainz")
        val recordings = listOf(
            createRecording("Song Title Extended", "Artist", OnlineSource.MUSICBRAINZ),
            createRecording("Song Title", "Artist", OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Song Title",
            artist = "Artist",
            sourcePriority = samePriority
        )

        // Exact match should rank higher than partial match when source priority is equal
        assertEquals("Song Title", result[0].title)
    }

    @Test
    fun `sortRecordings - title match has more weight than artist match`() {
        val recordings = listOf(
            createRecording("Wrong Title", "Target Artist", OnlineSource.MUSICBRAINZ),  // Artist matches
            createRecording("Target Title", "Wrong Artist", OnlineSource.MUSICBRAINZ)   // Title matches
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Target Title",
            artist = "Target Artist",
            sourcePriority = listOf("MusicBrainz")
        )

        // Title match should rank higher (2x weight)
        assertEquals("Target Title", result[0].title)
    }

    // endregion

    // region sortReleases tests

    @Test
    fun `sortReleases - uses songTitle for matching`() {
        val releases = listOf(
            createRelease(songTitle = "Target Song", albumTitle = "Album", artist = "Artist", source = OnlineSource.MUSICBRAINZ),
            createRelease(songTitle = null, albumTitle = "Target Song", artist = "Artist", source = OnlineSource.ITUNES)
        )

        val result = OnlineSearchSorter.sortReleases(
            releases = releases,
            title = "Target Song",
            artist = "Artist",
            sourcePriority = sourcePriority
        )

        // Release with songTitle matching should score higher
        assertEquals("Target Song", result[0].songTitle)
    }

    @Test
    fun `sortReleases - falls back to albumTitle then title`() {
        val releases = listOf(
            createRelease(songTitle = null, albumTitle = null, title = "Fallback Title", artist = "Artist", source = OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortReleases(
            releases = releases,
            title = "Fallback Title",
            artist = "Artist",
            sourcePriority = sourcePriority
        )

        assertEquals(1, result.size)
        assertEquals("Fallback Title", result[0].title)
    }

    // endregion

    // region sortLyrics tests

    @Test
    fun `sortLyrics - synced lyrics get bonus points`() {
        val lyrics = listOf(
            createLyrics("Song", "Artist", hasSynced = false, source = "MusicBrainz"),
            createLyrics("Song", "Artist", hasSynced = true, source = "MusicBrainz")
        )

        val result = OnlineSearchSorter.sortLyrics(
            lyrics = lyrics,
            title = "Song",
            artist = "Artist",
            sourcePriority = listOf("MusicBrainz")
        )

        // Synced lyrics should rank higher
        assertTrue(result[0].hasSyncedLyrics)
        assertTrue(!result[1].hasSyncedLyrics)
    }

    @Test
    fun `sortLyrics - empty list returns empty`() {
        val result = OnlineSearchSorter.sortLyrics(
            lyrics = emptyList(),
            title = "Test",
            artist = "Artist",
            sourcePriority = sourcePriority
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sortLyrics - source priority works with normalized names`() {
        val lyrics = listOf(
            createLyrics("Song", "Artist", hasSynced = true, source = "NetEase"),
            createLyrics("Song", "Artist", hasSynced = true, source = "QQ Music")
        )

        val result = OnlineSearchSorter.sortLyrics(
            lyrics = lyrics,
            title = "Song",
            artist = "Artist",
            sourcePriority = listOf("qq_music", "netease")
        )

        // QQ Music should rank higher due to priority
        assertEquals("QQ Music", result[0].source)
        assertEquals("NetEase", result[1].source)
    }

    @Test
    fun `sortLyrics - unknown lyrics source gets no priority bonus`() {
        val lyrics = listOf(
            createLyrics("Exact Match", "Artist", hasSynced = true, source = "UnknownSource"),
            createLyrics("Exact Match", "Artist", hasSynced = true, source = "NetEase")
        )

        val result = OnlineSearchSorter.sortLyrics(
            lyrics = lyrics,
            title = "Exact Match",
            artist = "Artist",
            sourcePriority = listOf("netease")
        )

        // Known source should rank higher
        assertEquals("NetEase", result[0].source)
        assertEquals("UnknownSource", result[1].source)
    }

    @Test
    fun `sortLyrics - exact match always outranks partial match regardless of source`() {
        val lyrics = listOf(
            createLyrics("Target S", "Artist", hasSynced = false, source = "NetEase"),
            createLyrics("Target Song", "Artist", hasSynced = false, source = "UnknownSource")
        )

        val result = OnlineSearchSorter.sortLyrics(
            lyrics = lyrics,
            title = "Target Song",
            artist = "Artist",
            sourcePriority = listOf("netease")
        )

        // 字典序分层：相关性档位绝对主导（tier 0 exact > tier 1 prefix），
        // 源优先级只在同档位内生效 —— exact match 即使来自未知源也排在最前
        assertEquals("UnknownSource", result[0].source)
        assertEquals("NetEase", result[1].source)
    }

    // endregion

    // region levenshtein distance tests

    @Test
    fun `levenshtein - similar strings get medium score`() {
        val recordings = listOf(
            createRecording("Hello World", "Artist", OnlineSource.MUSICBRAINZ),
            createRecording("Hellow World", "Artist", OnlineSource.ITUNES)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Hello World",
            artist = "Artist",
            sourcePriority = sourcePriority
        )

        // Exact match should still be first
        assertEquals("Hello World", result[0].title)
    }

    @Test
    fun `levenshtein - completely different strings get low score`() {
        val samePriority = listOf("MusicBrainz")
        val recordings = listOf(
            createRecording("Completely Different", "Artist", OnlineSource.MUSICBRAINZ),
            createRecording("Target Song", "Artist", OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Target Song",
            artist = "Artist",
            sourcePriority = samePriority
        )

        // Target match should be first when source priority is equal
        assertEquals("Target Song", result[0].title)
    }

    @Test
    fun `levenshtein - medium similarity does not exceed exact match`() {
        val recordings = listOf(
            createRecording("Hello World", "Artist", OnlineSource.MUSICBRAINZ),
            createRecording("Hellow World", "Artist", OnlineSource.MUSICBRAINZ)
        )

        val result = OnlineSearchSorter.sortRecordings(
            recordings = recordings,
            title = "Hello World",
            artist = "Artist",
            sourcePriority = listOf("MusicBrainz")
        )

        // Exact match must always rank first, even with same source
        assertEquals("Hello World", result[0].title)
        assertEquals("Hellow World", result[1].title)
    }

    // endregion

    // region Helper methods

    private fun createRecording(
        title: String,
        artist: String,
        source: OnlineSource
    ): OnlineRecording {
        return OnlineRecording(
            id = "id-$title-$source",
            title = title,
            artist = artist,
            duration = 180,
            releaseId = "release-$title",
            source = source
        )
    }

    private fun createRelease(
        songTitle: String?,
        albumTitle: String?,
        artist: String,
        source: OnlineSource,
        title: String = albumTitle ?: songTitle ?: "Unknown"
    ): OnlineRelease {
        return OnlineRelease(
            id = "id-$title-$source",
            title = title,
            artist = artist,
            year = null,
            format = null,
            trackCount = null,
            coverArtUrl = null,
            albumTitle = albumTitle,
            songTitle = songTitle,
            source = source
        )
    }

    private fun createLyrics(
        trackName: String,
        artistName: String,
        hasSynced: Boolean,
        source: String
    ): OnlineLyricsResult {
        return OnlineLyricsResult(
            id = 1L,
            trackName = trackName,
            artistName = artistName,
            albumName = null,
            duration = 180.0,
            hasSyncedLyrics = hasSynced,
            hasPlainLyrics = true,
            isInstrumental = false,
            source = source,
            sourceKey = "key-1",
            preview = null
        )
    }

    // endregion
}
