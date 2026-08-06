package com.voxly.presentation

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.presentation.navigation.MP3TagNavHost
import com.voxly.presentation.theme.MP3TagTheme
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import com.voxly.presentation.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the MP3 Tag Editor application.
 * Uses Jetpack Compose for the UI layer with Material Design 3.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        // Disable system-drawn translucent scrim behind the navigation bar so app
        // content extends truly edge-to-edge (no white/colored band on gesture nav area).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            var isReady by remember { mutableStateOf(false) }

            val libraryScanViewModel: LibraryScanViewModel = hiltViewModel()

            // Trigger an incremental scan whenever the activity returns to the
            // foreground. SAF-picked whitelist directories have no filesystem
            // change notification, so the MediaStore observer never fires for
            // external deletions in them; this covers the "delete in the
            // system file manager → switch back to Voxly" flow. Throttled
            // inside the ViewModel to coalesce rapid resume/pause bursts.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        libraryScanViewModel.refreshOnResume()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Keep splash screen visible while initializing
            LaunchedEffect(Unit) {
                libraryScanViewModel.initializeApp()
            }

            // Release the splash as soon as the library's initial build finished
            // (data rendered from cache, or empty on first install). This covers
            // both cases: cached data shows immediately, and a fresh install shows
            // the empty state while the background scan populates it.
            val libraryInitialized by libraryScanViewModel.libraryInitialized.collectAsStateWithLifecycle()
            LaunchedEffect(libraryInitialized) {
                if (libraryInitialized) {
                    isReady = true
                }
            }
            splashScreen.setKeepOnScreenCondition { !isReady }

            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val dynamicColors = settingsUiState.dynamicColors
            val themeMode = settingsUiState.themeMode
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkTheme
            }

            // In-app language switching: provide a Resources override so ALL Compose
            // resource consumers (stringResource / colorResource / stringArrayResource …)
            // recompose with the selected locale instantly — no activity recreation, no
            // navigation reset. null (follow system) = the activity's own Resources, which
            // follows the system config. AppCompatDelegate.setApplicationLocales() is still
            // called on switch (SettingsViewModel.setLanguage) for the official per-app
            // language sync (Android 13+ system settings), and android:configChanges on the
            // activity makes AppCompat update resources in place instead of recreating.
            val context = LocalContext.current
            val savedLanguageTag = settingsUiState.savedLanguageTag
            val localizedResources = remember(savedLanguageTag, context) {
                if (savedLanguageTag == null) {
                    context.resources
                } else {
                    val config = Configuration(context.resources.configuration).apply {
                        setLocales(android.os.LocaleList.forLanguageTags(savedLanguageTag))
                    }
                    context.createConfigurationContext(config).resources
                }
            }

            CompositionLocalProvider(LocalResources provides localizedResources) {
                MP3TagTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColors
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MP3TagNavHost()
                    }
                }
            }
        }
    }
}
