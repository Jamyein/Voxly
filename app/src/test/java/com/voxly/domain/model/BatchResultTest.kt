package com.voxly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchResultTest {

    @Test
    fun `BatchResult calculates failedCount from failedItems size`() {
        val failedItems = listOf(
            FailedItem("file1.mp3", "Error 1"),
            FailedItem("file2.mp3", "Error 2")
        )

        val result = BatchResult(
            totalFiles = 10,
            successCount = 8,
            failedCount = failedItems.size,
            failedItems = failedItems,
            status = BatchStatus.COMPLETED
        )

        assertEquals(10, result.totalFiles)
        assertEquals(8, result.successCount)
        assertEquals(2, result.failedCount)
        assertEquals(BatchStatus.COMPLETED, result.status)
    }

    @Test
    fun `BatchStatus enum has PROCESSING COMPLETED CANCELLED`() {
        assertEquals(3, BatchStatus.values().size)
        assertEquals(BatchStatus.PROCESSING, BatchStatus.valueOf("PROCESSING"))
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf("COMPLETED"))
        assertEquals(BatchStatus.CANCELLED, BatchStatus.valueOf("CANCELLED"))
    }
}
