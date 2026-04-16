package com.voxly.domain.usecase

import com.voxly.domain.model.BatchResult
import com.voxly.domain.model.BatchStatus
import com.voxly.domain.model.FailedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatchEngine<T>(
    private val maxConcurrency: Int = 4,
    private val memoryPressureMonitor: MemoryPressureMonitor,
    private val throttlePercent: Float = 0.05f
) {
    private var lastFailedItems: List<FailedItem> = emptyList()
    private val mutex = Mutex()

    fun getFailedItems(): List<FailedItem> = lastFailedItems.toList()

    suspend fun clearFailedItems() {
        mutex.withLock {
            lastFailedItems = emptyList()
        }
    }
    fun execute(
        items: List<T>,
        operation: suspend (T) -> Result<Unit>,
        itemName: (T) -> String
    ): Flow<BatchResult> = flow {
        val totalFiles = items.size
        var successCount = 0
        var failureCount = 0
        val failedItems = mutableListOf<FailedItem>()
        var lastEmittedPercent = -1f
        var lastEmitTime = 0L
        val minEmitIntervalMs = 200L

        try {
            emit(
                BatchResult(
                    totalFiles = totalFiles,
                    successCount = 0,
                    failedCount = 0,
                    failedItems = emptyList(),
                    status = BatchStatus.PROCESSING,
                    lastUpdatedFile = ""
                )
            )
            lastEmittedPercent = 0f
            lastEmitTime = System.currentTimeMillis()

            coroutineScope {
                var nextIndex = 0
                while (nextIndex < items.size) {
                    val concurrency = memoryPressureMonitor.getCurrentConcurrency(maxConcurrency)
                        .coerceIn(1, maxConcurrency)
                    val batch = items.subList(nextIndex, minOf(nextIndex + concurrency, items.size))
                    nextIndex += batch.size

                    val deferreds = batch.map { item ->
                        async {
                            try {
                                val result = operation(item)
                                if (result.isSuccess) {
                                    Pair(item, null)
                                } else {
                                    Pair(item, result.exceptionOrNull()?.message ?: "Unknown error")
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Pair(item, e.message ?: "Unknown error")
                            }
                        }
                    }

                    deferreds.awaitAll().forEach { (item, error) ->
                        if (error == null) {
                            successCount++
                        } else {
                            failureCount++
                            failedItems.add(FailedItem(itemName(item), error))
                        }
                    }

                    val processedCount = successCount + failureCount
                    val currentPercent = if (totalFiles > 0) {
                        processedCount.toFloat() / totalFiles
                    } else {
                        1f
                    }
                    val now = System.currentTimeMillis()
                    val percentChanged = (currentPercent - lastEmittedPercent) >= throttlePercent
                    val timeElapsed = (now - lastEmitTime) >= minEmitIntervalMs

                    if (percentChanged || timeElapsed || currentPercent >= 1f) {
                        emit(
                            BatchResult(
                                totalFiles = totalFiles,
                                successCount = successCount,
                                failedCount = failureCount,
                                failedItems = failedItems.toList(),
                                status = BatchStatus.PROCESSING,
                                lastUpdatedFile = itemName(batch.last())
                            )
                        )
                        lastEmittedPercent = currentPercent
                        lastEmitTime = now
                    }
                }
            }

            emit(
                BatchResult(
                    totalFiles = totalFiles,
                    successCount = successCount,
                    failedCount = failureCount,
                    failedItems = failedItems.toList(),
                    status = BatchStatus.COMPLETED
                )
            )
            mutex.withLock {
                lastFailedItems = failedItems.toList()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun retry(
        failedItemsInput: List<FailedItem>,
        operation: suspend (String) -> Result<Unit>
    ): Flow<BatchResult> = flow {
        val originalFailedItems = failedItemsInput.toList()
        val itemsToRetry = failedItemsInput.map { it.filePath }
        val totalFiles = itemsToRetry.size
        var successCount = 0
        var failureCount = 0
        val retryFailedItems = mutableListOf<FailedItem>()
        var lastEmittedPercent = -1f
        var lastEmitTime = 0L
        val minEmitIntervalMs = 200L

        try {
            emit(
                BatchResult(
                    totalFiles = totalFiles,
                    successCount = 0,
                    failedCount = 0,
                    failedItems = emptyList(),
                    status = BatchStatus.PROCESSING,
                    lastUpdatedFile = ""
                )
            )
            lastEmittedPercent = 0f
            lastEmitTime = System.currentTimeMillis()

            coroutineScope {
                var nextIndex = 0
                while (nextIndex < itemsToRetry.size) {
                    val concurrency = memoryPressureMonitor.getCurrentConcurrency(maxConcurrency)
                        .coerceIn(1, maxConcurrency)
                    val batch = itemsToRetry.subList(nextIndex, minOf(nextIndex + concurrency, itemsToRetry.size))
                    nextIndex += batch.size

                    val deferreds = batch.map { item ->
                        async {
                            try {
                                val result = operation(item)
                                if (result.isSuccess) {
                                    Pair(item, null)
                                } else {
                                    Pair(item, result.exceptionOrNull()?.message ?: "Unknown error")
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Pair(item, e.message ?: "Unknown error")
                            }
                        }
                    }

                    deferreds.awaitAll().forEach { (item, error) ->
                        if (error == null) {
                            successCount++
                        } else {
                            failureCount++
                            retryFailedItems.add(FailedItem(item, error))
                        }
                    }

                    val processedCount = successCount + failureCount
                    val currentPercent = if (totalFiles > 0) {
                        processedCount.toFloat() / totalFiles
                    } else {
                        1f
                    }
                    val now = System.currentTimeMillis()
                    val percentChanged = (currentPercent - lastEmittedPercent) >= throttlePercent
                    val timeElapsed = (now - lastEmitTime) >= minEmitIntervalMs

                    if (percentChanged || timeElapsed || currentPercent >= 1f) {
                        emit(
                            BatchResult(
                                totalFiles = totalFiles,
                                successCount = successCount,
                                failedCount = retryFailedItems.size,
                                failedItems = retryFailedItems.toList(),
                                status = BatchStatus.PROCESSING,
                                lastUpdatedFile = batch.last()
                            )
                        )
                        lastEmittedPercent = currentPercent
                        lastEmitTime = now
                    }
                }
            }

            val finalFailedItems = (retryFailedItems + originalFailedItems).distinctBy { it.filePath }

            emit(
                BatchResult(
                    totalFiles = totalFiles,
                    successCount = successCount,
                    failedCount = finalFailedItems.size,
                    failedItems = finalFailedItems,
                    status = BatchStatus.COMPLETED
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}
