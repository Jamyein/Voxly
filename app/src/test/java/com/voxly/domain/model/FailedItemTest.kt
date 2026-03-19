package com.voxly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedItemTest {

    @Test
    fun `FailedItem stores filePath and reason`() {
        val item = FailedItem(
            filePath = "/storage/music/test.mp3",
            reason = "Permission denied"
        )

        assertEquals("/storage/music/test.mp3", item.filePath)
        assertEquals("Permission denied", item.reason)
        assertTrue(item.timestamp > 0)
    }

    @Test
    fun `FailedItem uses provided timestamp`() {
        val timestamp = 1000L
        val item = FailedItem(
            filePath = "/test.mp3",
            reason = "Error",
            timestamp = timestamp
        )

        assertEquals(timestamp, item.timestamp)
    }
}