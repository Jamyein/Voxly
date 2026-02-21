package com.voxly.data.remote.wangy.ne

import android.util.Base64
import com.voxly.data.remote.wangy.crypto.WangyCrypto
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

/**
 * Netease Cloud Music crypto utilities.
 * Provides EAPI encryption and anonymous login support.
 * 
 * Based on Lyrico's NeCryptoUtils implementation.
 */
object NeCrypto {

    private const val DEVICEID_XOR_KEY = "3go8&$8*3*3h0k(2)2"
    
    // EAPI endpoint base URL (uses interface.music.163.com)
    const val EAPI_BASE_URL = "https://interface.music.163.com/"
    
    // Client simulation constants
    const val APP_VER = "3.1.3.203419"
    const val OS_VER = "Microsoft-Windows-10--build-19045-64bit"
    
    // Session expiration time (10 days)
    const val SESSION_EXPIRE_TIME = 10 * 24 * 60 * 60 * 1000L

    /**
     * Generates a unique device ID.
     */
    fun generateDeviceId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Generates client sign for PC client simulation.
     * Format: MAC@@@RANDOM@@@@@@HASH
     */
    fun generateClientSign(): String {
        val mac = (1..6).joinToString(":") {
            "%02X".format(Random.nextInt(256))
        }
        val randomStr = (1..8).map {
            ('A'..'Z').random()
        }.joinToString("")
        val hashPart = (1..64).map {
            "0123456789abcdef".random()
        }.joinToString("")

        return "$mac@@@$randomStr@@@@@@$hashPart"
    }

    /**
     * Generates anonymous username for guest login.
     * Uses XOR with device ID and MD5 hashing.
     * 
     * @param deviceId Device ID
     * @return Base64 encoded username
     */
    fun getAnonymousUsername(deviceId: String): String {
        val keyLength = DEVICEID_XOR_KEY.length
        val sb = StringBuilder()

        deviceId.forEachIndexed { index, char ->
            val keyChar = DEVICEID_XOR_KEY[index % keyLength]
            val xoredChar = (char.code xor keyChar.code).toChar()
            sb.append(xoredChar)
        }
        val xoredString = sb.toString()
        val md = MessageDigest.getInstance("MD5")
        val md5Digest = md.digest(xoredString.toByteArray(Charsets.UTF_8))

        val base64Md5 = Base64.encodeToString(md5Digest, Base64.NO_WRAP)

        val combinedStr = "$deviceId $base64Md5"

        return Base64.encodeToString(combinedStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * EAPI encryption for request parameters.
     * Uses the same algorithm as WangyCrypto.eapiEncrypt but returns ByteArray.
     * 
     * @param url API endpoint path (e.g., "/api/search/get")
     * @param jsonParams JSON string of parameters
     * @return Encrypted bytes (uppercase hex string will be created by caller)
     */
    fun encryptParams(url: String, jsonParams: String): ByteArray {
        // Use WangyCrypto's eapiEncrypt which has the same algorithm
        val result = WangyCrypto.eapiEncrypt(url, mapOf("temp" to "temp"))
        
        // Re-implement the exact algorithm here for clarity
        val digestText = "nobody%suse%smd5forencrypt"
        val message = String.format(digestText, url, jsonParams)
        val digest = md5(message)
        val data = "$url-36cd479b6b5-$jsonParams-36cd479b6b5-$digest"
        
        return WangyCrypto.aesEncryptEcbWithPadding(
            data.toByteArray(Charsets.UTF_8),
            "e82ckenh8dichen8".toByteArray()
        )
    }

    /**
     * AES decrypt response data.
     * 
     * @param data Encrypted response bytes
     * @return Decrypted string
     */
    fun aesDecrypt(data: ByteArray): String {
        val decrypted = WangyCrypto.aesEncryptEcbWithPadding(
            data,
            "e82ckenh8dichen8".toByteArray()
        )
        return String(decrypted).let {
            // Remove PKCS7 padding
            val paddingLength = it.last().code
            if (paddingLength in 1..16) {
                it.substring(0, it.length - paddingLength)
            } else {
                it
            }
        }
    }

    /**
     * Computes MD5 hash.
     */
    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts bytes to uppercase hex string.
     */
    fun bytesToHexUppercase(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
