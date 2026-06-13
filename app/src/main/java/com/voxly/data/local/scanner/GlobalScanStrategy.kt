package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
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
    private val fastScanProcessor: FastScanProcessor
) : ScanStrategy {
    companion object {
        private const val TAG = "GlobalScanStrategy"
        private val chineseCollator = SortUtil.chineseCollator
    }

    override suspend fun scan(): List<AudioFile> = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("GlobalScanStrategy scan started")

        val minDurationEnabled = settingsDataStore.minDurationFilterEnabled.first()
        val minDurationMs = settingsDataStore.minDurationFilterThresholdMs.first().toLong()

        val files: List<AudioFile> = mediaStoreDataSource.queryAll(minDurationEnabled, minDurationMs)
        val enrichedFiles = fastScanProcessor.enrichAll(files)
        enrichedFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }
}
