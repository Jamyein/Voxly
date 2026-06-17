package com.voxly.domain.model

import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

class AudioFileTest {

    @Ignore("Requires Android runtime")
    @Test
    fun `getFormattedDuration returns correct format for short durations`() {
        val audioFile = createAudioFile(duration = 125000) // 2:05
        assertEquals("2:05", audioFile.getFormattedDuration())
    }

    @Ignore("Requires Android runtime")
    @Test
    fun `getFormattedDuration returns correct format for long durations`() {
        val audioFile = createAudioFile(duration = 3661000) // 1:01:01
        assertEquals("1:01:01", audioFile.getFormattedDuration())
    }

    @Ignore("Requires Android runtime")
    @Test
    fun `getFormattedSize returns correct format for bytes`() {
        val audioFile = createAudioFile(size = 500)
        assertEquals("500 B", audioFile.getFormattedSize())
    }

    @Ignore("Requires Android runtime")
    @Test
    fun `getFormattedSize returns correct format for kilobytes`() {
        val audioFile = createAudioFile(size = 1536000)
        assertTrue(audioFile.getFormattedSize().contains("KB"))
    }

    @Ignore("Requires Android runtime")
    @Test
    fun `getFormattedSize returns correct format for megabytes`() {
        val audioFile = createAudioFile(size = 5242880)
        assertTrue(audioFile.getFormattedSize().contains("MB"))
    }

    @Test
    fun `getFormattedTrackNumber returns correct format with total`() {
        val metadata = AudioMetadata(trackNumber = 3, totalTracks = 12)
        assertEquals("3/12", metadata.getFormattedTrackNumber())
    }

    @Test
    fun `getFormattedTrackNumber returns track number only without total`() {
        val metadata = AudioMetadata(trackNumber = 5)
        assertEquals("5", metadata.getFormattedTrackNumber())
    }

    @Test
    fun `getDisplayTitle returns title when available`() {
        val metadata = AudioMetadata(title = "Test Song")
        assertEquals("Test Song", metadata.getDisplayTitle("filename.mp3"))
    }

    @Test
    fun `getDisplayTitle returns filename without extension when title is empty`() {
        val metadata = AudioMetadata(title = "")
        assertEquals("filename", metadata.getDisplayTitle("filename.mp3"))
    }

    private fun createAudioFile(
        duration: Long = 0,
        size: Long = 0
    ): AudioFile {
        return AudioFile(
            path = "/test/file.mp3",
            name = "file.mp3",
            size = size,
            duration = duration,
            format = com.voxly.domain.model.AudioFormat.MP3,
            bitrate = 320,
            sampleRate = 44100,
            channels = 2,
            metadata = AudioMetadata()
        )
    }
}
