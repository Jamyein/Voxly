package com.voxly.presentation.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

private const val MIN_DIMENSION = 1

fun decodeBitmapFromBytes(
    bytes: ByteArray,
    targetSizePx: Int? = null
): Bitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val source = ImageDecoder.createSource(bytes)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM

            val target = targetSizePx
            if (target != null && target > 0) {
                val width = info.size.width.coerceAtLeast(MIN_DIMENSION)
                val height = info.size.height.coerceAtLeast(MIN_DIMENSION)
                var sampleSize = 1
                while ((width / sampleSize) > target || (height / sampleSize) > target) {
                    sampleSize *= 2
                }
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
        }
    }.getOrNull()
}

/**
 * Utility function to convert bitmap to JPEG bytes.
 */
fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray? {
    return runCatching {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), output)
        output.toByteArray()
    }.getOrNull()
}

/**
 * Utility function to rotate JPEG bytes.
 */
fun rotateJpegBytes(bytes: ByteArray, degrees: Float): ByteArray? {
    return runCatching {
        val src = decodeBitmapFromBytes(bytes)
            ?: throw IllegalArgumentException("Invalid image bytes")
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        bitmapToJpegBytes(rotated) ?: throw IllegalStateException("Failed to encode rotated image")
    }.getOrNull()
}
