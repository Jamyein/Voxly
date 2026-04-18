package com.voxly.data.local.metadata.lightweight

import java.io.File
import java.nio.charset.Charset

/**
 * Test data factory for constructing minimal valid OGG and OPUS files.
 */
object OggTestDataFactory {

    /**
     * Creates a temporary OGG file with a Vorbis Comment header.
     *
     * @param comments Map of Vorbis comment keys to values
     * @param isOpus If true, creates an Opus file with OpusTags; otherwise OGG Vorbis
     * @return File handle to the temp file
     */
    fun createTempFile(comments: Map<String, String>, isOpus: Boolean = false): File {
        val ext = if (isOpus) ".opus" else ".ogg"
        val packetData = if (isOpus) {
            buildOpusTagsPacket(comments)
        } else {
            buildVorbisCommentPacket(comments)
        }
        val oggPage = buildOggPage(packetData, isFirstPage = true, isLastPage = false)

        val file = File.createTempFile("test", ext)
        file.writeBytes(oggPage)
        return file
    }

    private fun buildOggPage(
        data: ByteArray,
        isFirstPage: Boolean,
        isLastPage: Boolean
    ): ByteArray {
        val header = ByteArray(27)
        // Capture pattern
        header[0] = 'O'.code.toByte()
        header[1] = 'g'.code.toByte()
        header[2] = 'g'.code.toByte()
        header[3] = 'S'.code.toByte()
        // Version
        header[4] = 0
        // Header type flags
        header[5] = ((if (isFirstPage) 0x02 else 0x00) or (if (isLastPage) 0x04 else 0x00)).toByte()
        // Granule position: 8 bytes
        for (i in 6 until 14) header[i] = 0
        // Bitstream serial number: 4 bytes
        for (i in 14 until 18) header[i] = 0
        // Page sequence number: 4 bytes
        for (i in 18 until 22) header[i] = 0
        // CRC checksum: 4 bytes (zeros for simplicity in tests)
        for (i in 22 until 26) header[i] = 0
        // Number of page segments
        header[26] = 1

        // Segment table: 1 segment
        val segmentTable = ByteArray(1)
        segmentTable[0] = data.size.toByte()

        return header + segmentTable + data
    }

    private fun buildVorbisCommentPacket(comments: Map<String, String>): ByteArray {
        // Packet type + "vorbis"
        val header = byteArrayOf(
            0x03, // packet type
            'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
            'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()
        )
        return header + buildVorbisCommentBody(comments)
    }

    private fun buildOpusTagsPacket(comments: Map<String, String>): ByteArray {
        val header = "OpusTags".toByteArray(Charsets.UTF_8)
        return header + buildVorbisCommentBody(comments)
    }

    private fun buildVorbisCommentBody(comments: Map<String, String>): ByteArray {
        val vendor = "TestVendor".toByteArray(Charsets.UTF_8)
        val vendorLen = ByteArray(4)
        vendorLen[0] = (vendor.size and 0xFF).toByte()
        vendorLen[1] = (vendor.size shr 8 and 0xFF).toByte()
        vendorLen[2] = (vendor.size shr 16 and 0xFF).toByte()
        vendorLen[3] = (vendor.size shr 24 and 0xFF).toByte()

        val commentCount = ByteArray(4)
        val count = comments.size
        commentCount[0] = (count and 0xFF).toByte()
        commentCount[1] = (count shr 8 and 0xFF).toByte()
        commentCount[2] = (count shr 16 and 0xFF).toByte()
        commentCount[3] = (count shr 24 and 0xFF).toByte()

        val commentBytes = comments.map { (k, v) ->
            val text = "$k=$v".toByteArray(Charsets.UTF_8)
            val len = ByteArray(4)
            len[0] = (text.size and 0xFF).toByte()
            len[1] = (text.size shr 8 and 0xFF).toByte()
            len[2] = (text.size shr 16 and 0xFF).toByte()
            len[3] = (text.size shr 24 and 0xFF).toByte()
            len + text
        }.reduceOrNull { a, b -> a + b } ?: ByteArray(0)

        return vendorLen + vendor + commentCount + commentBytes
    }

    /** Creates a file with wrong OggS signature. */
    fun createWrongSignatureFile(): File {
        val file = File.createTempFile("test", ".ogg")
        file.writeBytes("NOTS....".toByteArray(Charsets.US_ASCII))
        return file
    }
}
