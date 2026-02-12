package com.voxly

import android.app.Application
import com.voxly.core.util.CrashHandler
import com.voxly.core.util.LogManager
import com.voxly.core.util.Logger
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for MP3 Tag Editor.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MP3TagApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        initLogging()
    }

    private fun initLogging() {
        LogManager.init(this)

        Logger.init()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        Logger.i("Application started - Version ${BuildConfig.VERSION_NAME}")
    }
}
