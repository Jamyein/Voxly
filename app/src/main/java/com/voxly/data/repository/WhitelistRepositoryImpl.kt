package com.voxly.data.repository

import android.net.Uri
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.WhitelistDirectory
import com.voxly.domain.repository.WhitelistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val safAccessService: SafWriteAccessService
) : WhitelistRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _whitelistDirectories = MutableStateFlow<List<WhitelistDirectory>>(emptyList())
    private val _blacklistDirectories = MutableStateFlow<List<WhitelistDirectory>>(emptyList())

    init {
        runBlocking(Dispatchers.IO) {
            try {
                val uris = settingsDataStore.selectedDirectoryUris.first()
                _whitelistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
            } catch (e: Exception) {
            }
        }
        runBlocking(Dispatchers.IO) {
            try {
                val uris = settingsDataStore.blacklistDirectoryUris.first()
                _blacklistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
            } catch (e: Exception) {
            }
        }

        repositoryScope.launch {
            settingsDataStore.selectedDirectoryUris.collect { uris ->
                _whitelistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
            }
        }
        repositoryScope.launch {
            settingsDataStore.blacklistDirectoryUris.collect { uris ->
                _blacklistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
            }
        }
    }

    override fun getWhitelistDirectories(): Flow<List<WhitelistDirectory>> = 
        _whitelistDirectories.asStateFlow()

    override fun getBlacklistDirectories(): Flow<List<WhitelistDirectory>> = 
        _blacklistDirectories.asStateFlow()

    override fun getValidWhitelistPaths(): Flow<List<String>> =
        _whitelistDirectories.map { dirs -> dirs.filter { it.isValid }.map { it.path } }

    override fun getValidBlacklistPaths(): Flow<List<String>> =
        _blacklistDirectories.map { dirs -> dirs.filter { it.isValid }.map { it.path } }

    // Synchronous method for use in AlbumArtistAggregator - no suspension
    override fun getValidWhitelistPathsOnce(): List<String> =
        _whitelistDirectories.value.filter { it.isValid }.map { it.path }

    override fun getValidBlacklistPathsOnce(): List<String> =
        _blacklistDirectories.value.filter { it.isValid }.map { it.path }

    override suspend fun addWhitelistDirectory(uri: String, path: String) {
        val current = _whitelistDirectories.value.toMutableList()
        if (current.none { it.uri == uri }) {
            current.add(WhitelistDirectory(uri = uri, path = path))
            settingsDataStore.setSelectedDirectoryUris(current.map { it.uri })
        }
    }

    override suspend fun removeWhitelistDirectory(uri: String) {
        val current = _whitelistDirectories.value.toMutableList()
        current.removeAll { it.uri == uri }
        settingsDataStore.setSelectedDirectoryUris(current.map { it.uri })
    }

    override suspend fun clearWhitelist() {
        _whitelistDirectories.value = emptyList()
        settingsDataStore.setSelectedDirectoryUris(emptyList())
    }

    override suspend fun addBlacklistDirectory(uri: String, path: String) {
        val current = _blacklistDirectories.value.toMutableList()
        if (current.none { it.uri == uri }) {
            current.add(WhitelistDirectory(uri = uri, path = path))
            settingsDataStore.setBlacklistDirectoryUris(current.map { it.uri })
        }
    }

    override suspend fun removeBlacklistDirectory(uri: String) {
        val current = _blacklistDirectories.value.toMutableList()
        current.removeAll { it.uri == uri }
        settingsDataStore.setBlacklistDirectoryUris(current.map { it.uri })
    }

    override suspend fun clearBlacklist() {
        _blacklistDirectories.value = emptyList()
        settingsDataStore.setBlacklistDirectoryUris(emptyList())
    }

    override suspend fun validateDirectories() {
        val validatedWhitelist = _whitelistDirectories.value.map { dir ->
            val path = safAccessService.mapTreeUriToPath(Uri.parse(dir.uri))
            dir.copy(path = path ?: dir.path, isValid = path != null)
        }
        _whitelistDirectories.value = validatedWhitelist

        val validatedBlacklist = _blacklistDirectories.value.map { dir ->
            val path = safAccessService.mapTreeUriToPath(Uri.parse(dir.uri))
            dir.copy(path = path ?: dir.path, isValid = path != null)
        }
        _blacklistDirectories.value = validatedBlacklist
    }
}
