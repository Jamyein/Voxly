package com.voxly.data.repository

data class OnlineSourceSettings(
    val enableMusicBrainz: Boolean,
    val enableITunes: Boolean,
    val enableNetease: Boolean,
    val enableQQMusic: Boolean,
    val coverEnableMusicBrainz: Boolean,
    val coverEnableITunes: Boolean,
    val coverEnableNetease: Boolean,
    val coverEnableQQMusic: Boolean,
    val searchLimit: Int,
    val searchLimitMusicBrainz: Int,
    val searchLimitITunes: Int,
    val searchLimitNetease: Int,
    val searchLimitQQMusic: Int,
    val metadataPriority: List<String>,
    val coverPriority: List<String>
) {
    val requestLimit: Int
        get() = if (searchLimit <= 0) 200 else searchLimit

    fun getSourceLimit(source: String): Int {
        val perSourceLimit = when (source) {
            "MusicBrainz" -> searchLimitMusicBrainz
            "iTunes" -> searchLimitITunes
            "NetEase" -> searchLimitNetease
            "QQ Music" -> searchLimitQQMusic
            else -> 0
        }
        return if (perSourceLimit > 0) perSourceLimit else requestLimit
    }

    val hasAnyEnabledSource: Boolean
        get() = enableMusicBrainz || enableITunes || enableNetease || enableQQMusic

    val hasAnyCoverEnabledSource: Boolean
        get() = coverEnableMusicBrainz || coverEnableITunes || coverEnableNetease || coverEnableQQMusic

    companion object {
        val EMPTY = OnlineSourceSettings(
            enableMusicBrainz = false,
            enableITunes = false,
            enableNetease = false,
            enableQQMusic = false,
            coverEnableMusicBrainz = false,
            coverEnableITunes = false,
            coverEnableNetease = false,
            coverEnableQQMusic = false,
            searchLimit = 200,
            searchLimitMusicBrainz = 0,
            searchLimitITunes = 0,
            searchLimitNetease = 0,
            searchLimitQQMusic = 0,
            metadataPriority = emptyList(),
            coverPriority = emptyList()
        )
    }
}
