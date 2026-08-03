package com.voxly.data.local.scanner

import com.voxly.core.util.SortUtil
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class GlobalScanStrategy @Inject constructor(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val libraryCache: MusicLibraryCache,
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

        val files = mediaStoreDataSource.queryAll()
        fastScanProcessor.enrichAll(files)
            .sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
    }
}
