package com.voxly.presentation.viewmodel

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectoryManagementViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _directories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val directories: StateFlow<List<SelectedDirectory>> = _directories.asStateFlow()

    private val _blacklistDirectories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val blacklistDirectories: StateFlow<List<SelectedDirectory>> = _blacklistDirectories.asStateFlow()

    val whitelistEnabled: StateFlow<Boolean> = settingsDataStore.whitelistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val blacklistEnabled: StateFlow<Boolean> = settingsDataStore.blacklistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadDirectories()
        loadBlacklistDirectories()
    }

    fun loadDirectories() {
        viewModelScope.launch {
            val uris = settingsDataStore.selectedDirectoryUris.first()
            _directories.value = uris.mapNotNull { uriString ->
                val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
                val path = getPathFromUri(parsed)
                if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
            }
        }
    }

    fun addDirectory(directoryUri: Uri) {
        val path = getPathFromUri(directoryUri)
        if (path.isBlank()) return
        val uriString = directoryUri.toString()
        val updated = (_directories.value + SelectedDirectory(uri = uriString, path = path))
            .distinctBy { it.uri }
        persistDirectories(updated)
    }

    fun removeDirectory(directoryUri: String) {
        val updated = _directories.value.filterNot { it.uri == directoryUri }
        persistDirectories(updated)
    }

    fun clearDirectories() {
        persistDirectories(emptyList())
    }

    private fun persistDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setSelectedDirectoryUris(directories.map { it.uri })
            _directories.value = directories
        }
    }

    fun loadBlacklistDirectories() {
        viewModelScope.launch {
            val uris = settingsDataStore.blacklistDirectoryUris.first()
            _blacklistDirectories.value = uris.mapNotNull { uriString ->
                val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
                val path = getPathFromUri(parsed)
                if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
            }
        }
    }

    fun addBlacklistDirectory(directoryUri: Uri) {
        val path = getPathFromUri(directoryUri)
        if (path.isBlank()) return
        val uriString = directoryUri.toString()
        val updated = (_blacklistDirectories.value + SelectedDirectory(uri = uriString, path = path))
            .distinctBy { it.uri }
        persistBlacklistDirectories(updated)
    }

    fun removeBlacklistDirectory(directoryUri: String) {
        val updated = _blacklistDirectories.value.filterNot { it.uri == directoryUri }
        persistBlacklistDirectories(updated)
    }

    fun clearBlacklistDirectories() {
        persistBlacklistDirectories(emptyList())
    }

    private fun persistBlacklistDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setBlacklistDirectoryUris(directories.map { it.uri })
            _blacklistDirectories.value = directories
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

            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId.startsWith("raw:")) {
                return@runCatching documentId.removePrefix("raw:")
            }

            val idParts = documentId.split(":", limit = 2)
            val volume = idParts.firstOrNull().orEmpty()
            val relativePath = idParts.getOrNull(1)?.trim('/').orEmpty()

            when {
                volume.equals("primary", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    if (relativePath.isEmpty()) externalRoot else "$externalRoot/$relativePath"
                }
                volume.equals("home", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
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
