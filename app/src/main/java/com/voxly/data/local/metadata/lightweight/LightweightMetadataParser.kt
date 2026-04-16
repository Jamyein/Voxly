package com.voxly.data.local.metadata.lightweight

import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioMetadata
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Lightweight metadata parser for MP3 (ID3v2.3/2.4) and FLAC.
 *
 * Only reads the first portion of the file (typically < 128 KB) to extract
 * common text tags, bypassing the full TagLib JNI round-trip for the hot path.
 */
object LightweightMetadataParser {
    private const val TAG = "LightweightMetadataParser"
    private const val DEFAULT_READ_LIMIT = 128 * 1024

    data class Result(
        val metadata: AudioMetadata,
        val audioInfo: TagLibMetadataProcessor.AudioInfo? = null
    )

    /**
     * Attempts to parse [file] using a lightweight native-Kotlin parser.
     * Returns null if the format is unsupported or parsing yields insufficient data,
     * in which case the caller should fall back to TagLib.
     */
    fun parse(file: File, readLimit: Int = DEFAULT_READ_LIMIT): Result? {
        return try {
            when (file.extension.lowercase()) {
                "mp3" -> ID3v2Parser.parse(file, readLimit)
                "flac" -> FlacVorbisCommentParser.parse(file, readLimit)
                "m4a", "aac" -> M4aMetadataParser.parse(file, readLimit)
                else -> null
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Lightweight parse failed for ${file.path}", e)
            null
        }
    }
}

/**
 * ID3v2.3 / ID3v2.4 text-frame parser.
 * Reads only the tag portion at the start of the file.
 */
internal object ID3v2Parser {
    private const val TAG = "ID3v2Parser"

    fun parse(file: File, readLimit: Int): LightweightMetadataParser.Result? {
        val bytes = readHead(file, readLimit)
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }

        val majorVersion = bytes[3].toInt() and 0xFF // 3 = v2.3, 4 = v2.4
        if (majorVersion != 3 && majorVersion != 4) return null

        val flags = bytes[5].toInt() and 0xFF
        val hasExtendedHeader = (flags and 0x40) != 0
        val tagSize = readSyncSafeInt(bytes, 6)
        if (tagSize <= 0 || tagSize > bytes.size - 10) return null

        val frames = mutableMapOf<String, String>()
        var offset = 10

        if (hasExtendedHeader) {
            // Skip extended header (size is a syncsafe int at offset 10)
            val extSize = if (majorVersion == 4) readSyncSafeInt(bytes, offset) else readInt32BE(bytes, offset)
            offset += 4 + extSize
        }

        val end = 10 + tagSize
        while (offset + 10 <= end) {
            val frameId = bytes.decodeAscii(offset, 4)
            if (frameId.isBlank() || frameId.any { it.code < 0x20 || it.code > 0x7A }) {
                break // padding reached
            }
            val frameSize = if (majorVersion == 4) readSyncSafeInt(bytes, offset + 4) else readInt32BE(bytes, offset + 4)
            if (frameSize < 0 || offset + 10 + frameSize > bytes.size) break
            val frameData = bytes.copyOfRange(offset + 10, offset + 10 + frameSize)
            if (frameId.startsWith("T")) {
                parseTextFrame(frameData)?.let { frames[frameId] = it }
            }
            offset += 10 + frameSize
        }

        val metadata = AudioMetadata(
            title = frames["TIT2"],
            artist = frames["TPE1"],
            album = frames["TALB"],
            albumArtist = frames["TPE2"] ?: frames["TPE1"],
            year = frames["TYER"] ?: frames["TDRC"]?.take(4),
            genre = frames["TCON"],
            trackNumber = parseTrackDisc(frames["TRCK"])?.first,
            totalTracks = parseTrackDisc(frames["TRCK"])?.second,
            discNumber = parseTrackDisc(frames["TPOS"])?.first,
            totalDiscs = parseTrackDisc(frames["TPOS"])?.second,
            composer = frames["TCOM"]
        )

