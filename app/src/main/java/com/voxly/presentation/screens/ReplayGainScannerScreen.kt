package com.voxly.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.repository.ScanQuality
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ReplayGain scanner screen for analyzing audio files and calculating gain values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayGainScannerScreen(
    filePaths: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String) -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentFile by remember { mutableStateOf("") }
    var scannedCount by remember { mutableIntStateOf(0) }
    var successfulCount by remember { mutableIntStateOf(0) }
    var scanType by remember { mutableStateOf(ScanType.TRACK_ONLY) }
    var targetLoudness by remember { mutableFloatStateOf(-18f) }
    var scanQuality by remember { mutableStateOf(ScanQuality.NORMAL) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replay_gain_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isScanning) {
                // Scanning UI
                ScanningContent(
                    progress = progress,
                    currentFile = currentFile,
                    scannedCount = scannedCount,
                    totalCount = filePaths.size,
                    onCancel = { isScanning = false }
                )
            } else {
                // Configuration UI
                ConfigurationContent(
                    fileCount = filePaths.size,
                    scanType = scanType,
                    onScanTypeChange = { scanType = it },
                    targetLoudness = targetLoudness,
                    onTargetLoudnessChange = { targetLoudness = it },
                    scanQuality = scanQuality,
                    onScanQualityChange = { scanQuality = it },
                    onStartScan = {
                        isScanning = true
                        scannedCount = 0
                        successfulCount = 0
                        progress = 0f
                        // Simulate scanning
                        scope.launch {
                            filePaths.forEachIndexed { index, path ->
                                if (!isScanning) return@launch // Check if cancelled
                                currentFile = path.substringAfterLast("/")
                                delay(500)
                                scannedCount = index + 1
                                progress = (index + 1).toFloat() / filePaths.size
                                successfulCount++
                            }
                            isScanning = false
                        }
                    },
                    onShowSettings = { showSettingsSheet = true }
                )
            }

            // Settings ModalBottomSheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ScanSettingsSheet(
                        scanType = scanType,
                        onScanTypeChange = { scanType = it },
                        targetLoudness = targetLoudness,
                        onTargetLoudnessChange = { targetLoudness = it },
                        scanQuality = scanQuality,
                        onScanQualityChange = { scanQuality = it },
                        onDismiss = { showSettingsSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningContent(
    progress: Float,
    currentFile: String,
    scannedCount: Int,
    totalCount: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = appIconPainter(AppIcon.Equalizer),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.replay_gain_scanning),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$scannedCount / $totalCount ${stringResource(R.string.files_queued, totalCount)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = currentFile,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Text(
            text = stringResource(R.string.replay_gain_scan_progress, (progress * 100).toInt()),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.dialog_cancel))
        }
    }
}

@Composable
private fun ConfigurationContent(
    fileCount: Int,
    scanType: ScanType,
    onScanTypeChange: (ScanType) -> Unit,
    targetLoudness: Float,
    onTargetLoudnessChange: (Float) -> Unit,
    scanQuality: ScanQuality,
    onScanQualityChange: (ScanQuality) -> Unit,
    onStartScan: () -> Unit,
    onShowSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Icon(
            painter = appIconPainter(AppIcon.Equalizer),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.replay_gain_scanner_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Text(
            text = stringResource(R.string.files_queued, fileCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Scan Type Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.replay_gain_scan_type),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ScanTypeOption(
                    selected = scanType == ScanType.TRACK_ONLY,
                    title = stringResource(R.string.replay_gain_scan_track_only),
                    description = "Calculate gain for each track independently",
                    onSelect = { onScanTypeChange(ScanType.TRACK_ONLY) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ScanTypeOption(
                    selected = scanType == ScanType.TRACK_AND_ALBUM,
                    title = stringResource(R.string.replay_gain_scan_album),
                    description = "Calculate track and album gain (requires album grouping)",
                    onSelect = { onScanTypeChange(ScanType.TRACK_AND_ALBUM) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Loudness Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.replay_gain_target_loudness),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = String.format("%.1f LUFS", targetLoudness),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = targetLoudness,
                    onValueChange = onTargetLoudnessChange,
                    valueRange = -24f..-14f,
                    steps = 10,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = stringResource(R.string.replay_gain_loudness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan Quality Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.replay_gain_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scanQuality == ScanQuality.FAST,
                        onClick = { onScanQualityChange(ScanQuality.FAST) },
                        label = { Text(stringResource(R.string.replay_gain_quality_fast)) }
                    )
                    FilterChip(
                        selected = scanQuality == ScanQuality.NORMAL,
                        onClick = { onScanQualityChange(ScanQuality.NORMAL) },
                        label = { Text(stringResource(R.string.replay_gain_quality_normal)) }
                    )
                    FilterChip(
                        selected = scanQuality == ScanQuality.ACCURATE,
                        onClick = { onScanQualityChange(ScanQuality.ACCURATE) },
                        label = { Text(stringResource(R.string.replay_gain_quality_accurate)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Scan Button
        Button(
            onClick = onStartScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(painter = appIconPainter(AppIcon.Equalizer), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.replay_gain_scan),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ScanTypeOption(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanSettingsSheet(
    scanType: ScanType,
    onScanTypeChange: (ScanType) -> Unit,
    targetLoudness: Float,
    onTargetLoudnessChange: (Float) -> Unit,
    scanQuality: ScanQuality,
    onScanQualityChange: (ScanQuality) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.replay_gain_settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Scan Type
        Text(
            text = stringResource(R.string.replay_gain_scan_type),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        ScanTypeOption(
            selected = scanType == ScanType.TRACK_ONLY,
            title = stringResource(R.string.replay_gain_scan_track_only),
            description = "Calculate gain for each track independently",
            onSelect = { onScanTypeChange(ScanType.TRACK_ONLY) }
        )

        ScanTypeOption(
            selected = scanType == ScanType.TRACK_AND_ALBUM,
            title = stringResource(R.string.replay_gain_scan_album),
            description = "Calculate track and album gain",
            onSelect = { onScanTypeChange(ScanType.TRACK_AND_ALBUM) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target Loudness
        Text(
            text = stringResource(R.string.replay_gain_target_loudness),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = String.format("%.1f LUFS", targetLoudness),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Slider(
            value = targetLoudness,
            onValueChange = onTargetLoudnessChange,
            valueRange = -24f..-14f,
            steps = 10
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quality
        Text(
            text = stringResource(R.string.replay_gain_quality),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = scanQuality == ScanQuality.FAST,
                onClick = { onScanQualityChange(ScanQuality.FAST) },
                label = { Text(stringResource(R.string.replay_gain_quality_fast)) }
            )
            FilterChip(
                selected = scanQuality == ScanQuality.NORMAL,
                onClick = { onScanQualityChange(ScanQuality.NORMAL) },
                label = { Text(stringResource(R.string.replay_gain_quality_normal)) }
            )
            FilterChip(
                selected = scanQuality == ScanQuality.ACCURATE,
                onClick = { onScanQualityChange(ScanQuality.ACCURATE) },
                label = { Text(stringResource(R.string.replay_gain_quality_accurate)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.dialog_ok))
        }
    }
}

private enum class ScanType {
    TRACK_ONLY,
    TRACK_AND_ALBUM
}
