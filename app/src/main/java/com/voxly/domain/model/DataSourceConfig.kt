package com.voxly.domain.model

/**
 * Represents a data source configuration that includes:
 * - enabled state (switch)
 * - priority/order index
 * - extra options (e.g., country code for iTunes)
 */
data class DataSourceConfig(
    val sourceId: String,           // e.g., "itunes", "musicbrainz", "netease", "qq_music"
    val enabled: Boolean = true,
    val order: Int = 0,
    val extraOptions: List<Pair<String, String>> = emptyList()  // e.g., listOf("countryCode" to "us")
) {
    fun getExtraOption(key: String, default: String = ""): String {
        return extraOptions.find { it.first == key }?.second ?: default
    }

    fun withExtraOption(key: String, value: String): DataSourceConfig {
        val updated = extraOptions.toMutableList()
        updated.removeAll { it.first == key }
        updated.add(key to value)
        return copy(extraOptions = updated)
    }

    companion object {
        fun defaultSources(sourceIds: List<String>): List<DataSourceConfig> {
            return sourceIds.mapIndexed { index, id ->
                DataSourceConfig(
                    sourceId = id,
                    enabled = true,
                    order = index
                )
            }
        }

        fun defaultMetadataSources() = defaultSources(listOf("itunes", "musicbrainz", "netease", "qq_music"))
        fun defaultLyricsSources() = defaultSources(listOf("netease", "qq_music"))
        fun defaultCoverSources() = defaultSources(listOf("itunes", "musicbrainz", "netease", "qq_music"))
    }
}

/**
 * Type of data source (metadata, lyrics, cover)
 */
enum class DataSourceType {
    METADATA,
    LYRICS,
    COVER
}

/**
 * Full source configuration for a specific type
 */
data class SourceTypeConfig(
    val type: DataSourceType,
    val sources: List<DataSourceConfig> = emptyList()
) {
    fun getSource(sourceId: String): DataSourceConfig? {
        return sources.find { it.sourceId == sourceId }
    }

    fun updateSource(source: DataSourceConfig): SourceTypeConfig {
        return copy(sources = sources.map {
            if (it.sourceId == source.sourceId) source else it
        })
    }

    fun reorderSources(orderedIds: List<String>): SourceTypeConfig {
        val sourceMap = sources.associateBy { it.sourceId }
        val reordered = orderedIds.mapIndexedNotNull { index, id ->
            sourceMap[id]?.copy(order = index)
        }
        return copy(sources = reordered)
    }
}

/**
 * All source configurations combined
 */
data class SourceConfigurations(
    val metadata: SourceTypeConfig = SourceTypeConfig(DataSourceType.METADATA, DataSourceConfig.defaultMetadataSources()),
    val lyrics: SourceTypeConfig = SourceTypeConfig(DataSourceType.LYRICS, DataSourceConfig.defaultLyricsSources()),
    val cover: SourceTypeConfig = SourceTypeConfig(DataSourceType.COVER, DataSourceConfig.defaultCoverSources())
) {
    fun getConfig(type: DataSourceType): SourceTypeConfig {
        return when (type) {
            DataSourceType.METADATA -> metadata
            DataSourceType.LYRICS -> lyrics
            DataSourceType.COVER -> cover
        }
    }

    fun updateConfig(config: SourceTypeConfig): SourceConfigurations {
        return when (config.type) {
            DataSourceType.METADATA -> copy(metadata = config)
            DataSourceType.LYRICS -> copy(lyrics = config)
            DataSourceType.COVER -> copy(cover = config)
        }
    }
}