        // Consider parse successful if we got at least one core field
        val hasCoreData = !metadata.title.isNullOrBlank() ||
                !metadata.artist.isNullOrBlank() ||
                !metadata.album.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata) else null
    }

    private fun readHead(file: File, limit: Int): ByteArray {
        return FileInputStream(file).use { fis ->
            val buf = ByteArray(limit.coerceAtMost(file.length().toInt().coerceAtLeast(limit)))
            val read = fis.read(buf)
            if (read < buf.size) buf.copyOf(read) else buf
        }
    }

    private fun readSyncSafeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
                ((data[offset + 1].toInt() and 0x7F) shl 14) or
                ((data[offset + 2].toInt() and 0x7F) shl 7) or
                (data[offset + 3].toInt() and 0x7F)
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String {
        return String(this, offset, length, Charsets.ISO_8859_1)
    }

    private fun parseTextFrame(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt() and 0xFF
        val textBytes = data.copyOfRange(1, data.size)
        val string = when (encoding) {
            0 -> String(textBytes, Charsets.ISO_8859_1)
            1 -> decodeUtf16(textBytes)
            2 -> String(textBytes, Charsets.UTF_16BE)
            3 -> String(textBytes, Charsets.UTF_8)
            else -> String(textBytes, Charsets.ISO_8859_1)
        }
        // Some frames contain multiple null-separated values; take the first
        return string.trim().replace("\u0000", " ").trim().takeIf { it.isNotBlank() }
    }

    private fun decodeUtf16(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val bom = (bytes[0].toInt() and 0xFF) to (bytes[1].toInt() and 0xFF)
            if (bom == 0xFF to 0xFE) {
                return String(bytes, Charsets.UTF_16LE)
            } else if (bom == 0xFE to 0xFF) {
                return String(bytes, Charsets.UTF_16BE)
            }
        }
        return String(bytes, Charsets.UTF_16)
    }

    private fun parseTrackDisc(value: String?): Pair<Int?, Int?>? {
        val v = value?.trim() ?: return null
        val parts = v.split('/')
        val first = parts.getOrNull(0)?.toIntOrNull()
        val second = parts.getOrNull(1)?.toIntOrNull()
        return if (first != null) Pair(first, second) else null
    }
}

/**
 * Lightweight FLAC metadata parser.
 * Reads STREAMINFO for audio properties and VORBIS_COMMENT for tags.
 */
internal object FlacVorbisCommentParser {
    private const val TAG = "FlacVorbisCommentParser"

    fun parse(file: File, readLimit: Int): LightweightMetadataParser.Result? {
        val bytes = readHead(file, readLimit)
        if (bytes.size < 8) return null
        if (bytes[0] != 'f'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'a'.code.toByte() || bytes[3] != 'C'.code.toByte()) {
            return null
        }

        var offset = 4
        val comments = mutableMapOf<String, String>()
        var streamInfo: TagLibMetadataProcessor.AudioInfo? = null

        while (offset + 4 < bytes.size) {
            val blockHeader = bytes[offset].toInt() and 0xFF
            val isLast = (blockHeader and 0x80) != 0
            val blockType = blockHeader and 0x7F
            val blockSize = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)
            offset += 4

            if (offset + blockSize > bytes.size) break

            when (blockType) {
                0 -> streamInfo = parseStreamInfo(bytes, offset)
                4 -> parseVorbisComment(bytes, offset, blockSize, comments)
            }

            offset += blockSize
            if (isLast) break
        }

