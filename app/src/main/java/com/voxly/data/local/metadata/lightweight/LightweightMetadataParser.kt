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
                "ogg", "oga", "opus" -> OggVorbisCommentParser.parse(file, readLimit)
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
        val frameHeaderSize = 10 // 4 ID + 4 size + 2 flags
        while (offset + frameHeaderSize <= end) {
            val frameId = bytes.decodeAscii(offset, 4)
            if (frameId.isBlank() || frameId.any { it.code < 0x20 || it.code > 0x7A }) {
                break // padding reached
            }
            val frameSize = if (majorVersion == 4) readSyncSafeInt(bytes, offset + 4) else readInt32BE(bytes, offset + 4)
            if (frameSize < 0 || offset + frameHeaderSize + frameSize > bytes.size) break
            // Skip 10-byte header + 2-byte flags = 12 bytes before text content
            val frameData = bytes.copyOfRange(offset + frameHeaderSize, offset + frameHeaderSize + frameSize)
            if (frameId.startsWith("T")) {
                parseTextFrame(frameData)?.let { frames[frameId] = it }
            }
            offset += frameHeaderSize + frameSize
        }

        val metadata = AudioMetadata(
            title = frames["TIT2"],
            artist = frames["TPE1"],
            album = frames["TALB"],
            albumArtist = frames["TPE2"] ?: frames["TPE1"],
            year = frames["TYER"] ?: frames["TDRC"]?.take(4) ?: frames["TDRL"]?.take(4) ?: frames["TDOR"]?.take(4),
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
                !metadata.album.isNullOrBlank() ||
                !metadata.year.isNullOrBlank() ||
                !metadata.albumArtist.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata) else null
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
                !metadata.album.isNullOrBlank() ||
                !metadata.year.isNullOrBlank() ||
                !metadata.albumArtist.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata, streamInfo) else null
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
        val totalSamples = combined and 0xFFFFFFFFFL

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

        repeat(commentCount.coerceAtMost(10000).toInt()) {
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
    private val NUMERIC_ATOMS = setOf("trkn", "disk")

    fun parse(file: File, readLimit: Int): LightweightMetadataParser.Result? {
        val bytes = readHead(file, readLimit)
        if (bytes.size < 8) return null

        val ilstOffset = findIlstBox(bytes) ?: return null
        val (textTags, numericTags) = parseIlst(bytes, ilstOffset)

        val metadata = AudioMetadata(
            title = textTags["©nam"],
            artist = textTags["©ART"],
            album = textTags["©alb"],
            albumArtist = textTags["aART"] ?: textTags["©ART"],
            year = textTags["©day"]?.take(4),
            genre = textTags["©gen"],
            composer = textTags["©wrt"],
            trackNumber = numericTags["trkn"]?.first,
            totalTracks = numericTags["trkn"]?.second,
            discNumber = numericTags["disk"]?.first,
            totalDiscs = numericTags["disk"]?.second
        )

        val hasCoreData = !metadata.title.isNullOrBlank() ||
                !metadata.artist.isNullOrBlank() ||
                !metadata.album.isNullOrBlank() ||
                !metadata.year.isNullOrBlank() ||
                !metadata.albumArtist.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata) else null
    }

    private fun findIlstBox(bytes: ByteArray): Int? {
        val moovOffset = findBox(bytes, 0, bytes.size, "moov") ?: return null
        val udtaOffset = findBox(bytes, moovOffset + 8, moovOffset + boxSize(bytes, moovOffset), "udta") ?: return null
        val metaOffset = findBox(bytes, udtaOffset + 8, udtaOffset + boxSize(bytes, udtaOffset), "meta") ?: return null
        val metaContentEnd = metaOffset + boxSize(bytes, metaOffset) - 8
        return findBox(bytes, metaOffset + 12, metaContentEnd, "ilst")
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

    private fun parseIlst(bytes: ByteArray, ilstOffset: Int): Pair<Map<String, String>, Map<String, Pair<Int?, Int?>>> {
        val textTags = mutableMapOf<String, String>()
        val numericTags = mutableMapOf<String, Pair<Int?, Int?>>()
        val ilstSize = boxSize(bytes, ilstOffset)
        var offset = ilstOffset + 8
        val end = (ilstOffset + ilstSize).coerceAtMost(bytes.size)

        while (offset + 8 <= end) {
            val itemSize = readInt32BE(bytes, offset)
            val itemType = bytes.decodeAscii(offset + 4, 4)
            if (itemSize <= 0 || offset + itemSize > end) break

            when {
                itemType in TEXT_ATOMS -> {
                    val dataOffset = findBox(bytes, offset + 8, offset + itemSize, "data")
                    if (dataOffset != null) {
                        val dataSize = boxSize(bytes, dataOffset)
                        val textStart = dataOffset + 16
                        val textEnd = (dataOffset + dataSize).coerceAtMost(bytes.size)
                        if (textStart < textEnd) {
                            val charset = when {
                                dataOffset + 15 < bytes.size -> {
                                    val dataType = bytes[dataOffset + 13].toInt() and 0xFF
                                    when (dataType) {
                                        0x02 -> Charsets.UTF_16
                                        else -> Charsets.UTF_8
                                    }
                                }
                                else -> Charsets.UTF_8
                            }
                            val text = String(bytes, textStart, textEnd - textStart, charset)
                                .trim()
                                .replace("\u0000", " ")
                                .trim()
                            if (text.isNotBlank()) {
                                textTags[itemType] = text
                            }
                        }
                    }
                }
                itemType in NUMERIC_ATOMS -> {
                    val dataOffset = findBox(bytes, offset + 8, offset + itemSize, "data")
                    if (dataOffset != null) {
                        val pair = parseNumericAtom(bytes, dataOffset)
                        if (pair != null) {
                            numericTags[itemType] = pair
                        }
                    }
                }
            }
            offset += itemSize
        }
        return Pair(textTags, numericTags)
    }

    private fun parseNumericAtom(bytes: ByteArray, dataOffset: Int): Pair<Int?, Int?>? {
        if (dataOffset + 28 > bytes.size) return null
        val dataSize = boxSize(bytes, dataOffset)
        if (dataSize < 28) return null
        val value1 = readUInt16BE(bytes, dataOffset + 24)
        val value2 = readUInt16BE(bytes, dataOffset + 26)
        return if (value1 != 0) Pair(value1, if (value2 != 0) value2 else null) else null
    }

    private fun readUInt16BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
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

/**
 * Lightweight OGG/OPUS Vorbis Comment parser.
 * Reads OGG pages to locate the Vorbis Comment packet.
 */
internal object OggVorbisCommentParser {
    private const val TAG = "OggVorbisCommentParser"

    fun parse(file: File, readLimit: Int): LightweightMetadataParser.Result? {
        val bytes = readHead(file, readLimit)
        if (bytes.size < 27) return null
        if (bytes[0] != 'O'.code.toByte() || bytes[1] != 'g'.code.toByte() ||
            bytes[2] != 'g'.code.toByte() || bytes[3] != 'S'.code.toByte()) {
            return null
        }

        val comments = mutableMapOf<String, String>()
        var offset = 0
        var pageCount = 0
        var foundComment = false
        var pendingPacket: ByteArray? = null

        while (offset + 27 <= bytes.size && pageCount < 10) {
            if (bytes[offset] != 'O'.code.toByte() || bytes[offset + 1] != 'g'.code.toByte() ||
                bytes[offset + 2] != 'g'.code.toByte() || bytes[offset + 3] != 'S'.code.toByte()) {
                break
            }

            val pageSegments = bytes[offset + 26].toInt() and 0xFF
            if (offset + 27 + pageSegments > bytes.size) break

            val segmentTable = bytes.copyOfRange(offset + 27, offset + 27 + pageSegments)
            val segmentSizes = segmentTable.map { it.toInt() and 0xFF }
            val pageDataSize = segmentSizes.sum()
            val headerSize = 27 + pageSegments

            if (offset + headerSize + pageDataSize > bytes.size) break

            val pageData = bytes.copyOfRange(offset + headerSize, offset + headerSize + pageDataSize)

            // Handle continued packet: if previous page ended with segment of 255, append to pending
            val continuedPacket = pendingPacket
            pendingPacket = null

            // Check if packet continues to next page (last segment == 255)
            val lastSegmentSize = segmentSizes.lastOrNull() ?: 0
            if (lastSegmentSize == 255) {
                // Packet continues on next page; accumulate data
                pendingPacket = if (continuedPacket != null) {
                    continuedPacket + pageData
                } else {
                    pageData
                }
            }

            // Only process if we have a complete packet (not pending) and haven't found comment
            if (!foundComment && continuedPacket == null) {
                if (pageData.size >= 7 && pageData[0] == 0x03.toByte() &&
                    pageData[1] == 'v'.code.toByte() && pageData[2] == 'o'.code.toByte() &&
                    pageData[3] == 'r'.code.toByte() && pageData[4] == 'b'.code.toByte() &&
                    pageData[5] == 'i'.code.toByte() && pageData[6] == 's'.code.toByte()) {
                    parseVorbisComment(pageData, 7, comments)
                    foundComment = true
                } else if (pageData.size >= 8 && pageData[0] == 'O'.code.toByte() &&
                    pageData[1] == 'p'.code.toByte() && pageData[2] == 'u'.code.toByte() &&
                    pageData[3] == 's'.code.toByte() && pageData[4] == 'T'.code.toByte() &&
                    pageData[5] == 'a'.code.toByte() && pageData[6] == 'g'.code.toByte() &&
                    pageData[7] == 's'.code.toByte()) {
                    parseOpusTags(pageData, 8, comments)
                    foundComment = true
                }
            } else if (!foundComment && continuedPacket != null) {
                // Check the accumulated packet for comment header
                if (continuedPacket.size >= 7 && continuedPacket[0] == 0x03.toByte() &&
                    continuedPacket[1] == 'v'.code.toByte() && continuedPacket[2] == 'o'.code.toByte() &&
                    continuedPacket[3] == 'r'.code.toByte() && continuedPacket[4] == 'b'.code.toByte() &&
                    continuedPacket[5] == 'i'.code.toByte() && continuedPacket[6] == 's'.code.toByte()) {
                    parseVorbisComment(continuedPacket, 7, comments)
                    foundComment = true
                } else if (continuedPacket.size >= 8 && continuedPacket[0] == 'O'.code.toByte() &&
                    continuedPacket[1] == 'p'.code.toByte() && continuedPacket[2] == 'u'.code.toByte() &&
                    continuedPacket[3] == 's'.code.toByte() && continuedPacket[4] == 'T'.code.toByte() &&
                    continuedPacket[5] == 'a'.code.toByte() && continuedPacket[6] == 'g'.code.toByte() &&
                    continuedPacket[7] == 's'.code.toByte()) {
                    parseOpusTags(continuedPacket, 8, comments)
                    foundComment = true
                }
            }

            offset += headerSize + pageDataSize
            pageCount++
        }

        if (!foundComment) return null

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
                !metadata.album.isNullOrBlank() ||
                !metadata.year.isNullOrBlank() ||
                !metadata.albumArtist.isNullOrBlank()
        return if (hasCoreData) LightweightMetadataParser.Result(metadata) else null
    }

    private fun parseVorbisComment(data: ByteArray, offset: Int, out: MutableMap<String, String>) {
        if (offset + 4 > data.size) return
        val vendorLen = readUInt32LE(data, offset)
        var pos = offset + 4 + vendorLen.toInt()
        if (pos + 4 > data.size) return
        val commentCount = readUInt32LE(data, pos)
        pos += 4

        repeat(commentCount.coerceAtMost(10000).toInt()) {
            if (pos + 4 > data.size) return
            val commentLen = readUInt32LE(data, pos)
            pos += 4
            if (pos + commentLen.toInt() > data.size) return
            val comment = String(data, pos, commentLen.toInt(), Charsets.UTF_8)
            pos += commentLen.toInt()
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                val value = comment.substring(eq + 1)
                out[key] = value.trim()
            }
        }
    }

    private fun parseOpusTags(data: ByteArray, offset: Int, out: MutableMap<String, String>) {
        // OpusTags format is the same as Vorbis Comment after the "OpusTags" header
        parseVorbisComment(data, offset, out)
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}

internal fun readHead(file: File, limit: Int): ByteArray {
    return FileInputStream(file).use { fis ->
        val buf = ByteArray(limit.coerceAtMost(file.length().toInt().coerceAtLeast(limit)))
        val read = fis.read(buf)
        if (read < buf.size) buf.copyOf(read) else buf
    }
}
