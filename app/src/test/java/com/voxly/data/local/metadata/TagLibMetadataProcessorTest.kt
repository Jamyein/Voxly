package com.voxly.data.local.metadata

import android.content.Context
import com.voxly.domain.model.AudioMetadata
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for TagLibMetadataProcessor.
 */
class TagLibMetadataProcessorTest {

    private lateinit var context: Context
    private lateinit var processor: TagLibMetadataProcessor

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = mockk()
        every { context.applicationContext } returns context
        processor = TagLibMetadataProcessor(context)
    }

    @Test
    fun `isFormatSupported returns true for supported formats`() = runBlocking {
        // Note: This test requires actual file system access
        // In real implementation, you'd mock the file checks
        val supportedExtensions = listOf("mp3", "flac", "ogg", "m4a", "wma", "wav", "ape", "opus", "wv")
        
        supportedExtensions.forEach { ext ->
            val isSupported = processor.isFormatSupported("test.$ext")
            assertTrue("Extension $ext should be supported", isSupported)
        }
    }

    @Test
    fun `isFormatSupported returns false for unsupported formats`() = runBlocking {
        val unsupportedExtensions = listOf("txt", "pdf", "jpg", "png")
        
        unsupportedExtensions.forEach { ext ->
            val isSupported = processor.isFormatSupported("test.$ext")
            assertFalse("Extension $ext should not be supported", isSupported)
        }
    }

    @Test
    fun `readMetadata returns null for non-existent file`() = runBlocking {
        val result = processor.readMetadata("/non/existent/file.mp3")
        assertNull(result)
    }

    @Test
    fun `extractAlbumArt returns null for file without album art`() = runBlocking {
        // This test requires a real audio file without album art
        // In production, you'd create a test file or mock the jaudiotagger calls
    }
}