        val metadata = AudioMetadata(
            title = comments["TITLE"],
            artist = comments["ARTIST"],
            album = comments["ALBUM"],
            albumArtist = comments["ALBUMARTIST"] ?: comments["ARTIST"],
            year = comments["DATE"] ?: comments["YEAR"],
            genre = comments["GENRE"],
            trackNumber = comments["TRACKNUMBER"]?.toIntOrNull(),
            totalTracks = comments["TRACKTOTAL"]?.toIntOrNull() ?: comments["TOTALTRACKS"]?.toIntOrNull(),
            discNumber = comments["DISCNUMBER"]?.toIntOrNull(),
            totalDiscs = comments["DISCTOTAL"]?.toIntOrNull() ?: comments["TOTALDISCS"]?.toIntOrNull(),
            composer = comments["COMPOSER"]
        )

        val hasCoreData = !metadata.title.isNullOrBlank() ||
                !metadata.artist.isNullOrBlank() ||
                !metadata.album.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata, streamInfo) else null
    }

    private fun readHead(file: File, limit: Int): ByteArray {
        return FileInputStream(file).use { fis ->
            val toRead = limit.coerceAtMost(file.length().toInt().coerceAtLeast(limit))
            val buf = ByteArray(toRead)
            val read = fis.read(buf)
            if (read < buf.size) buf.copyOf(read) else buf
        }
    }

    private fun parseStreamInfo(bytes: ByteArray, offset: Int): TagLibMetadataProcessor.AudioInfo? {
        // STREAMINFO is exactly 34 bytes
        if (offset + 34 > bytes.size) return null
        // Combined field: 8 bytes big-endian
        val b0 = bytes[offset + 10].toLong() and 0xFF
        val b1 = bytes[offset + 11].toLong() and 0xFF
        val b2 = bytes[offset + 12].toLong() and 0xFF
        val b3 = bytes[offset + 13].toLong() and 0xFF
        val b4 = bytes[offset + 14].toLong() and 0xFF
        val b5 = bytes[offset + 15].toLong() and 0xFF
        val b6 = bytes[offset + 16].toLong() and 0xFF
        val b7 = bytes[offset + 17].toLong() and 0xFF
        val combined = (b0 shl 56) or (b1 shl 48) or (b2 shl 40) or (b3 shl 32) or
                (b4 shl 24) or (b5 shl 16) or (b6 shl 8) or b7

val sampleRate = ((combined ushr 44) and 0xFFFFF).toInt()
        val channels = ((combined ushr 41) and 0x7).toInt() + 1
        val bitsPerSample = ((combined ushr 36) and 0x1F).toInt() + 1
        val totalSamples = (combined and 0xFFFFFFFFFL).toLong()

        if (sampleRate <= 0) return null
        val durationMs = (totalSamples * 1000L) / sampleRate

        return TagLibMetadataProcessor.AudioInfo(
            bitrate = 0, // Will be filled by MediaStore or TagLib fallback
            sampleRate = sampleRate,
            channels = channels,
            durationMs = durationMs
        )
    }

    private fun parseVorbisComment(
        bytes: ByteArray,
        offset: Int,
        blockSize: Int,
        out: MutableMap<String, String>
    ) {
        var pos = offset
        val end = offset + blockSize
        if (pos + 4 > end) return
        val vendorLen = readUInt32LE(bytes, pos)
        pos += 4 + vendorLen.toInt()
        if (pos + 4 > end) return
        val commentCount = readUInt32LE(bytes, pos)
        pos += 4

        repeat(commentCount.coerceAtMost(100).toInt()) {
            if (pos + 4 > end) return
            val commentLen = readUInt32LE(bytes, pos)
            pos += 4
            if (pos + commentLen.toInt() > end) return
            val comment = String(bytes, pos, commentLen.toInt(), Charsets.UTF_8)
            pos += commentLen.toInt()
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                val value = comment.substring(eq + 1)
                out[key] = value.trim()
            }
        }
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}

/**
 * Lightweight M4A / AAC metadata parser.
 * Reads iTunes-style text tags (©day, ©nam, ©ART, etc.) from the moov/udta/meta/ilst atom hierarchy.
 */
