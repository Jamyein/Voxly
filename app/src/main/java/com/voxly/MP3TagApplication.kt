package com.voxly

import android.app.Application
import android.content.Context
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.voxly.core.util.CrashHandler
import com.voxly.core.util.FileLoggingTree
import com.voxly.core.util.LogManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.data.local.worker.LibraryPeriodicScanWorker
import com.voxly.presentation.ui.coil.VoxlyImageLoader
import com.voxly.presentation.viewmodel.MediaStoreChangeWatcher
import java.util.concurrent.TimeUnit
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * Application class for MP3 Tag Editor.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MP3TagApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    private lateinit var fileLoggingTree: FileLoggingTree

    @Inject
    @Named("ApplicationScope")
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var musicLibraryCache: MusicLibraryCache

    @Inject
    lateinit var mediaStoreChangeWatcher: MediaStoreChangeWatcher

    override fun onCreate() {
        super.onCreate()
        @OptIn(ExperimentalComposeApi::class)
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true

        Timber.tag("Voxly").i("Application created")
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        applicationScope.launch(Dispatchers.IO) {
            initLogging()
        }

        warmUpCache()
        VoxlyImageLoader.getInstance(this)

        // Semi-automatic scanning layers (phased rollout):
        // Phase 1: MediaStore ContentObserver — detects audio changes while
        //          the app is alive, debounced to 2.5s.
        mediaStoreChangeWatcher.start()

        // Phase 3: WorkManager periodic scan — runs every 6h with
        //          charging + battery-not-low constraints. Covers background
        //          MediaStore events missed by the observer (Android 14+
        //          restriction) and SAF directory tree changes.
        val periodicScan = PeriodicWorkRequestBuilder<LibraryPeriodicScanWorker>(
            LibraryPeriodicScanWorker.INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LibraryPeriodicScanWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicScan
        )
        Timber.tag(TAG).i("Periodic scan scheduled: ${LibraryPeriodicScanWorker.INTERVAL_HOURS}h")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_COMPLETE,
            TRIM_MEMORY_MODERATE -> {
                CoverUriProvider.clearCaches()
            }
        }
    }

    companion object {
        private const val TAG = "MP3TagApplication"
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return VoxlyImageLoader.getInstance(context).imageLoader
    }

    private suspend fun initLogging() {
        LogManager.init(this)
        loadLoggingSettings()

        fileLoggingTree = FileLoggingTree()
        Timber.plant(fileLoggingTree)

        if (LogManager.isLoggingEnabled) {
            fileLoggingTree.cleanupExcessLogs()
        }

        Timber.i("Application started - Version ${BuildConfig.VERSION_NAME}")
    }

    private suspend fun loadLoggingSettings() {
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

    private fun warmUpCache() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                musicLibraryCache.warmUp()
            } catch (e: Exception) {
                Timber.w(e, "Failed to warm up music library cache")
            }
        }
    }
}
