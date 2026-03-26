package com.voxly.domain.usecase

import app.cash.turbine.test
import com.voxly.domain.model.BatchResult
import com.voxly.domain.model.BatchStatus
import com.voxly.domain.model.FailedItem
import com.voxly.domain.usecase.BatchEngine
import com.voxly.domain.usecase.MemoryPressureMonitor
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchEngineTest {

    private val mockMemoryMonitor = mockk<MemoryPressureMonitor> {
        every { getCurrentConcurrency(any()) } returns 4
    }

    @Test
    fun `execute emits progress and completion`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        val results = mutableListOf<BatchResult>()
        engine.execute(
            items = listOf("file1.mp3", "file2.mp3", "file3.mp3"),
            operation = { Result.success(Unit) },
            itemName = { it }
        ).collect { results.add(it) }

        assertTrue(results.last().status == BatchStatus.COMPLETED)
        assertEquals(3, results.last().successCount)
        assertEquals(0, results.last().failedCount)
    }

    @Test
    fun `execute tracks failures in failedItems`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        val results = mutableListOf<BatchResult>()
        engine.execute(
            items = listOf("file1.mp3", "file2.mp3", "file3.mp3"),
            operation = { item ->
                if (item == "file2.mp3") {
                    Result.failure(Exception("Test error"))
                } else {
                    Result.success(Unit)
                }
            },
            itemName = { it }
        ).collect { results.add(it) }

        val final = results.last()
        assertEquals(2, final.successCount)
        assertEquals(1, final.failedCount)
        assertEquals("file2.mp3", final.failedItems.first().filePath)
        assertEquals("Test error", final.failedItems.first().reason)
    }

    @Test
    fun `retry processes only failed items`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        // First run with some failures
        engine.execute(
            items = listOf("file1.mp3", "file2.mp3"),
            operation = { item ->
                if (item == "file2.mp3") Result.failure(Exception("Error")) else Result.success(Unit)
            },
            itemName = { it }
        ).first { it.status != BatchStatus.PROCESSING }

        // Capture failed items
        val failedItems = engine.getFailedItems()

        // Retry
        val retryResults = mutableListOf<BatchResult>()
        engine.retry(failedItems) { Result.success(Unit) }.collect { retryResults.add(it) }

        val final = retryResults.last()
        assertEquals(1, final.totalFiles) // Only file2.mp3
        assertEquals(BatchStatus.COMPLETED, final.status)
    }

    @Test
    fun `retry restores original failures if retry fails`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        // First run with failure
        engine.execute(
            items = listOf("file1.mp3", "file2.mp3"),
            operation = { item ->
                if (item == "file2.mp3") Result.failure(Exception("Original Error")) else Result.success(Unit)
            },
            itemName = { it }
        ).first { it.status != BatchStatus.PROCESSING }

        val originalFailed = engine.getFailedItems()

        // Retry with failure
        val retryResults = mutableListOf<BatchResult>()
        engine.retry(originalFailed) { Result.failure(Exception("Retry Error")) }.collect { retryResults.add(it) }

        val final = retryResults.last()
        // Should have both original failure and retry failure
        assertEquals(2, final.failedCount)
    }

    @Test
    fun `execute throttles progress updates to every 5 percent`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        val results = mutableListOf<BatchResult>()
        engine.execute(
            items = (1..100).map { "file$it.mp3" },
            operation = { Result.success(Unit) },
            itemName = { it }
        ).collect { results.add(it) }

        // With 100 files and 5% throttle, should emit ~20 updates max
        assertTrue("Expected <= 25 updates but got ${results.size}", results.size <= 25)
        // First and last should always emit
        assertEquals(0, results.first().successCount)
        assertEquals(BatchStatus.COMPLETED, results.last().status)
    }

    @Test
    fun `execute counts failures toward visible progress`() = runBlocking {
        val engine = BatchEngine<String>(memoryPressureMonitor = mockMemoryMonitor)

        val results = mutableListOf<BatchResult>()
        engine.execute(
            items = listOf("file1.mp3", "file2.mp3", "file3.mp3", "file4.mp3"),
            operation = { Result.failure(Exception("Always fails")) },
            itemName = { it }
        ).collect { results.add(it) }

        assertTrue(results.any { it.status == BatchStatus.PROCESSING && it.failedCount > 0 })
        assertEquals(4, results.last().failedCount)
        assertEquals(BatchStatus.COMPLETED, results.last().status)
    }

    @Test
    fun `execute respects reduced concurrency from memory monitor`() = runBlocking {
        val constrainedMonitor = mockk<MemoryPressureMonitor> {
            every { getCurrentConcurrency(any()) } returns 1
        }
        val engine = BatchEngine<String>(memoryPressureMonitor = constrainedMonitor)
        val activeOperations = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)

        engine.execute(
            items = listOf("file1.mp3", "file2.mp3", "file3.mp3"),
            operation = {
                val current = activeOperations.incrementAndGet()
                maxObservedConcurrency.updateAndGet { previous -> maxOf(previous, current) }
                Thread.sleep(25)
                activeOperations.decrementAndGet()
                Result.success(Unit)
            },
            itemName = { it }
        ).collect { }

        assertEquals(1, maxObservedConcurrency.get())
    }
}
