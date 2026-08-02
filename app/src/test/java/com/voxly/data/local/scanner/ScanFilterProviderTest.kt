package com.voxly.data.local.scanner

import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.WhitelistRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterProviderTest {

    private val settingsDataStore = mockk<SettingsDataStore>()
    private val whitelistRepository = mockk<WhitelistRepository>()

    @Test
    fun `whitelist enabled and has paths makes whitelistEnabled true with paths`() = runTest {
        every { settingsDataStore.whitelistEnabled } returns flowOf(true)
        every { settingsDataStore.blacklistEnabled } returns flowOf(false)
        every { whitelistRepository.getValidWhitelistPathsOnce() } returns listOf("/music")
        every { whitelistRepository.getValidBlacklistPathsOnce() } returns emptyList()

        val provider = ScanFilterProvider(settingsDataStore, whitelistRepository)
        val settings = provider.current()

        assertTrue(settings.whitelistEnabled)
        assertEquals(listOf("/music"), settings.whitelistPaths)
        assertFalse(settings.blacklistEnabled)
        assertFalse(settings.minDurationEnabled)
    }

    @Test
    fun `whitelist enabled but no valid paths makes whitelistEnabled false`() = runTest {
        every { settingsDataStore.whitelistEnabled } returns flowOf(true)
        every { settingsDataStore.blacklistEnabled } returns flowOf(false)
        every { whitelistRepository.getValidWhitelistPathsOnce() } returns emptyList()

        val provider = ScanFilterProvider(settingsDataStore, whitelistRepository)
        val settings = provider.current()

        assertFalse(settings.whitelistEnabled)
    }

    @Test
    fun `blacklist enabled and has paths makes blacklistEnabled true with paths`() = runTest {
        every { settingsDataStore.whitelistEnabled } returns flowOf(false)
        every { settingsDataStore.blacklistEnabled } returns flowOf(true)
        every { whitelistRepository.getValidBlacklistPathsOnce() } returns listOf("/exclude")
        every { whitelistRepository.getValidWhitelistPathsOnce() } returns emptyList()

        val provider = ScanFilterProvider(settingsDataStore, whitelistRepository)
        val settings = provider.current()

        assertTrue(settings.blacklistEnabled)
        assertEquals(listOf("/exclude"), settings.blacklistPaths)
    }

    @Test
    fun `nothing enabled leaves all filters off`() = runTest {
        every { settingsDataStore.whitelistEnabled } returns flowOf(false)
        every { settingsDataStore.blacklistEnabled } returns flowOf(false)

        val provider = ScanFilterProvider(settingsDataStore, whitelistRepository)
        val settings = provider.current()

        assertFalse(settings.whitelistEnabled)
        assertFalse(settings.blacklistEnabled)
        assertTrue(settings.whitelistPaths.isEmpty())
        assertTrue(settings.blacklistPaths.isEmpty())
    }
}
