package com.voxly.presentation.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.voxly.data.local.SafTreeWatcher
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Process-lifetime watcher that requests a library refresh via
 * [LibraryRepository.refresh] when MediaStore audio content changes are
 * observed.
 *
 * Pattern follows the mainstream open-source music apps (Auxio, Phonograph,
 * NewPipe): a long-lived [ContentObserver] is registered on
 * [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] with `notifyForDescendants = true`,
 * each change event is pushed into a [MutableSharedFlow] with
 * [BufferOverflow.DROP_OLDEST] (so the binder-thread callback never blocks),
 * and a debounced collector routes the burst to the shared refresh entry point.
 *
 * The observer deliberately **does not** classify add/update/delete — that work
 * is left to the existing incremental scan in [AudioFileScanner], which
 * already does mtime diffing against the Room cache.
 *
 * Threading: the ContentObserver is constructed with the main looper [Handler]
 * so `onChange` is cheap and `tryEmit` is lock-free. The downstream debounce +
 * collector runs on [appScope] (the application-scoped CoroutineScope) so
 * it survives individual ViewModel / Activity lifecycles.
 */
@OptIn(FlowPreview::class)
@Singleton
class MediaStoreChangeWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val safTreeWatcher: SafTreeWatcher,
    private val settingsDataStore: SettingsDataStore,
    @Named("ApplicationScope") private val appScope: CoroutineScope
) {
    private val _changes = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val registered = AtomicBoolean(false)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // selfChange == true means OUR process wrote the change — ignore
            // to avoid echo loops. Voxly never writes to MediaStore today, so
            // this is a forward-compat guard for future write paths.
            if (selfChange) return
            _changes.tryEmit(Unit)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (selfChange) return
            _changes.tryEmit(Unit)
        }
    }

    /**
     * Register the ContentObserver and start the debounced collector. Idempotent
     * — calling more than once is a no-op. Should be called once from
     * `MP3TagApplication.onCreate()`.
     */
    fun start() {
        if (!registered.compareAndSet(false, true)) return
        val resolver: ContentResolver = context.contentResolver
        resolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer
        )
        appScope.launch {
            _changes
                .debounce(DEBOUNCE_MS)
                .collect {
                    Timber.tag(TAG).d("MediaStore change → global refresh → SAF walk")
                    libraryRepository.refresh(RefreshStrategy.INCREMENTAL)

                    // Phase 4: detect changes in SAF-picked directories that
                    // MediaStore observer doesn't cover (USB drives, SD roots).
                    try {
                        val uris = settingsDataStore.selectedDirectoryUris.first()
                        safTreeWatcher.detectChanges(uris)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "SAF walk failed")
                    }
                }
        }
        Timber.tag(TAG).i("MediaStore observer started")
    }

    /**
     * Unregister and stop. Optional — process death cleans up automatically.
     * Provided for symmetry / future lifecycle-aware use.
     */
    fun stop() {
        if (!registered.compareAndSet(true, false)) return
        context.contentResolver.unregisterContentObserver(observer)
        Timber.tag(TAG).i("MediaStore observer stopped")
    }

    companion object {
        private const val TAG = "MediaStoreWatcher"
        private const val DEBOUNCE_MS = 2_500L
    }
}
