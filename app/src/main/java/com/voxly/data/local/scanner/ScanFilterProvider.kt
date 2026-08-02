package com.voxly.data.local.scanner

import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.WhitelistRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-shot reader for [FilterEngine.FilterSettings] (whitelist/blacklist only).
 *
 * Every ScanStrategy used to read whitelist/blacklist settings from DataStore
 * itself, duplicating the same ~10 lines. This provider reads the settings ONCE
 * per scan lifecycle so the strategies stay pure. minDuration is NOT included —
 * it is consumed at the MediaStore Cursor layer and stays inside the strategies.
 */
@Singleton
class ScanFilterProvider @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val whitelistRepository: WhitelistRepository,
) {
    suspend fun current(): FilterEngine.FilterSettings {
        val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
        val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
        val whitelistPaths = if (whitelistEnabled) whitelistRepository.getValidWhitelistPathsOnce() else emptyList()
        val blacklistPaths = if (blacklistEnabled) whitelistRepository.getValidBlacklistPathsOnce() else emptyList()

        return FilterEngine.FilterSettings(
            whitelistEnabled = whitelistEnabled && whitelistPaths.isNotEmpty(),
            blacklistEnabled = blacklistEnabled && blacklistPaths.isNotEmpty(),
            minDurationEnabled = false,
            whitelistPaths = whitelistPaths,
            blacklistPaths = blacklistPaths,
            minDurationMs = 0L
        )
    }
}
