package com.voxly.di

import com.voxly.data.local.cache.AlbumArtCacheManager
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlbumArtCacheEntryPoint {
    fun albumArtCacheManager(): AlbumArtCacheManager
    fun tagLibMetadataProcessor(): TagLibMetadataProcessor
}
