package com.voxly.data.remote.tengx.crypto

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * QQ Music lyrics decryption utility.
 *
 * Replicates the QRC decryption chain from the Lyrico QQ Music plugin:
 *   1. hex string → bytes
 *   2. 3DES decrypt (DESede/ECB/NoPadding, 24-byte key)
 *   3. zlib inflate → plain text
 *
 * Key source: Lyrico-Plugins qq/source.js decryptQrc / decodeQqLyricPayload.
 */
object QQMusicLyricCrypto {

    /** 24-byte 3DES key hardcoded in QQ Music's QRC client. */
    private const val QRC_KEY = "!@#)(*\$%123ZXC!@!@#)(NHL"

    /**
     * Decrypts a hex-encoded QRC lyric payload.
     *
     * @return plain-text QRC or null when input is empty / not a multiple of 8 bytes.
     */
    fun decryptQrc(hex: String): String? {
        val bytes = hexToBytes(hex)
        if (bytes.isEmpty() || bytes.size % 8 != 0) return null
        return try {
            val keyBytes = QRC_KEY.toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DESede"))
            inflateBytesToText(cipher.doFinal(bytes))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a QQ Music lyric payload, trying 3DES first then falling back
     * to base64 (some trans / legacy responses are Base64 LRC).
     *
     * After 3DES decrypt + inflate, the result is validated: it must contain
     * '[' (QRC / LRC marker) to be accepted.  Invalid output falls through
     * to the base64 path.
     */
    fun decodeLyricPayload(value: String): String {
        val raw = value.trim()
        if (raw.isEmpty()) return ""
        val decrypted = decryptQrc(raw)
        if (!decrypted.isNullOrBlank() && decrypted.contains('[')) return decrypted
        return try {
            String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Converts a hex string to byte array.  Non-hex characters are stripped first
     * so mixed-format inputs (with newlines, etc.) still parse.
     */
    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        val len = clean.length
        if (len % 2 != 0) return ByteArray(0)
        return ByteArray(len / 2) { i ->
            ((clean[i * 2].digitToIntOrNull(16) ?: 0) shl 4 or
                    (clean[i * 2 + 1].digitToIntOrNull(16) ?: 0)).toByte()
        }
    }

    /** zlib inflate (RFC 1950) → UTF-8 string. */
    private fun inflateBytesToText(bytes: ByteArray): String {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 3)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        inflater.end()
        return out.toString(Charsets.UTF_8)
    }
}
