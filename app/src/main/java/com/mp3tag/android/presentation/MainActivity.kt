package com.mp3tag.android.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import com.mp3tag.android.presentation.navigation.MP3TagNavHost
import com.mp3tag.android.presentation.theme.MP3TagTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the MP3 Tag Editor application.
 * Uses Jetpack Compose for the UI layer with Material Design 3.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MP3TagTheme {
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
