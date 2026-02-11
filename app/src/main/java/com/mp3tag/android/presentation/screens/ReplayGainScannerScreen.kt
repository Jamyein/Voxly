package com.mp3tag.android.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mp3tag.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Placeholder screen for ReplayGain scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayGainScannerScreen(
    filePaths: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String) -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentFile by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replay_gain_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Equalizer,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.replay_gain_scanner_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.files_queued, filePaths.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isScanning) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.replay_gain_scan_progress, (progress * 100).toInt()),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isScanning = false
                        }
                    ) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            } else {
                Button(
                    onClick = {
                        isScanning = true
                        // Simulate scanning progress
                        scope.launch {
                            filePaths.forEachIndexed { index, path ->
                                currentFile = path.substringAfterLast("/")
                                kotlinx.coroutines.delay(500)
                                progress = (index + 1).toFloat() / filePaths.size
                            }
                            isScanning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.start_scanning))
                }
            }
        }
    }
}
