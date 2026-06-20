package com.voxly.data.local.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.cache.EnrichmentJobEntity
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import com.voxly.domain.model.AudioFile
import com.voxly.domain.usecase.MemoryPressureMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * WorkManager worker for background metadata enrichment.
 *
 * Processes pending enrichment jobs from the Room queue:
 * 1. Try LightweightMetadataParser first (fast, no JNI, no cover art).
 * 2. If missing year/sampleRate, fall back to TagLib readAllMetadata(includeAlbumArt=false).
 * 3. Write enriched metadata back to cache.
 * 4. Mark job as completed or failed.
 */
@HiltWorker
class EnrichmentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val musicLibraryCache: MusicLibraryCache,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val memoryPressureMonitor: MemoryPressureMonitor
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EnrichmentWorker"
        private const val BATCH_SIZE = 200
        private const val MAX_CONCURRENCY = 4
        private const val WORK_NAME = "metadata_enrichment_work"

        fun workName(): String = WORK_NAME
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.tag("Voxly").i("EnrichmentWorker doWork started")
        try {
            var processed = 0
            var hasMore = true

            while (hasMore) {
                val jobs = musicLibraryCache.getPendingEnrichmentJobs(BATCH_SIZE)
                Timber.tag("Voxly").i("EnrichmentWorker batch: jobs=${jobs.size} processed=$processed")
                if (jobs.isEmpty()) {
                    hasMore = false
                    break
                }

                val semaphore = Semaphore(memoryPressureMonitor.getCurrentConcurrency(MAX_CONCURRENCY))
                val enrichedFiles = mutableListOf<AudioFile>()

                coroutineScope {
                    jobs.map { job ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                processJob(job)
                            }
                        }
                    }.awaitAll().filterNotNull().let { enrichedFiles.addAll(it) }
                }

                if (enrichedFiles.isNotEmpty()) {
                    musicLibraryCache.updateCache(enrichedFiles)
                }

                processed += jobs.size
            }

            Timber.tag("Voxly").i("EnrichmentWorker completed. Processed $processed jobs.")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(TAG, "EnrichmentWorker failed", e)
            Result.retry()
        }
    }

    private suspend fun processJob(job: EnrichmentJobEntity): AudioFile? = withContext(Dispatchers.IO) {
        val cachedFile = musicLibraryCache.getCachedFile(job.filePath) ?: run {
            musicLibraryCache.updateEnrichmentJobStatus(job.filePath, EnrichmentJobEntity.STATUS_COMPLETED)
            return@withContext null
        }

        val file = File(job.filePath)
        if (!file.exists() || !file.canRead()) {
            musicLibraryCache.updateEnrichmentJobStatus(job.filePath, EnrichmentJobEntity.STATUS_COMPLETED)
            return@withContext null
        }

        val cachedEntity = musicLibraryCache.getCachedFileEntity(job.filePath)
        if (cachedEntity?.lastEditedByUserAt != null) {
            Timber.d(TAG, "Skipping enrichment for ${job.filePath}: user edited at ${cachedEntity.lastEditedByUserAt}")
            musicLibraryCache.updateEnrichmentJobStatus(job.filePath, EnrichmentJobEntity.STATUS_COMPLETED)
            return@withContext null
        }

        try {
            val lightweightResult = LightweightMetadataParser.parse(file)
            val hasMissingData = cachedFile.metadata.year.isNullOrBlank() || cachedFile.sampleRate == 0
            val lightweightSufficient = lightweightResult != null &&
                    (!cachedFile.metadata.year.isNullOrBlank() || !lightweightResult.metadata.year.isNullOrBlank()) &&
                    (cachedFile.sampleRate != 0 || lightweightResult.audioInfo?.sampleRate != null)

            val enriched = if (lightweightResult != null && (lightweightSufficient || !hasMissingData)) {
                cachedFile.copy(
                    sampleRate = lightweightResult.audioInfo?.sampleRate ?: cachedFile.sampleRate,
                    channels = lightweightResult.audioInfo?.channels ?: cachedFile.channels,
                    metadata = cachedFile.metadata.copy(
                        title = cachedFile.metadata.title ?: lightweightResult.metadata.title,
                        artist = cachedFile.metadata.artist ?: lightweightResult.metadata.artist,
                        album = cachedFile.metadata.album ?: lightweightResult.metadata.album,
                        albumArtist = cachedFile.metadata.albumArtist ?: lightweightResult.metadata.albumArtist,
                        year = cachedFile.metadata.year ?: lightweightResult.metadata.year,
                        genre = cachedFile.metadata.genre ?: lightweightResult.metadata.genre,
                        composer = cachedFile.metadata.composer ?: lightweightResult.metadata.composer
                    )
                )
            } else {
                val complete = metadataProcessor.readAllMetadata(job.filePath, includeAlbumArt = false)
                cachedFile.copy(
                    sampleRate = complete?.audioInfo?.sampleRate ?: cachedFile.sampleRate,
                    channels = complete?.audioInfo?.channels ?: cachedFile.channels,
                    metadata = cachedFile.metadata.copy(
                        title = cachedFile.metadata.title ?: complete?.metadata?.title,
                        artist = cachedFile.metadata.artist ?: complete?.metadata?.artist,
                        album = cachedFile.metadata.album ?: complete?.metadata?.album,
                        albumArtist = cachedFile.metadata.albumArtist ?: complete?.metadata?.albumArtist,
                        year = cachedFile.metadata.year ?: complete?.metadata?.year,
                        genre = cachedFile.metadata.genre ?: complete?.metadata?.genre,
                        composer = cachedFile.metadata.composer ?: complete?.metadata?.composer
                    )
                )
            }

            musicLibraryCache.updateEnrichmentJobStatus(job.filePath, EnrichmentJobEntity.STATUS_COMPLETED)
            enriched
        } catch (e: Exception) {
            Timber.w(TAG, "Enrichment failed for ${job.filePath}", e)
            val attempts = job.attemptCount + 1
            val status = if (attempts >= 3) EnrichmentJobEntity.STATUS_FAILED else EnrichmentJobEntity.STATUS_PENDING
            musicLibraryCache.updateEnrichmentJobStatus(job.filePath, status)
            null
        }
    }
}
