package com.voxly.data.remote.wangy.crypto

import android.util.Base64
import com.google.gson.Gson
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * WangY Music encryption utility class.
 * Implements EAPI and WeAPI encryption algorithms used by WangY Music API.
 *
 * EAPI: Used for search and lyrics endpoints
 * WeAPI: Used for song detail and other authenticated endpoints
 */
object WangyCrypto {

    // EAPI constants
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_AES_KEY = "e82ckenh8dichen8"
    private const val EAPI_DIGEST = "36cd479b6b5"

    // WeAPI constants
    private const val WEAPI_AES_KEY = "0CoJUm6Qyw8W8jud"
    private const val WEAPI_AES_IV = "0102030405060708"
    private const val WEAPI_RSA_PUBLIC_KEY = """MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"""

    // LinuxAPI constants (simulates Linux client)
    private const val LINUX_API_KEY = "rFgB&h#%2?^eDg:Q"

    // Common constants
    private const val AES_BLOCK_SIZE = 16
    private const val RSA_KEY_SIZE = 2048

    // ============================================
    // EAPI Encryption (Search & Lyrics)
    // ============================================

    /**
     * EAPI encryption for search and lyrics endpoints.
     *
     * Process (matching TypeScript reference):
     * 1. Convert data to JSON string
     * 2. Create message: "nobody{url}use{text}md5forencrypt"
     * 3. Compute MD5 hash of the message
     * 4. Create data string: "{url}-36cd479b6b5-{text}-36cd479b6b5-{digest}"
     * 5. AES-128-ECB encrypt the data string
     * 6. Convert to base64, then hex, then uppercase
     *
     * @param url The API endpoint URL
     * @param data Request parameters as map
     * @return Encrypted parameters as map with "params" key
     */
    fun eapiEncrypt(url: String, data: Map<String, Any>): Map<String, String> {
        return try {
            // Convert data to JSON string
            val text = buildJsonString(data)

            // Create the message for MD5: "nobody{url}use{text}md5forencrypt"
            val message = "nobody${url}use${text}md5forencrypt"

            // Compute MD5 hash
            val digest = md5(message.toByteArray())

            // Create the data string: "{url}-36cd479b6b5-{text}-36cd479b6b5-{digest}"
            val dataStr = "${url}-${EAPI_DIGEST}-${text}-${EAPI_DIGEST}-${digest}"

            // AES-ECB encrypt
            val encrypted = aesEncryptEcb(dataStr.toByteArray(), EAPI_AES_KEY.toByteArray())

            // Convert to base64, then hex, then uppercase
            val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            val hex = bytesToHex(base64.toByteArray()).uppercase()

            mapOf("params" to hex)
        } catch (e: Exception) {
            throw CryptoException("EAPI encryption failed", e)
        }
    }

    // ============================================
    // LinuxAPI Encryption (Simple AES)
    // ============================================

    /**
     * LinuxAPI encryption - simpler than WeAPI.
     * Used by Linux desktop client.
     *
     * Process (参考 music-tag-web applications/utils/encrypt.py):
     * 1. Convert data to JSON string
     * 2. Add "method": "POST" to the data
     * 3. AES-128-ECB encrypt the JSON string with fixed key
     * 4. Return as "eparams"
     *
     * @param url The API endpoint URL (without domain)
     * @param data Request parameters as map
     * @return Encrypted parameters as map with "eparams" key
     */
    fun linuxEncrypt(url: String, data: Map<String, Any>): Map<String, String> {
        return try {
            // Add method to data
            val dataWithMethod = data.toMutableMap()
            dataWithMethod["method"] = "POST"

            // Convert to JSON string
            val text = buildJsonString(dataWithMethod)

            // AES-128-ECB encrypt (no IV, no padding issues)
            val encrypted = aesEncryptEcb(text.toByteArray(Charsets.UTF_8), LINUX_API_KEY.toByteArray())

            // Convert to hex string (uppercase) - matching Python reference implementation
            val eparams = bytesToHex(encrypted).uppercase()

            mapOf("eparams" to eparams)
        } catch (e: Exception) {
            throw CryptoException("LinuxAPI encryption failed", e)
        }
    }

    // ============================================
    // WeAPI Encryption (Song Details & Auth)
    // ============================================

