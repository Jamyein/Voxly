package com.voxly.data.local.scanner

import com.voxly.domain.model.AudioFile

interface ScanStrategy {
    suspend fun scan(): List<AudioFile>
}
