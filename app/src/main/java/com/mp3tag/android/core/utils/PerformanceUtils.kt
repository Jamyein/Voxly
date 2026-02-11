package com.mp3tag.android.core.utils

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for performance optimizations.
 */
@Singleton
class PerformanceUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Creates an optimized ImageLoader for Coil.
     * Configures memory and disk caching for better image loading performance.
     */
    fun createOptimizedImageLoader(): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB disk cache
                    .build()
            }
            .crossfade(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    components {
                        add(ImageDecoderDecoder.Factory())
                    }
                }
            }
            .build()
    }

    companion object {
        /**
         * Calculates the optimal number of items to load in a list based on screen size.
         * @param screenHeight Screen height in pixels
         * @param itemHeight Average item height in pixels
         * @return Optimal page size
         */
        fun calculateOptimalPageSize(screenHeight: Int, itemHeight: Int): Int {
            // Load 2x the visible items for smooth scrolling
            return ((screenHeight / itemHeight) * 2).coerceIn(10, 50)
        }

        /**
         * Formats a file size with appropriate units.
         * @param bytes Size in bytes
         * @return Formatted string (e.g., "1.5 MB")
         */
        fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }

        /**
         * Formats duration in milliseconds to human-readable string.
         * @param durationMs Duration in milliseconds
         * @return Formatted string (e.g., "3:45")
         */
        fun formatDuration(durationMs: Long): String {
            val hours = durationMs / 3_600_000
            val minutes = (durationMs % 3_600_000) / 60_000
            val seconds = (durationMs % 60_000) / 1_000

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

        /**
         * Throttles a function call to prevent excessive execution.
         * Usage: val throttledFunction = throttle(1000) { doSomething() }
         */
        inline fun <T> throttle(
            intervalMs: Long,
            crossinline action: (T) -> Unit
        ): (T) -> Unit {
            var lastExecutionTime = 0L
            return { param: T ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastExecutionTime >= intervalMs) {
                    lastExecutionTime = currentTime
                    action(param)
                }
            }
        }
    }
}