    /**
     * WeAPI encryption for song details and authenticated endpoints.
     *
     * Process (matching TypeScript reference):
     * 1. Convert data map to JSON string
     * 2. Generate random secretKey (16 chars from random number string)
     * 3. First AES-CBC encrypt with presetKey and IV
     * 4. Second AES-CBC encrypt with secretKey and IV
     * 5. RSA encrypt reversed secretKey with public key
     * 6. params = base64 of second AES result
     * 7. encSecKey = hex of base64 of RSA result
     *
     * @param data Request parameters as map
     * @return Encrypted parameters as map with "params" and "encSecKey" keys
     */
    fun weapiEncrypt(data: Map<String, Any>): Map<String, String> {
        return try {
            // Convert data to JSON string
            val text = buildJsonString(data)

            // Generate random secret key (16 chars from random number string)
            val randomDigits = Math.random().toString().substringAfter('.')
            val secretKeyStr = randomDigits.padEnd(16, '0').take(16)
            val secretKey = secretKeyStr.toByteArray(Charsets.UTF_8)

            // First layer: AES-CBC with fixed presetKey and IV
            val firstLayer = aesEncryptCbc(
                text.toByteArray(Charsets.UTF_8),
                WEAPI_AES_KEY.toByteArray(Charsets.UTF_8),
                WEAPI_AES_IV.toByteArray(Charsets.UTF_8)
            )

            // Second layer: AES-CBC with random secretKey and IV
            val secondLayer = aesEncryptCbc(
                firstLayer,
                secretKey,
                WEAPI_AES_IV.toByteArray(Charsets.UTF_8)
            )

            // RSA encrypt the reversed secret key
            val rsaEncrypted = rsaEncrypt(secretKey.reversedArray(), WEAPI_RSA_PUBLIC_KEY)

            // Base64 encode AES result
            val params = Base64.encodeToString(secondLayer, Base64.NO_WRAP)

            // Base64 encode RSA result, then convert to hex
            val rsaBase64 = Base64.encodeToString(rsaEncrypted, Base64.NO_WRAP)
            val encSecKey = bytesToHex(rsaBase64.toByteArray(Charsets.UTF_8)).lowercase()

            mapOf("params" to params, "encSecKey" to encSecKey)
        } catch (e: Exception) {
            throw CryptoException("WeAPI encryption failed", e)
        }
    }

    // ============================================
    // AES Encryption Methods
    // ============================================

    /**
     * AES-128-ECB encryption with PKCS5/PKCS7 padding.
     * Used in EAPI encryption.
     *
     * Note: PKCS5 and PKCS7 are the same for 16-byte block size.
     *
     * @param data Data to encrypt
     * @param key 16-byte AES key
     * @return Encrypted data
     */
    fun aesEncryptEcb(data: ByteArray, key: ByteArray): ByteArray {
        return try {
            val keySpec = SecretKeySpec(key, "AES")
            // Use PKCS5Padding (same as PKCS7 for 16-byte blocks) to handle variable-length data
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            throw CryptoException("AES-ECB encryption failed", e)
        }
    }

    /**
     * AES-128-CBC encryption with PKCS7 padding.
     * Used in WeAPI encryption.
     *
     * @param data Data to encrypt
     * @param key 16-byte AES key
     * @param iv 16-byte IV vector
     * @return Encrypted data
     */
    fun aesEncryptCbc(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return try {
            // Apply PKCS7 padding
            val paddedData = pkcs7Padding(data)

            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(paddedData)
        } catch (e: Exception) {
            throw CryptoException("AES-CBC encryption failed", e)
        }
    }

    // ============================================
    // RSA Encryption
    // ============================================

    /**
     * RSA encryption using OAEP padding.
     * Used in WeAPI to encrypt the random AES key.
     *
     * @param data Data to encrypt (typically the AES key)
     * @param publicKey Base64-encoded RSA public key
     * @return Encrypted data
     */
    fun rsaEncrypt(data: ByteArray, publicKey: String): ByteArray {
        return try {
            // Decode public key from Base64
            val keyBytes = Base64.decode(publicKey, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKeySpec = keyFactory.generatePublic(keySpec)

            // Initialize cipher with RSA/ECB/OAEPWithSHA-1AndMGF1Padding
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKeySpec)

            cipher.doFinal(data)
        } catch (e: Exception) {
            throw CryptoException("RSA encryption failed", e)
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Computes MD5 hash of the input data.
     *
     * @param data Input data
     * @return MD5 hash as lowercase hex string
     */
    fun md5(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data)
        return bytesToHex(digest).lowercase()
    }

    /**
     * Converts byte array to hexadecimal string.
     *
     * @param bytes Byte array
     * @return Hexadecimal string (lowercase by default)
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Applies PKCS7 padding to the input data.
     * Ensures data length is multiple of block size (16 bytes).
     *
     * @param data Input data
     * @return Padded data
     */
    fun pkcs7Padding(data: ByteArray): ByteArray {
        val paddingLength = AES_BLOCK_SIZE - (data.size % AES_BLOCK_SIZE)
        val padding = ByteArray(paddingLength) { paddingLength.toByte() }
        return data + padding
    }

    /**
     * Generates a random key of specified length.
     *
     * @param length Key length in bytes
     * @return Random key bytes
     */
    fun generateRandomKey(length: Int): ByteArray {
        val random = Random(System.currentTimeMillis())
        return ByteArray(length) { random.nextInt(256).toByte() }
    }

    /**
     * Builds a JSON string from a map using Gson.
     *
     * @param data Map of key-value pairs
     * @return JSON string
     */
    private fun buildJsonString(data: Map<String, Any>): String {
        return Gson().toJson(data)
    }

    /**
     * Custom exception for encryption-related errors.
     */
    class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
