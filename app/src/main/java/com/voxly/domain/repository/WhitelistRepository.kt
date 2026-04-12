package com.voxly.domain.repository

import com.voxly.domain.model.WhitelistDirectory
import kotlinx.coroutines.flow.Flow

interface WhitelistRepository {
    fun getWhitelistDirectories(): Flow<List<WhitelistDirectory>>
    
    fun getBlacklistDirectories(): Flow<List<WhitelistDirectory>>
    
    fun getValidWhitelistPaths(): Flow<List<String>>
    
    fun getValidBlacklistPaths(): Flow<List<String>>

    @Suppress("unused")
    fun getValidWhitelistPathsOnce(): List<String>

    @Suppress("unused")
    fun getValidBlacklistPathsOnce(): List<String>
    
    suspend fun addWhitelistDirectory(uri: String, path: String)
    
    suspend fun removeWhitelistDirectory(uri: String)
    
    suspend fun clearWhitelist()
    
    suspend fun addBlacklistDirectory(uri: String, path: String)
    
    suspend fun removeBlacklistDirectory(uri: String)
    
    suspend fun clearBlacklist()
    
    suspend fun validateDirectories()
}
