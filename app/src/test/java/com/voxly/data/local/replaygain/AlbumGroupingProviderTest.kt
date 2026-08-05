package com.voxly.data.local.replaygain

import com.voxly.data.local.cache.CachedAudioFileDao
import com.voxly.data.local.cache.CachedAudioFileDao.AlbumPathInfo
import com.voxly.data.local.scanner.AlbumArtistAggregator
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumGroupingProviderTest {
    private val dao = mockk<CachedAudioFileDao>()
    private val aggregator = mockk<AlbumArtistAggregator>()
    private val metadataProcessor = mockk<TagLibMetadataProcessor>()

    private val provider = AlbumGroupingProvider(dao, aggregator, metadataProcessor)

    @Test
    fun `groups by album_artist from Room cache`() = runTest {
        val paths = listOf("/music/a/1.mp3", "/music/a/2.mp3", "/music/b/3.mp3")
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "Album A", "Artist A", "Artist A"),
            AlbumPathInfo("/music/a/2.mp3", "Album A", "Artist A", "Artist A"),
            AlbumPathInfo("/music/b/3.mp3", "Album B", "Artist B", "Artist B")
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf(
                "Album A_Artist A" to listOf("/music/a/1.mp3", "/music/a/2.mp3"),
                "Album B_Artist B" to listOf("/music/b/3.mp3")
            ),
            result
        )
    }

    @Test
    fun `falls back to disk read when cache misses some paths`() = runTest {
        val paths = listOf("/music/a/1.mp3", "/music/a/2.mp3")
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "Album A", "Artist A", "Artist A")
        )
        every { aggregator.albums } returns MutableStateFlow(emptyList<AlbumGroup>())
        coEvery { metadataProcessor.readMetadata("/music/a/2.mp3", false) } returns AudioMetadata(
            album = "Album A",
            albumArtist = "Artist A"
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf("Album A_Artist A" to listOf("/music/a/1.mp3", "/music/a/2.mp3")),
            result
        )
    }

    @Test
    fun `groups compilation album by albumArtist with different track artists`() = runTest {
        val paths = listOf(
            "/music/various/1.mp3",
            "/music/various/2.mp3",
            "/music/various/3.mp3"
        )
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/various/1.mp3", "Compilation Album", "Various Artists", "Various Artists"),
            AlbumPathInfo("/music/various/2.mp3", "Compilation Album", "Various Artists", "Various Artists"),
            AlbumPathInfo("/music/various/3.mp3", "Compilation Album", "Various Artists", "Various Artists")
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf(
                "Compilation Album_Various Artists" to listOf(
                    "/music/various/1.mp3",
                    "/music/various/2.mp3",
                    "/music/various/3.mp3"
                )
            ),
            result
        )
    }

    @Test
    fun `falls back to track artist when albumArtist is null in disk read`() = runTest {
        val paths = listOf("/music/a/1.mp3", "/music/a/2.mp3")
        // Cache has correct albumArtist for file 1
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "Album A", "Artist A", "Artist A")
        )
        every { aggregator.albums } returns MutableStateFlow(emptyList<AlbumGroup>())
        // Disk read for file 2 has null albumArtist — tests fallback to artist
        coEvery { metadataProcessor.readMetadata("/music/a/2.mp3", false) } returns AudioMetadata(
            album = "Album A",
            artist = "Artist A",
            albumArtist = null
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf("Album A_Artist A" to listOf("/music/a/1.mp3", "/music/a/2.mp3")),
            result
        )
    }

    @Test
    fun `puts files with empty album and artist into singleton groups`() = runTest {
        val paths = listOf("/music/a/1.mp3", "/music/b/2.mp3")
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "", "", ""),
            AlbumPathInfo("/music/b/2.mp3", null, null, null)
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(2, result.size)
        assertEquals(listOf("/music/a/1.mp3"), result["singleton_0"])
        assertEquals(listOf("/music/b/2.mp3"), result["singleton_1"])
    }

    @Test
    fun `falls back to aggregator when Room misses some paths`() = runTest {
        val paths = listOf("/music/a/1.mp3", "/music/b/2.mp3")
        // Room only returns the first path
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "Album A", "Artist A", "Artist A")
        )
        // Aggregator has the second path
        val aggregatorGroup = AlbumGroup(
            name = "Album B",
            albumArtist = "Artist B",
            files = persistentListOf(
                mockk<AudioFile> {
                    every { path } returns "/music/b/2.mp3"
                }
            ),
            sortKey = "album b"
        )
        every { aggregator.albums } returns MutableStateFlow(listOf(aggregatorGroup))

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf(
                "Album A_Artist A" to listOf("/music/a/1.mp3"),
                "Album B_Artist B" to listOf("/music/b/2.mp3")
            ),
            result
        )
    }

    @Test
    fun `empty paths returns empty map`() = runTest {
        val result = provider.groupByAlbum(emptyList())
        assertEquals(emptyMap<String, List<String>>(), result)
    }

    @Test
    fun `chunks large path list to avoid SQLite variable limit`() = runTest {
        val paths = (0 until 600).map { "/music/test_$it.mp3" }
        // Each chunk only returns info for the paths it was actually queried with
        coEvery { dao.getAlbumInfoByPaths(any()) } answers {
            val chunkPaths = firstArg<List<String>>()
            chunkPaths.map { AlbumPathInfo(it, "Album X", "Artist X", "Artist X") }
        }

        val result = provider.groupByAlbum(paths)

        assertEquals(
            mapOf("Album X_Artist X" to paths),
            result
        )
    }

    @Test
    fun `room cache falls back to artist when albumArtist is null`() = runTest {
        val paths = listOf("/music/a/1.mp3")
        coEvery { dao.getAlbumInfoByPaths(any()) } returns listOf(
            AlbumPathInfo("/music/a/1.mp3", "Album A", null, "Artist A")
        )

        val result = provider.groupByAlbum(paths)

        assertEquals(mapOf("Album A_Artist A" to listOf("/music/a/1.mp3")), result)
    }
}
