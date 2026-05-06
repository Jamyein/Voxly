package com.voxly.data.remote.tengx.crypto

import android.util.Base64
import java.security.MessageDigest

/**
 * QQ Music API signature utility.
 * Implements the zzcSign algorithm used by QQ Music mobile API.
 *
 * Based on any-listen-extension-online-metadata implementation:
 * https://github.com/any-listen/any-listen-extension-online-metadata
 * Reference: src/qq_music/sign.ts
 *
 * Used for generating signatures for POST search requests.
 */
object QQMusicCrypto {

    /**
     * Generates a zzcSign signature for the given text.
     *
     * Algorithm (from any-listen TypeScript reference):
     * 1. Compute SHA1 hash of the input text
     * 2. Pick characters from hash using PART_1_INDEXES for part1
     * 3. Pick characters from hash using PART_2_INDEXES for part2
     * 4. XOR scramble: SCRAMBLE_VALUES[i] ^ hash_byte[i] for each byte
     * 5. Base64 encode scrambled bytes, remove /+= chars, lowercase
     * 6. Combine: "zzc" + part1 + base64_scramble + part2, lowercase
     *
     * @param text Input text to sign (typically JSON body string)
     * @return Signature string starting with "zzc"
     */
    fun zzcSign(text: String): String {
        // Step 1: SHA1 hash
        val hash = sha1(text)

        // Step 2: Pick characters for part1 and part2
        val part1 = pickHashByIdx(hash, PART_1_INDEXES)
        val part2 = pickHashByIdx(hash, PART_2_INDEXES)

        // Step 3: XOR scramble
        val scrambled = ByteArray(SCRAMBLE_VALUES.size) { i ->
            val hashByte = hash.substring(i * 2, i * 2 + 2).toInt(16)
            (SCRAMBLE_VALUES[i] xor hashByte).toByte()
        }

        // Step 4: Base64 encode and clean
        val b64Part = Base64.encodeToString(scrambled, Base64.NO_WRAP)
            .replace("/", "")
            .replace("+", "")
            .replace("=", "")
            .lowercase()

        // Step 5: Combine
        return "zzc${part1}${b64Part}${part2}".lowercase()
    }

    /**
     * Computes SHA1 hash of the input string.
     *
     * @param text Input string
     * @return SHA1 hash as lowercase hex string (40 characters)
     */
    private fun sha1(text: String): String {
        val md = MessageDigest.getInstance("SHA1")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Picks characters from hash string using index array.
     *
     * @param hash SHA1 hash string (40 chars)
     * @param indexes Array of character indexes to pick
     * @return Concatenated characters from specified indexes
     */
    private fun pickHashByIdx(hash: String, indexes: IntArray): String {
        return buildString {
            for (idx in indexes) {
                if (idx < hash.length) {
                    append(hash[idx])
                }
            }
        }
    }

    // Index arrays for signature generation
    private val PART_1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
    private val PART_2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)

    // XOR scramble values (20 bytes)
    private val SCRAMBLE_VALUES = intArrayOf(
        89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179
    )
}
