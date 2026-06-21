package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.WhitelistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class GlobalScanStrategy @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val fileProcessor: FileProcessor,
    private val fastScanProcessor: FastScanProcessor,
    private val filterEngine: FilterEngine,
    private val whitelistRepository: WhitelistRepository
) : ScanStrategy {
    companion object {
        private const val TAG = "GlobalScanStrategy"
        private val chineseCollator = SortUtil.chineseCollator
    }

    override suspend fun scan(): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("GlobalScanStrategy scan started")

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        var files: List<AudioFile> = mediaStoreDataSource.queryAll(minDurationEnabled, minDurationMs)

        // Apply whitelist/blacklist filters before lightweight metadata parsing
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val whitelistPaths = if (whitelistEnabled) whitelistRepository.getValidWhitelistPathsOnce() else emptyList()
        val blacklistPaths = if (blacklistEnabled) whitelistRepository.getValidBlacklistPathsOnce() else emptyList()
        files = filterEngine.applyFilters(files, FilterEngine.FilterSettings(
            whitelistEnabled = whitelistEnabled && whitelistPaths.isNotEmpty(),
            blacklistEnabled = blacklistEnabled && blacklistPaths.isNotEmpty(),
            minDurationEnabled = false,
            whitelistPaths = whitelistPaths,
            blacklistPaths = blacklistPaths,
            minDurationMs = 0L
        ))

        val enrichedFiles = fastScanProcessor.enrichAll(files)
        enrichedFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }
}
