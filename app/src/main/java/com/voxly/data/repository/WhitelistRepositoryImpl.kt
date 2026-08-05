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

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val safAccessService: SafWriteAccessService
) : WhitelistRepository {

    companion object {
        private const val TAG = "WhitelistRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _whitelistDirectories = MutableStateFlow<List<WhitelistDirectory>>(emptyList())
    private val _blacklistDirectories = MutableStateFlow<List<WhitelistDirectory>>(emptyList())

    init {
        repositoryScope.launch {
            try {
                val uris = settingsDataStore.selectedDirectoryUris.first()
                _whitelistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
                Timber.d("$TAG: Initial whitelist loaded: ${_whitelistDirectories.value.size} directories")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to load whitelist")
            }
        }
        repositoryScope.launch {
            try {
                val uris = settingsDataStore.blacklistDirectoryUris.first()
                _blacklistDirectories.value = uris.mapNotNull { uriString ->
                    val path = safAccessService.mapTreeUriToPath(Uri.parse(uriString))
                    if (path != null) WhitelistDirectory(uri = uriString, path = path)
                    else null
                }
                Timber.d("$TAG: Initial blacklist loaded: ${_blacklistDirectories.value.size} directories")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to load blacklist")
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

    override fun getValidWhitelistPaths(): Flow<List<String>> {
        Timber.tag("Voxly").i("WhitelistRepository: operation=getValidWhitelistPaths")
        return _whitelistDirectories.map { dirs -> dirs.filter { it.isValid }.map { it.path } }
    }

    override fun getValidBlacklistPaths(): Flow<List<String>> =
        _blacklistDirectories.map { dirs -> dirs.filter { it.isValid }.map { it.path } }

    // Synchronous method for use in AlbumArtistAggregator - no suspension
    override fun getValidWhitelistPathsOnce(): List<String> =
        _whitelistDirectories.value.filter { it.isValid }.map { it.path }

    override fun getValidBlacklistPathsOnce(): List<String> =
        _blacklistDirectories.value.filter { it.isValid }.map { it.path }

    override suspend fun addWhitelistDirectory(uri: String, path: String) {
        Timber.tag("Voxly").i("WhitelistRepository: operation=addWhitelistDirectory")
        Timber.d("$TAG: addWhitelistDirectory: $path")
        val current = _whitelistDirectories.value.toMutableList()
        if (current.none { it.uri == uri }) {
            current.add(WhitelistDirectory(uri = uri, path = path))
            // Publish synchronously: filterSettings (and the whitelist filter in
            // filteredAllAudios) must see the new path before the scan that
            // refresh() starts writes to the cache, or the freshly scanned files
            // get filtered out by the stale whitelist (UI shows nothing despite
            // "scan completed" logs). The DataStore collector below is async and
            // can lag the scan. Lesson #24.
            _whitelistDirectories.value = current
            settingsDataStore.setSelectedDirectoryUris(current.map { it.uri })
            Timber.i("$TAG: Added whitelist directory: $path")
        }
    }

    override suspend fun removeWhitelistDirectory(uri: String) {
        Timber.d("$TAG: removeWhitelistDirectory: $uri")
        val current = _whitelistDirectories.value.toMutableList()
        current.removeAll { it.uri == uri }
        // Publish synchronously so the filter drops the removed path immediately
        // (see addWhitelistDirectory — lesson #24).
        _whitelistDirectories.value = current
        settingsDataStore.setSelectedDirectoryUris(current.map { it.uri })
        Timber.i("$TAG: Removed whitelist directory: $uri")
    }

    override suspend fun clearWhitelist() {
        Timber.i("$TAG: Clearing whitelist")
        _whitelistDirectories.value = emptyList()
        settingsDataStore.setSelectedDirectoryUris(emptyList())
    }

    override suspend fun addBlacklistDirectory(uri: String, path: String) {
        Timber.d("$TAG: addBlacklistDirectory: $path")
        val current = _blacklistDirectories.value.toMutableList()
        if (current.none { it.uri == uri }) {
            current.add(WhitelistDirectory(uri = uri, path = path))
            // Publish synchronously (see addWhitelistDirectory — lesson #24).
            _blacklistDirectories.value = current
            settingsDataStore.setBlacklistDirectoryUris(current.map { it.uri })
            Timber.i("$TAG: Added blacklist directory: $path")
        }
    }

    override suspend fun removeBlacklistDirectory(uri: String) {
        Timber.d("$TAG: removeBlacklistDirectory: $uri")
        val current = _blacklistDirectories.value.toMutableList()
        current.removeAll { it.uri == uri }
        // Publish synchronously (see addWhitelistDirectory — lesson #24).
        _blacklistDirectories.value = current
        settingsDataStore.setBlacklistDirectoryUris(current.map { it.uri })
        Timber.i("$TAG: Removed blacklist directory: $uri")
    }

    override suspend fun clearBlacklist() {
        Timber.i("$TAG: Clearing blacklist")
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
        
        val invalidWhitelist = validatedWhitelist.count { !it.isValid }
        val invalidBlacklist = validatedBlacklist.count { !it.isValid }
        Timber.d("$TAG: validateDirectories: whitelist invalid=$invalidWhitelist, blacklist invalid=$invalidBlacklist")
    }
}
