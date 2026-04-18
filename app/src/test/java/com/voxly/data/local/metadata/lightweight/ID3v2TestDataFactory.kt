package com.voxly.data.local.metadata.lightweight

import java.io.File
import java.nio.charset.Charset

/**
 * Test data factory for constructing minimal valid ID3v2.3 / ID3v2.4 tags.
 */
object ID3v2TestDataFactory {

    /**
     * Creates a temporary MP3 file with an ID3v2 tag containing the given frames.
     *
     * @param version 3 for ID3v2.3, 4 for ID3v2.4
     * @param frames Map of frame ID (e.g. "TIT2") to text content
     * @return File handle to the temp file (caller should delete)
     */
    fun createTempFile(version: Int = 3, frames: Map<String, String>): File {
        require(version == 3 || version == 4) { "Only ID3v2.3 and v2.4 supported" }
        val tagBytes = buildTag(version, frames)
        val file = File.createTempFile("test", ".mp3")
        file.writeBytes(tagBytes)
        return file
    }

    private fun buildTag(version: Int, frames: Map<String, String>): ByteArray {
        val frameBytes = frames.flatMap { (id, text) ->
            buildTextFrame(version, id, text).toList()
        }.toByteArray()

        val tagSize = frameBytes.size
        val syncSafeSize = if (version == 4) toSyncSafe(tagSize) else tagSize

        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = version.toByte()
        header[4] = 0 // revision
        header[5] = 0 // flags (no extended header, no unsync, no footer)
        header[6] = (syncSafeSize shr 21 and 0x7F).toByte()
        header[7] = (syncSafeSize shr 14 and 0x7F).toByte()
        header[8] = (syncSafeSize shr 7 and 0x7F).toByte()
        header[9] = (syncSafeSize and 0x7F).toByte()

        return header + frameBytes
    }

    private fun buildTextFrame(version: Int, id: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        // Encoding byte (3 = UTF-8) + text
        val content = ByteArray(1 + textBytes.size)
        content[0] = 3 // UTF-8 encoding
        System.arraycopy(textBytes, 0, content, 1, textBytes.size)

        val frameId = id.toByteArray(Charsets.US_ASCII)
        val size = if (version == 4) {
            toSyncSafe(content.size)
        } else {
            content.size
        }

        val header = ByteArray(10)
        System.arraycopy(frameId, 0, header, 0, 4)
        header[4] = (size shr 24 and 0xFF).toByte()
        header[5] = (size shr 16 and 0xFF).toByte()
        header[6] = (size shr 8 and 0xFF).toByte()
        header[7] = (size and 0xFF).toByte()
        header[8] = 0 // flags
        header[9] = 0 // flags

        return header + content
    }

    private fun toSyncSafe(value: Int): Int {
        return (value and 0x7F) or
                ((value and 0x3F80) shl 1) or
                ((value and 0x1FC000) shl 2) or
                ((value and 0xFE00000) shl 3)
    }

    /** Creates a file that is too short to be valid. */
    fun createTooShortFile(): File {
        val file = File.createTempFile("test", ".mp3")
        file.writeBytes(ByteArray(5))
        return file
    }

    /** Creates a file with wrong signature (not "ID3"). */
    fun createWrongSignatureFile(): File {
        val file = File.createTempFile("test", ".mp3")
        file.writeBytes("NOT3.....".toByteArray(Charsets.US_ASCII))
        return file
    }

    /** Creates a file with unsupported ID3 version (e.g. v2.2). */
    fun createUnsupportedVersionFile(): File {
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 2 // v2.2
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 0
        header[8] = 0
        header[9] = 0
        val file = File.createTempFile("test", ".mp3")
        file.writeBytes(header)
        return file
    }
}
