package com.voxly.data.local.metadata.lightweight

import com.voxly.data.local.metadata.TagLibMetadataProcessor
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for LightweightMetadataParser and its sub-parsers.
 */
class LightweightMetadataParserTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun <T : File> T.track(): T {
        tempFiles.add(this)
        return this
    }

    // ==================== Entry Point (LightweightMetadataParser.parse) ====================

    @Test
    fun `parse routes mp3 to ID3v2Parser`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 3,
            frames = mapOf("TIT2" to "Title")
        ).track()
        val result = LightweightMetadataParser.parse(file)
        assertNotNull(result)
        assertEquals("Title", result?.metadata?.title)
    }

    @Test
    fun `parse routes flac to FlacVorbisCommentParser`() {
        val file = FlacTestDataFactory.createTempFile(
            comments = mapOf("TITLE" to "Flac Title")
        ).track()
        val result = LightweightMetadataParser.parse(file)
        assertNotNull(result)
        assertEquals("Flac Title", result?.metadata?.title)
    }

    @Test
    fun `parse routes m4a to M4aMetadataParser`() {
        val file = M4aTestDataFactory.createTempFile(
            tags = mapOf("©nam" to "M4A Title")
        ).track()
        val result = LightweightMetadataParser.parse(file)
        assertNotNull(result)
        assertEquals("M4A Title", result?.metadata?.title)
    }

    @Test
    fun `parse routes aac to M4aMetadataParser`() {
        val file = M4aTestDataFactory.createTempFile(
            tags = mapOf("©nam" to "AAC Title")
        ).track().apply {
            // Rename to .aac
            val aacFile = File(parent, name.replace(".m4a", ".aac"))
            renameTo(aacFile)
            tempFiles.remove(this)
            tempFiles.add(aacFile)
        }
        val result = LightweightMetadataParser.parse(tempFiles.last())
        assertNotNull(result)
        assertEquals("AAC Title", result?.metadata?.title)
    }

    @Test
    fun `parse routes ogg to OggVorbisCommentParser`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf("TITLE" to "OGG Title")
        ).track()
        val result = LightweightMetadataParser.parse(file)
        assertNotNull(result)
        assertEquals("OGG Title", result?.metadata?.title)
    }

    @Test
    fun `parse routes opus to OggVorbisCommentParser`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf("TITLE" to "Opus Title"),
            isOpus = true
        ).track()
        val result = LightweightMetadataParser.parse(file)
        assertNotNull(result)
        assertEquals("Opus Title", result?.metadata?.title)
    }

    @Test
    fun `parse returns null for unsupported extension`() {
        val file = File.createTempFile("test", ".wav").track()
        file.writeBytes(ByteArray(100))
        val result = LightweightMetadataParser.parse(file)
        assertNull(result)
    }

    // ==================== ID3v2Parser ====================

    @Test
    fun `ID3v2Parser extracts all fields from v2_3`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 3,
            frames = mapOf(
                "TIT2" to "Song Title",
                "TPE1" to "Artist Name",
                "TALB" to "Album Name",
                "TPE2" to "Album Artist",
                "TYER" to "2023",
                "TCON" to "Rock",
                "TRCK" to "5/12",
                "TPOS" to "2/3",
                "TCOM" to "Composer"
            )
        ).track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNotNull(result)
        result!!.metadata.let { m ->
            assertEquals("Song Title", m.title)
            assertEquals("Artist Name", m.artist)
            assertEquals("Album Name", m.album)
            assertEquals("Album Artist", m.albumArtist)
            assertEquals("2023", m.year)
            assertEquals("Rock", m.genre)
            assertEquals(5, m.trackNumber)
            assertEquals(12, m.totalTracks)
            assertEquals(2, m.discNumber)
            assertEquals(3, m.totalDiscs)
            assertEquals("Composer", m.composer)
        }
    }

    @Test
    fun `ID3v2Parser extracts all fields from v2_4`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 4,
            frames = mapOf(
                "TIT2" to "Song Title",
                "TPE1" to "Artist",
                "TALB" to "Album",
                "TDRC" to "2024-05-15"
            )
        ).track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2024", result!!.metadata.year)
    }

    @Test
    fun `ID3v2Parser year falls back from TYER to TDRC to TDRL to TDOR`() {
        // Only TDOR
        val file1 = ID3v2TestDataFactory.createTempFile(
            version = 4,
            frames = mapOf("TPE1" to "Artist", "TDOR" to "2021-01-01")
        ).track()
        val result1 = ID3v2Parser.parse(file1, 128 * 1024)
        assertEquals("2021", result1!!.metadata.year)

        // TDRL + TDOR → TDRL wins
        val file2 = ID3v2TestDataFactory.createTempFile(
            version = 4,
            frames = mapOf("TPE1" to "Artist", "TDRL" to "2022", "TDOR" to "2021")
        ).track()
        val result2 = ID3v2Parser.parse(file2, 128 * 1024)
        assertEquals("2022", result2!!.metadata.year)

        // TDRC + TDRL → TDRC wins
        val file3 = ID3v2TestDataFactory.createTempFile(
            version = 4,
            frames = mapOf("TPE1" to "Artist", "TDRC" to "2023", "TDRL" to "2022")
        ).track()
        val result3 = ID3v2Parser.parse(file3, 128 * 1024)
        assertEquals("2023", result3!!.metadata.year)

        // TYER + TDRC → TYER wins (v2.3)
        val file4 = ID3v2TestDataFactory.createTempFile(
            version = 3,
            frames = mapOf("TPE1" to "Artist", "TYER" to "2020", "TDRC" to "2023")
        ).track()
        val result4 = ID3v2Parser.parse(file4, 128 * 1024)
        assertEquals("2020", result4!!.metadata.year)
    }

    @Test
    fun `ID3v2Parser accepts file with only year`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 4,
            frames = mapOf("TDRC" to "2023")
        ).track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2023", result!!.metadata.year)
    }

    @Test
    fun `ID3v2Parser accepts file with only albumArtist`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 3,
            frames = mapOf("TPE2" to "Various Artists")
        ).track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Various Artists", result!!.metadata.albumArtist)
    }

    @Test
    fun `ID3v2Parser returns null for too short file`() {
        val file = ID3v2TestDataFactory.createTooShortFile().track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `ID3v2Parser returns null for wrong signature`() {
        val file = ID3v2TestDataFactory.createWrongSignatureFile().track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `ID3v2Parser returns null for unsupported version`() {
        val file = ID3v2TestDataFactory.createUnsupportedVersionFile().track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `ID3v2Parser falls back albumArtist to artist`() {
        val file = ID3v2TestDataFactory.createTempFile(
            version = 3,
            frames = mapOf("TPE1" to "Artist Name")
        ).track()
        val result = ID3v2Parser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Artist Name", result!!.metadata.artist)
        assertEquals("Artist Name", result.metadata.albumArtist)
    }

    // ==================== FlacVorbisCommentParser ====================

    @Test
    fun `FlacVorbisCommentParser extracts all fields and audioInfo`() {
        val file = FlacTestDataFactory.createTempFile(
            comments = mapOf(
                "TITLE" to "Flac Title",
                "ARTIST" to "Flac Artist",
                "ALBUM" to "Flac Album",
                "ALBUMARTIST" to "Flac AlbumArtist",
                "DATE" to "2023",
                "GENRE" to "Jazz",
                "TRACKNUMBER" to "3",
                "TRACKTOTAL" to "10",
                "DISCNUMBER" to "1",
                "TOTALDISCS" to "2",
                "COMPOSER" to "Flac Composer"
            ),
            includeStreamInfo = true
        ).track()
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        result!!.metadata.let { m ->
            assertEquals("Flac Title", m.title)
            assertEquals("Flac Artist", m.artist)
            assertEquals("Flac Album", m.album)
            assertEquals("Flac AlbumArtist", m.albumArtist)
            assertEquals("2023", m.year)
            assertEquals("Jazz", m.genre)
            assertEquals(3, m.trackNumber)
            assertEquals(10, m.totalTracks)
            assertEquals(1, m.discNumber)
            assertEquals(2, m.totalDiscs)
            assertEquals("Flac Composer", m.composer)
        }
        assertNotNull(result.audioInfo)
        assertEquals(44100, result.audioInfo!!.sampleRate)
        assertEquals(2, result.audioInfo.channels)
        assertTrue(result.audioInfo.durationMs > 0)
    }

    @Test
    fun `FlacVorbisCommentParser accepts file with only year`() {
        val file = FlacTestDataFactory.createTempFile(
            comments = mapOf("YEAR" to "2022"),
            includeStreamInfo = false
        ).track()
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2022", result!!.metadata.year)
        assertNull(result.audioInfo)
    }

    @Test
    fun `FlacVorbisCommentParser returns null for wrong signature`() {
        val file = FlacTestDataFactory.createWrongSignatureFile().track()
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `FlacVorbisCommentParser returns null when no comments and no streamInfo`() {
        val file = FlacTestDataFactory.createNoCommentFile().track()
        // STREAMINFO alone doesn't have core metadata fields
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `FlacVorbisCommentParser falls back albumArtist to artist`() {
        val file = FlacTestDataFactory.createTempFile(
            comments = mapOf("ARTIST" to "Artist Only"),
            includeStreamInfo = false
        ).track()
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Artist Only", result!!.metadata.artist)
        assertEquals("Artist Only", result.metadata.albumArtist)
    }

    @Test
    fun `FlacVorbisCommentParser year falls back from DATE to YEAR`() {
        val file = FlacTestDataFactory.createTempFile(
            comments = mapOf("YEAR" to "2021"),
            includeStreamInfo = false
        ).track()
        val result = FlacVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2021", result!!.metadata.year)
    }

    // ==================== M4aMetadataParser ====================

    @Test
    fun `M4aMetadataParser extracts all fields`() {
        val file = M4aTestDataFactory.createTempFile(
            tags = mapOf(
                "©nam" to "M4A Title",
                "©ART" to "M4A Artist",
                "©alb" to "M4A Album",
                "aART" to "M4A AlbumArtist",
                "©day" to "2023-06-15",
                "©gen" to "Pop",
                "©wrt" to "M4A Composer"
            )
        ).track()
        val result = M4aMetadataParser.parse(file, 128 * 1024)
        assertNotNull(result)
        result!!.metadata.let { m ->
            assertEquals("M4A Title", m.title)
            assertEquals("M4A Artist", m.artist)
            assertEquals("M4A Album", m.album)
            assertEquals("M4A AlbumArtist", m.albumArtist)
            assertEquals("2023", m.year)
            assertEquals("Pop", m.genre)
            assertEquals("M4A Composer", m.composer)
        }
    }

    @Test
    fun `M4aMetadataParser accepts file with only year`() {
        val file = M4aTestDataFactory.createTempFile(
            tags = mapOf("©day" to "2022")
        ).track()
        val result = M4aMetadataParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2022", result!!.metadata.year)
    }

    @Test
    fun `M4aMetadataParser returns null for missing moov`() {
        val file = M4aTestDataFactory.createMissingMoovFile().track()
        val result = M4aMetadataParser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `M4aMetadataParser falls back albumArtist to artist`() {
        val file = M4aTestDataFactory.createTempFile(
            tags = mapOf("©ART" to "Artist Only")
        ).track()
        val result = M4aMetadataParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Artist Only", result!!.metadata.artist)
        assertEquals("Artist Only", result.metadata.albumArtist)
    }

    // ==================== OggVorbisCommentParser ====================

    @Test
    fun `OggVorbisCommentParser extracts all fields from ogg`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf(
                "TITLE" to "OGG Title",
                "ARTIST" to "OGG Artist",
                "ALBUM" to "OGG Album",
                "ALBUMARTIST" to "OGG AlbumArtist",
                "DATE" to "2023",
                "GENRE" to "Electronic",
                "TRACKNUMBER" to "7",
                "TOTALTRACKS" to "14",
                "DISCNUMBER" to "1",
                "DISCTOTAL" to "2",
                "COMPOSER" to "OGG Composer"
            ),
            isOpus = false
        ).track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        result!!.metadata.let { m ->
            assertEquals("OGG Title", m.title)
            assertEquals("OGG Artist", m.artist)
            assertEquals("OGG Album", m.album)
            assertEquals("OGG AlbumArtist", m.albumArtist)
            assertEquals("2023", m.year)
            assertEquals("Electronic", m.genre)
            assertEquals(7, m.trackNumber)
            assertEquals(14, m.totalTracks)
            assertEquals(1, m.discNumber)
            assertEquals(2, m.totalDiscs)
            assertEquals("OGG Composer", m.composer)
        }
    }

    @Test
    fun `OggVorbisCommentParser extracts all fields from opus`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf(
                "TITLE" to "Opus Title",
                "ARTIST" to "Opus Artist",
                "DATE" to "2024"
            ),
            isOpus = true
        ).track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Opus Title", result!!.metadata.title)
        assertEquals("2024", result.metadata.year)
    }

    @Test
    fun `OggVorbisCommentParser accepts file with only year`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf("YEAR" to "2021"),
            isOpus = false
        ).track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2021", result!!.metadata.year)
    }

    @Test
    fun `OggVorbisCommentParser returns null for wrong signature`() {
        val file = OggTestDataFactory.createWrongSignatureFile().track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNull(result)
    }

    @Test
    fun `OggVorbisCommentParser falls back albumArtist to artist`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf("ARTIST" to "Artist Only"),
            isOpus = false
        ).track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("Artist Only", result!!.metadata.artist)
        assertEquals("Artist Only", result.metadata.albumArtist)
    }

    @Test
    fun `OggVorbisCommentParser year falls back from DATE to YEAR`() {
        val file = OggTestDataFactory.createTempFile(
            comments = mapOf("YEAR" to "2020"),
            isOpus = false
        ).track()
        val result = OggVorbisCommentParser.parse(file, 128 * 1024)
        assertNotNull(result)
        assertEquals("2020", result!!.metadata.year)
    }
}
