package com.voxly.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.WhitelistDirectory
import com.voxly.domain.repository.WhitelistRepository
import com.voxly.domain.usecase.UnifiedScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectoryManagementViewModel @Inject constructor(
    private val whitelistRepository: WhitelistRepository,
    private val settingsDataStore: SettingsDataStore,
    private val unifiedScanManager: UnifiedScanManager
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

    fun addDirectory(directoryUri: Uri) {
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

    private fun getPathFromUri(uri: Uri): String {
        return runCatching {
            if (uri.scheme == "file") return@runCatching uri.path.orEmpty()
            if (uri.scheme != "content") return@runCatching uri.path.orEmpty()

            val documentId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            if (documentId.startsWith("raw:")) {
                return@runCatching documentId.removePrefix("raw:")
            }

            val idParts = documentId.split(":", limit = 2)
            val volume = idParts.firstOrNull().orEmpty()
            val relativePath = idParts.getOrNull(1)?.trim('/').orEmpty()

            when {
                volume.equals("primary", ignoreCase = true) -> {
                    val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
                    if (relativePath.isEmpty()) externalRoot else "$externalRoot/$relativePath"
                }
                volume.equals("home", ignoreCase = true) -> {
                    val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
                    val documentsRoot = "$externalRoot/Documents"
                    if (relativePath.isEmpty()) documentsRoot else "$documentsRoot/$relativePath"
                }
                volume.isNotEmpty() -> {
                    if (relativePath.isEmpty()) "/storage/$volume" else "/storage/$volume/$relativePath"
                }
                else -> uri.path.orEmpty()
            }
        }.getOrElse {
            uri.path.orEmpty()
        }
    }
}
