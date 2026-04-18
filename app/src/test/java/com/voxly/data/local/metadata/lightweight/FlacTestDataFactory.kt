package com.voxly.data.local.metadata.lightweight

import java.io.File

/**
 * Test data factory for constructing minimal valid FLAC files with Vorbis Comments.
 */
object FlacTestDataFactory {

    /**
     * Creates a temporary FLAC file with STREAMINFO and VORBIS_COMMENT blocks.
     *
     * @param comments Map of Vorbis comment keys (e.g. "TITLE") to values
     * @param includeStreamInfo Whether to include a STREAMINFO block
     * @return File handle to the temp file
     */
    fun createTempFile(
        comments: Map<String, String>,
        includeStreamInfo: Boolean = true
    ): File {
        val blocks = mutableListOf<ByteArray>()

        if (includeStreamInfo) {
            blocks.add(buildStreamInfoBlock(isLast = comments.isEmpty()))
        }
        if (comments.isNotEmpty()) {
            blocks.add(buildVorbisCommentBlock(comments, isLast = true))
        }

        val fLaC = byteArrayOf(
            'f'.code.toByte(), 'L'.code.toByte(),
            'a'.code.toByte(), 'C'.code.toByte()
        )

        val file = File.createTempFile("test", ".flac")
        file.writeBytes(fLaC + blocks.reduce { a, b -> a + b })
        return file
    }

    private fun buildStreamInfoBlock(isLast: Boolean): ByteArray {
        val blockType = 0 // STREAMINFO
        val header = ByteArray(4)
        header[0] = ((if (isLast) 0x80 else 0x00) or blockType).toByte()
        // Block size = 34 bytes
        header[1] = 0
        header[2] = 0
        header[3] = 34

        // STREAMINFO body: 34 bytes
        val body = ByteArray(34)
        // minBlockSize: 2 bytes
        body[0] = 0x10
        body[1] = 0x00
        // maxBlockSize: 2 bytes
        body[2] = 0x10
        body[3] = 0x00
        // minFrameSize: 3 bytes
        body[4] = 0
        body[5] = 0
        body[6] = 0
        // maxFrameSize: 3 bytes
        body[7] = 0
        body[8] = 0
        body[9] = 0
        // sampleRate (20 bits) + channels (3 bits) + bitsPerSample (5 bits) + totalSamples (36 bits)
        // SampleRate = 44100 = 0xAC44
        // Encoded in bytes 10-17 (8 bytes)
        // combined = (sampleRate << 44) | ((channels-1) << 41) | ((bitsPerSample-1) << 36) | totalSamples
        // Let's set sampleRate=44100, channels=2, bitsPerSample=16, totalSamples=44100*60=2646000 (1 minute)
        val sampleRate = 44100L
        val channels = 2
        val bitsPerSample = 16
        val totalSamples = sampleRate * 60L
        val combined = (sampleRate shl 44) or
                ((channels - 1).toLong() shl 41) or
                ((bitsPerSample - 1).toLong() shl 36) or
                totalSamples

        for (i in 0 until 8) {
            body[10 + i] = (combined ushr (56 - i * 8) and 0xFF).toByte()
        }

        // MD5 signature: 16 bytes, leave as zeros

        return header + body
    }

    private fun buildVorbisCommentBlock(comments: Map<String, String>, isLast: Boolean): ByteArray {
        val blockType = 4 // VORBIS_COMMENT

        val vendor = "TestVendor".toByteArray(Charsets.UTF_8)
        val commentBytes = comments.map { (k, v) ->
            val text = "$k=$v".toByteArray(Charsets.UTF_8)
            val len = ByteArray(4)
            len[0] = (text.size and 0xFF).toByte()
            len[1] = (text.size shr 8 and 0xFF).toByte()
            len[2] = (text.size shr 16 and 0xFF).toByte()
            len[3] = (text.size shr 24 and 0xFF).toByte()
            len + text
        }

        val commentCount = ByteArray(4)
        val count = comments.size
        commentCount[0] = (count and 0xFF).toByte()
        commentCount[1] = (count shr 8 and 0xFF).toByte()
        commentCount[2] = (count shr 16 and 0xFF).toByte()
        commentCount[3] = (count shr 24 and 0xFF).toByte()

        val vendorLen = ByteArray(4)
        vendorLen[0] = (vendor.size and 0xFF).toByte()
        vendorLen[1] = (vendor.size shr 8 and 0xFF).toByte()
        vendorLen[2] = (vendor.size shr 16 and 0xFF).toByte()
        vendorLen[3] = (vendor.size shr 24 and 0xFF).toByte()

        val body = vendorLen + vendor + commentCount + commentBytes.reduce { a, b -> a + b }

        val header = ByteArray(4)
        header[0] = ((if (isLast) 0x80 else 0x00) or blockType).toByte()
        val size = body.size
        header[1] = (size shr 16 and 0xFF).toByte()
        header[2] = (size shr 8 and 0xFF).toByte()
        header[3] = (size and 0xFF).toByte()

        return header + body
    }

    /** Creates a file with wrong fLaC signature. */
    fun createWrongSignatureFile(): File {
        val file = File.createTempFile("test", ".flac")
        file.writeBytes("NOTC....".toByteArray(Charsets.US_ASCII))
        return file
    }

    /** Creates a minimal valid FLAC with no comment block. */
    fun createNoCommentFile(): File {
        return createTempFile(emptyMap(), includeStreamInfo = true)
    }
}
