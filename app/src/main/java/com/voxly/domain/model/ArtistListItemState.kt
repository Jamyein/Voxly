package com.voxly.domain.model

import androidx.compose.runtime.Immutable

/**
 * Stable UI model for artist list items.
 * Extracted from ArtistGroup to prevent recomposition caused by unstable AudioFile/AudioMetadata.
 */
@Immutable
data class ArtistListItemState(
    val name: String,
    val coverPath: String?,
    val albumCount: Int,
    val trackCount: Int
)
