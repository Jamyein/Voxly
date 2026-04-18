package com.voxly.data.local.metadata.lightweight

import java.io.File
import java.nio.charset.Charset

/**
 * Test data factory for constructing minimal valid M4A files with iTunes-style metadata.
 */
object M4aTestDataFactory {

    data class NumericTag(val trackNumber: Int, val totalTracks: Int?, val discNumber: Int?, val totalDiscs: Int?)

    /**
     * Creates a temporary M4A file with moov/udta/meta/ilst hierarchy.
     *
     * @param tags Map of atom types (e.g. "©nam") to text values
     * @param numericTag Optional numeric tag for track/disc numbers
     * @return File handle to the temp file
     */
    fun createTempFile(tags: Map<String, String>, numericTag: NumericTag? = null): File {
        val ilst = buildIlstBox(tags, numericTag)
        val meta = buildMetaBox(ilst)
        val udta = buildUdtaBox(meta)
        val moov = buildMoovBox(udta)

        val file = File.createTempFile("test", ".m4a")
        file.writeBytes(moov)
        return file
    }

    private fun buildBox(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        val size = 8 + data.size
        val sizeBytes = ByteArray(4)
        sizeBytes[0] = (size shr 24 and 0xFF).toByte()
        sizeBytes[1] = (size shr 16 and 0xFF).toByte()
        sizeBytes[2] = (size shr 8 and 0xFF).toByte()
        sizeBytes[3] = (size and 0xFF).toByte()
        return sizeBytes + typeBytes + data
    }

    private fun buildIlstBox(tags: Map<String, String>, numericTag: NumericTag?): ByteArray {
        val items = mutableListOf<ByteArray>()
        tags.forEach { (type, text) ->
            val dataBox = buildDataBox(text)
            items.add(buildBox(type, dataBox))
        }
        numericTag?.let { nt ->
            nt.trackNumber.let { trk ->
                items.add(buildNumericBox("trkn", trk, nt.totalTracks))
            }
            nt.discNumber?.let { disk ->
                items.add(buildNumericBox("disk", disk, nt.totalDiscs))
            }
        }
        val combined = items.reduceOrNull { a, b -> a + b } ?: ByteArray(0)
        return buildBox("ilst", combined)
    }

    private fun buildNumericBox(type: String, value: Int, total: Int?): ByteArray {
        val dataContent = ByteArray(20)
        dataContent[16] = (value shr 8 and 0xFF).toByte()
        dataContent[17] = (value and 0xFF).toByte()
        total?.let {
            dataContent[18] = (it shr 8 and 0xFF).toByte()
            dataContent[19] = (it and 0xFF).toByte()
        }
        val dataBox = buildBox("data", dataContent)
        return buildBox(type, dataBox)
    }

    private fun buildDataBox(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        // data atom: 8 bytes header + 4 bytes version/flags + 4 bytes reserved + text
        val versionFlags = ByteArray(4) // all zeros
        val reserved = ByteArray(4) // all zeros
        val content = versionFlags + reserved + textBytes
        return buildBox("data", content)
    }

    private fun buildMetaBox(ilstData: ByteArray): ByteArray {
        // meta box has a 4-byte version/flags header after the 8-byte box header
        val versionFlags = ByteArray(4)
        return versionFlags + ilstData
    }

    private fun buildUdtaBox(metaData: ByteArray): ByteArray {
        val metaBox = buildBox("meta", metaData)
        return buildBox("udta", metaBox)
    }

    private fun buildMoovBox(udtaData: ByteArray): ByteArray {
        return buildBox("moov", udtaData)
    }

    /** Creates a file missing the moov box. */
    fun createMissingMoovFile(): File {
        val file = File.createTempFile("test", ".m4a")
        file.writeBytes("randomdata".toByteArray(Charsets.US_ASCII))
        return file
    }
}
