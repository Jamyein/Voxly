package com.voxly.presentation.ui.coil

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.voxly.BuildConfig
import okio.Path.Companion.toPath

class VoxlyImageLoader private constructor(
    private val appContext: Context
) : SingletonImageLoader.Factory {

    private val _imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory("${appContext.cacheDir}/image_cache".toPath())
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(coil3.util.DebugLogger())
                }
            }
            .build()
    }

    val imageLoader: ImageLoader get() = _imageLoader

    override fun newImageLoader(context: Context): ImageLoader {
        return _imageLoader
    }

    companion object {
        @Volatile
        private var instance: VoxlyImageLoader? = null

        fun getInstance(context: Context): VoxlyImageLoader {
            return instance ?: synchronized(this) {
                instance ?: VoxlyImageLoader(context.applicationContext).also { instance = it }
            }
        }
    }
}