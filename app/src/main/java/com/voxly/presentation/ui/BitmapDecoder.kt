package com.voxly.presentation.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder

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