internal object M4aMetadataParser {
    private const val TAG = "M4aMetadataParser"
    private val TEXT_ATOMS = setOf("©nam", "©ART", "©alb", "©day", "©gen", "©wrt", "aART")

    fun parse(file: File, readLimit: Int): LightweightMetadataParser.Result? {
        val bytes = readHead(file, readLimit)
        if (bytes.size < 8) return null

        val ilstOffset = findIlstBox(bytes) ?: return null
        val tags = parseIlst(bytes, ilstOffset)

        val metadata = AudioMetadata(
            title = tags["©nam"],
            artist = tags["©ART"],
            album = tags["©alb"],
            albumArtist = tags["aART"] ?: tags["©ART"],
            year = tags["©day"]?.take(4),
            genre = tags["©gen"],
            composer = tags["©wrt"]
        )

        val hasCoreData = !metadata.title.isNullOrBlank() ||
                !metadata.artist.isNullOrBlank() ||
                !metadata.album.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata) else null
    }

    private fun readHead(file: File, limit: Int): ByteArray {
        return FileInputStream(file).use { fis ->
            val toRead = limit.coerceAtMost(file.length().toInt().coerceAtLeast(limit))
            val buf = ByteArray(toRead)
            val read = fis.read(buf)
            if (read < buf.size) buf.copyOf(read) else buf
        }
    }

    private fun findIlstBox(bytes: ByteArray): Int? {
        val moovOffset = findBox(bytes, 0, bytes.size, "moov") ?: return null
        val udtaOffset = findBox(bytes, moovOffset + 8, moovOffset + boxSize(bytes, moovOffset), "udta") ?: return null
        val metaOffset = findBox(bytes, udtaOffset + 8, udtaOffset + boxSize(bytes, udtaOffset), "meta") ?: return null
        // meta box has a 4-byte version/flags header after the 8-byte box header
        return findBox(bytes, metaOffset + 12, metaOffset + boxSize(bytes, metaOffset), "ilst")
    }

    private fun boxSize(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return readInt32BE(bytes, offset)
    }

    private fun findBox(bytes: ByteArray, start: Int, end: Int, type: String): Int? {
        var offset = start.coerceAtLeast(0)
        val limit = end.coerceAtMost(bytes.size)
        while (offset + 8 <= limit) {
            val size = readInt32BE(bytes, offset)
            val boxType = bytes.decodeAscii(offset + 4, 4)
            if (boxType == type) return offset
            if (size <= 0 || offset + size > limit) break
            offset += size
        }
        return null
    }

    private fun parseIlst(bytes: ByteArray, ilstOffset: Int): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        val ilstSize = boxSize(bytes, ilstOffset)
        var offset = ilstOffset + 8
        val end = (ilstOffset + ilstSize).coerceAtMost(bytes.size)

        while (offset + 8 <= end) {
            val itemSize = readInt32BE(bytes, offset)
            val itemType = bytes.decodeAscii(offset + 4, 4)
            if (itemSize <= 0 || offset + itemSize > end) break

            if (itemType in TEXT_ATOMS) {
                val dataOffset = findBox(bytes, offset + 8, offset + itemSize, "data")
                if (dataOffset != null) {
                    val dataSize = boxSize(bytes, dataOffset)
                    // data atom: 8 bytes header + 4 bytes version/flags + 4 bytes reserved + text
                    val textStart = dataOffset + 16
                    val textEnd = (dataOffset + dataSize).coerceAtMost(bytes.size)
                    if (textStart < textEnd) {
                        val text = String(bytes, textStart, textEnd - textStart, Charsets.UTF_8)
                            .trim()
                            .replace("\u0000", " ")
                            .trim()
                        if (text.isNotBlank()) {
                            tags[itemType] = text
                        }
                    }
                }
            }
            offset += itemSize
        }
        return tags
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String {
        return String(this, offset, length, Charsets.ISO_8859_1)
    }
}
