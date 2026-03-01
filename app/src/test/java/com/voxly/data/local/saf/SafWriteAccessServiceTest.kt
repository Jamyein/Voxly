package com.voxly.data.local.saf

import org.junit.Assert.assertEquals
import org.junit.Test

class SafWriteAccessServiceTest {

    @Test
    fun `calculateMatchLength returns base length for exact match`() {
        val filePath = "/storage/emulated/0/Music/Telegram"
        val basePath = "/storage/emulated/0/Music/Telegram"

        val result = SafWriteAccessService.calculateMatchLength(filePath, basePath)

        assertEquals(basePath.length, result)
    }

    @Test
    fun `calculateMatchLength returns base length for child path match`() {
        val filePath = "/storage/emulated/0/Music/Telegram/song.flac"
        val basePath = "/storage/emulated/0/Music/Telegram"

        val result = SafWriteAccessService.calculateMatchLength(filePath, basePath)

        assertEquals(basePath.length, result)
    }

    @Test
    fun `calculateMatchLength returns minus one for non matching path`() {
        val filePath = "/storage/emulated/0/Download/song.flac"
        val basePath = "/storage/emulated/0/Music/Telegram"

        val result = SafWriteAccessService.calculateMatchLength(filePath, basePath)

        assertEquals(-1, result)
    }
}
