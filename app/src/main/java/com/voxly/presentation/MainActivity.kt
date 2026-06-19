package com.voxly.presentation

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
            val isRefreshing by libraryScanViewModel.isRefreshing.collectAsStateWithLifecycle()

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

            LaunchedEffect(isRefreshing) {
                if (!isRefreshing) {
                    isReady = true
                }
            }
            splashScreen.setKeepOnScreenCondition { !isReady }

            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val dynamicColors by settingsViewModel.dynamicColors.collectAsStateWithLifecycle()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkTheme
            }

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
