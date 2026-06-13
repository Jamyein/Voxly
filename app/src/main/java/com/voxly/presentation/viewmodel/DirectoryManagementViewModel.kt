package com.voxly.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.WhitelistDirectory
import com.voxly.domain.repository.WhitelistRepository
import com.voxly.domain.usecase.RebuildDatabaseManager
import com.voxly.domain.usecase.RebuildDatabaseState
import com.voxly.domain.usecase.UnifiedScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DirectoryManagementViewModel @Inject constructor(
    private val whitelistRepository: WhitelistRepository,
    private val settingsDataStore: SettingsDataStore,
    private val unifiedScanManager: UnifiedScanManager,
    private val rebuildDatabaseManager: RebuildDatabaseManager
) : ViewModel() {

    val directories: StateFlow<List<WhitelistDirectory>> = 
        whitelistRepository.getWhitelistDirectories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blacklistDirectories: StateFlow<List<WhitelistDirectory>> = 
        whitelistRepository.getBlacklistDirectories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelistEnabled: StateFlow<Boolean> = settingsDataStore.whitelistEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blacklistEnabled: StateFlow<Boolean> = settingsDataStore.blacklistEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rebuildState: StateFlow<RebuildDatabaseState> = rebuildDatabaseManager.rebuildState

    fun rebuildDatabase() {
        Timber.tag("Voxly").i("DirectoryManagementViewModel: rebuildDatabase started")
        viewModelScope.launch {
            rebuildDatabaseManager.rebuild()
        }
    }

    fun addDirectory(directoryUri: Uri) {
        Timber.tag("Voxly").i("DirectoryManagementViewModel: addDirectory started")
        viewModelScope.launch {
            val path = getPathFromUri(directoryUri)
            if (path.isNotBlank()) {
                whitelistRepository.addWhitelistDirectory(directoryUri.toString(), path)
                unifiedScanManager.syncDirectories()
            }
        }
    }

    fun removeDirectory(directoryUri: String) {
        viewModelScope.launch {
            whitelistRepository.removeWhitelistDirectory(directoryUri)
            unifiedScanManager.syncDirectories()
        }
    }

    fun clearDirectories() {
        viewModelScope.launch {
            whitelistRepository.clearWhitelist()
        }
    }

    fun addBlacklistDirectory(directoryUri: Uri) {
        viewModelScope.launch {
            val path = getPathFromUri(directoryUri)
            if (path.isNotBlank()) {
                whitelistRepository.addBlacklistDirectory(directoryUri.toString(), path)
            }
        }
    }

    fun removeBlacklistDirectory(directoryUri: String) {
        viewModelScope.launch {
            whitelistRepository.removeBlacklistDirectory(directoryUri)
        }
    }

    fun clearBlacklistDirectories() {
        viewModelScope.launch {
            whitelistRepository.clearBlacklist()
        }
    }

    fun setWhitelistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setWhitelistEnabled(enabled)
        }
    }

    fun setBlacklistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setBlacklistEnabled(enabled)
        }
    }

    private fun getPathFromUri(uri: Uri): String = com.voxly.core.util.PathUtils.getPathFromUri(uri)
}
