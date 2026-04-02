package com.voxly.core.util

import android.Manifest
import android.os.Build

object MediaPermission {
    fun audioReadPermission(sdkInt: Int): String {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
