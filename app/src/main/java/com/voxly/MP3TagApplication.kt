package com.voxly

import android.app.Application
import com.voxly.core.util.CrashHandler
import com.voxly.core.util.FileLoggingTree
import com.voxly.core.util.LogManager
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application class for MP3 Tag Editor.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MP3TagApplication : Application() {

    private lateinit var fileLoggingTree: FileLoggingTree
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        initLogging()
    }

    private fun initLogging() {
        // Initialize LogManager first with default values
        LogManager.init(this)
        // Apply settings asynchronously
        applyLoggingSettings()
        Logger.init()

        // Always plant file logging tree - it checks isFileLoggingEnabled internally
        fileLoggingTree = FileLoggingTree()
        Timber.plant(fileLoggingTree)

        // Plant debug tree only in debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Cleanup excess logs on startup
        if (LogManager.isLoggingEnabled) {
            fileLoggingTree.cleanupExcessLogs()
        }

        // Setup crash handler
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

        Timber.i("Application started - Version ${BuildConfig.VERSION_NAME}")
    }

    private fun applyLoggingSettings() {
        appScope.launch {
            try {
                val settings = SettingsDataStore(this@MP3TagApplication)
                LogManager.isLoggingEnabled = settings.loggingEnabled.first()
                LogManager.isFileLoggingEnabled = settings.fileLoggingEnabled.first()
                LogManager.isConsoleLoggingEnabled = settings.consoleLoggingEnabled.first()
                LogManager.isCrashReportingEnabled = settings.crashReportingEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "Failed to load logging settings, using defaults")
            }
        }
    }
}
