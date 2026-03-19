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

class BatchEngine<T>(
    private val maxConcurrency: Int = 4,
    private val memoryPressureMonitor: MemoryPressureMonitor,
    private val throttlePercent: Float = 0.05f
) {
    private val _failedItems = mutableListOf<FailedItem>()
    private var lastEmittedPercent = -1f
    private var lastEmitTime = 0L
    private val minEmitIntervalMs = 200L

    fun execute(
        items: List<T>,
        operation: suspend (T) -> Result<Unit>,
        itemName: (T) -> String
    ): Flow<BatchResult> = flow {
        val totalFiles = items.size
        var successCount = 0
        var failureCount = 0
        _failedItems.clear()
        lastEmittedPercent = -1f
        lastEmitTime = 0L

        try {
            // Emit initial state before processing begins
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
                items.chunked(maxConcurrency).forEach { batch ->
                    // Check memory pressure between chunks
                    val concurrency = memoryPressureMonitor.getCurrentConcurrency(maxConcurrency)

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
                                throw e  // Re-throw cancellation
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
                            _failedItems.add(FailedItem(itemName(item), error))
                        }
                    }

                    // Throttle check: emit only if 5% more or 200ms passed
                    val currentPercent = successCount.toFloat() / totalFiles
                    val now = System.currentTimeMillis()
                    val percentChanged = (currentPercent - lastEmittedPercent) >= throttlePercent
                    val timeElapsed = (now - lastEmitTime) >= minEmitIntervalMs

                    if (percentChanged || timeElapsed || currentPercent >= 1f) {
                        emit(
                            BatchResult(
                                totalFiles = totalFiles,
                                successCount = successCount,
                                failedCount = failureCount,
                                failedItems = _failedItems.toList(),
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
                    failedItems = _failedItems.toList(),
                    status = BatchStatus.COMPLETED
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun retry(
        failedItems: List<FailedItem>,
        operation: suspend (String) -> Result<Unit>
    ): Flow<BatchResult> = flow {
        val originalFailedItems = failedItems.toList()
        val itemsToRetry = failedItems.map { it.filePath }
        val totalFiles = itemsToRetry.size
        var successCount = 0
        var failureCount = 0
        _failedItems.clear()
        lastEmittedPercent = -1f
        lastEmitTime = 0L

        try {
            // Emit initial state before processing begins
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
                itemsToRetry.chunked(maxConcurrency).forEach { batch ->
                    val concurrency = memoryPressureMonitor.getCurrentConcurrency(maxConcurrency)

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
                            _failedItems.add(FailedItem(item, error))
                        }
                    }

                    val currentPercent = successCount.toFloat() / totalFiles
                    val now = System.currentTimeMillis()
                    val percentChanged = (currentPercent - lastEmittedPercent) >= throttlePercent
                    val timeElapsed = (now - lastEmitTime) >= minEmitIntervalMs

                    if (percentChanged || timeElapsed || currentPercent >= 1f) {
                        emit(
                            BatchResult(
                                totalFiles = totalFiles,
                                successCount = successCount,
                                failedCount = failureCount,
                                failedItems = _failedItems.toList(),
                                status = BatchStatus.PROCESSING,
                                lastUpdatedFile = batch.last()
                            )
                        )
                        lastEmittedPercent = currentPercent
                        lastEmitTime = now
                    }
                }
            }

            val finalFailedItems = _failedItems + originalFailedItems

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

    fun getFailedItems(): List<FailedItem> = _failedItems.toList()
}