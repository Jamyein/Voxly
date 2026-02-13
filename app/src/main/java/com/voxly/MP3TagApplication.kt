package com.voxly

import android.app.Application
import com.voxly.core.util.CrashHandler
import com.voxly.core.util.LogManager
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
        applyLoggingSettings()

        Logger.init()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        Logger.i("Application started - Version ${BuildConfig.VERSION_NAME}")
    }

    private fun applyLoggingSettings() {
        runBlocking {
            val settings = SettingsDataStore(this@MP3TagApplication)
            LogManager.isLoggingEnabled = settings.loggingEnabled.first()
            LogManager.isFileLoggingEnabled = settings.fileLoggingEnabled.first()
            LogManager.isConsoleLoggingEnabled = settings.consoleLoggingEnabled.first()
            LogManager.isCrashReportingEnabled = settings.crashReportingEnabled.first()
        }
    }
}
