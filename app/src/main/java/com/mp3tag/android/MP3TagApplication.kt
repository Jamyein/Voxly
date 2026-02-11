package com.mp3tag.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MP3 Tag Editor.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MP3TagApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // AppCompatDelegate automatically persists and restores language settings
    }
}
