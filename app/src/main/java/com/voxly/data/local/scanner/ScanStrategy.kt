package com.voxly.data.local.scanner

import com.voxly.domain.model.AudioFile
import java.io.File
import java.text.Collator
import java.util.Locale

interface ScanStrategy {
    suspend fun scan(): List<AudioFile>
}

abstract class BaseScanStrategy(
    protected val chineseCollator: Collator
) : ScanStrategy {
    protected companion object {
        private val chineseCollatorInstance: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }
    }
}
