package com.voxly.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            // Install splash screen inside setContent to access ViewModel
            val splashScreen = remember { installSplashScreen() }
            var isReady by remember { mutableStateOf(false) }

            val libraryScanViewModel: LibraryScanViewModel = hiltViewModel()
            val isRefreshing by libraryScanViewModel.isRefreshing.collectAsStateWithLifecycle()

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
