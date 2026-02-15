package com.voxly

import android.app.Application
import com.voxly.core.util.CrashHandler
import com.voxly.core.util.FileLoggingTree
import com.voxly.core.util.LogManager
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Application class for MP3 Tag Editor.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MP3TagApplication : Application() {

    private lateinit var fileLoggingTree: FileLoggingTree

    override fun onCreate() {
        super.onCreate()

        initLogging()
    }

    private fun initLogging() {
        // Initialize LogManager first
        LogManager.init(this)
        applyLoggingSettings()

        // Initialize Timber
        if (LogManager.isLoggingEnabled) {
            // Always plant file logging tree
            fileLoggingTree = FileLoggingTree()
            Timber.plant(fileLoggingTree)

            // Plant debug tree only in debug builds
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }

            // Cleanup excess logs on startup
            fileLoggingTree.cleanupExcessLogs()
        }

        // Setup crash handler
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        Timber.i("Application started - Version ${BuildConfig.VERSION_NAME}")
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
