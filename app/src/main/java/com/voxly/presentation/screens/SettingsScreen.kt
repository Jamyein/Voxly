package com.voxly.presentation.screens

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.presentation.viewmodel.SettingsViewModel

/**
 * Settings screen for application preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDirectoryManagement: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onCleanupLogs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val scanQuality by viewModel.scanQuality.collectAsState()
    val savedLanguageTag by viewModel.languageTag.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appleCountryCode by viewModel.appleCountryCode.collectAsState()
    val onlineSearchLimit by viewModel.onlineSearchLimit.collectAsState()
    val metadataSourceEnabledMusicBrainz by viewModel.metadataSourceEnabledMusicBrainz.collectAsState()
    val metadataSourceEnabledITunes by viewModel.metadataSourceEnabledITunes.collectAsState()
    val metadataSourceEnabledNetease by viewModel.metadataSourceEnabledNetease.collectAsState()
    val metadataSourceEnabledQQMusic by viewModel.metadataSourceEnabledQQMusic.collectAsState()
    val lyricsSourceEnabledMusicBrainz by viewModel.lyricsSourceEnabledMusicBrainz.collectAsState()
    val lyricsSourceEnabledITunes by viewModel.lyricsSourceEnabledITunes.collectAsState()
    val lyricsSourceEnabledNetease by viewModel.lyricsSourceEnabledNetease.collectAsState()
    val lyricsSourceEnabledQQMusic by viewModel.lyricsSourceEnabledQQMusic.collectAsState()
    val coverSourceEnabledMusicBrainz by viewModel.coverSourceEnabledMusicBrainz.collectAsState()
    val coverSourceEnabledITunes by viewModel.coverSourceEnabledITunes.collectAsState()
    val coverSourceEnabledNetease by viewModel.coverSourceEnabledNetease.collectAsState()
    val coverSourceEnabledQQMusic by viewModel.coverSourceEnabledQQMusic.collectAsState()
    val metadataSourcePriority by viewModel.metadataSourcePriority.collectAsState()
    val lyricsSourcePriority by viewModel.lyricsSourcePriority.collectAsState()
    val coverSourcePriority by viewModel.coverSourcePriority.collectAsState()
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsState()
    
    var languageExpanded by remember { mutableStateOf(false) }
    val languageOptions = remember {
        listOf(
            LanguageOption(R.string.settings_language_system, null),
            LanguageOption(R.string.settings_language_english, "en"),
            LanguageOption(R.string.settings_language_chinese_simplified, "zh-CN")
        )
    }
    
    // 使用 savedLanguageTag 或解析当前系统语言
    val effectiveLanguageTag = savedLanguageTag ?: resolveCurrentLanguageTag()
    val currentLanguageOption = languageOptions.firstOrNull {
        normalizeLanguageTag(it.languageTag) == normalizeLanguageTag(effectiveLanguageTag)
    } ?: languageOptions.first()

    var scanQualityExpanded by remember { mutableStateOf(false) }
    val scanQualityOptions = remember {
        listOf(
            ScanQualityOption("Fast", R.string.settings_scan_quality_fast),
            ScanQualityOption("Normal", R.string.settings_scan_quality_normal),
            ScanQualityOption("Accurate", R.string.settings_scan_quality_accurate)
        )
    }
    val currentScanQuality = scanQualityOptions.firstOrNull { it.value == scanQuality }
        ?: scanQualityOptions[1]
    var themeExpanded by remember { mutableStateOf(false) }
    val themeOptions = remember {
        listOf(
            ThemeModeOption("system", R.string.settings_theme_system),
            ThemeModeOption("light", R.string.settings_theme_light),
            ThemeModeOption("dark", R.string.settings_theme_dark)
        )
    }
    val currentTheme = themeOptions.firstOrNull { it.value == themeMode }
        ?: themeOptions.first()
    var appleCountryExpanded by remember { mutableStateOf(false) }
    val appleCountryOptions = remember {
        listOf(
            AppleCountryOption("us", R.string.settings_apple_country_us),
            AppleCountryOption("cn", R.string.settings_apple_country_cn),
            AppleCountryOption("hk", R.string.settings_apple_country_hk),
            AppleCountryOption("jp", R.string.settings_apple_country_jp),
            AppleCountryOption("gb", R.string.settings_apple_country_gb),
            AppleCountryOption("de", R.string.settings_apple_country_de),
            AppleCountryOption("fr", R.string.settings_apple_country_fr),
            AppleCountryOption("ca", R.string.settings_apple_country_ca),
            AppleCountryOption("au", R.string.settings_apple_country_au)
        )
    }
    val currentAppleCountry = appleCountryOptions.firstOrNull { it.value == appleCountryCode.lowercase() }
        ?: appleCountryOptions.first()

    var searchLimitExpanded by remember { mutableStateOf(false) }
    var showMetadataSourceDialog by remember { mutableStateOf(false) }
    var showLyricsSourceDialog by remember { mutableStateOf(false) }
    var showCoverSourceDialog by remember { mutableStateOf(false) }
    val searchLimitOptions = remember {
        listOf(
            SearchLimitOption(0, R.string.settings_online_search_limit_unlimited),
            SearchLimitOption(10),
            SearchLimitOption(25),
            SearchLimitOption(50)
        )
    }
    val currentSearchLimit = searchLimitOptions.firstOrNull { it.value == onlineSearchLimit }
        ?: searchLimitOptions[1]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                SettingsDropdownRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                    selectedLabel = stringResource(currentTheme.labelResId),
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it }
                ) {
                    themeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResId)) },
                            onClick = {
                                viewModel.setThemeMode(option.value)
                                themeExpanded = false
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                    checked = dynamicColors,
                    onCheckedChange = { viewModel.setDynamicColors(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Section
            SettingsSection(title = stringResource(R.string.settings_section_language)) {
                SettingsDropdownRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_subtitle),
                    selectedLabel = stringResource(currentLanguageOption.labelResId),
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = it }
                ) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResId)) },
                            onClick = {
                                viewModel.setLanguage(option.languageTag)
                                languageExpanded = false
                                activity?.recreate()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning Section
            SettingsSection(title = stringResource(R.string.settings_section_scanning)) {
                SettingsDropdownRow(
                    title = stringResource(R.string.settings_scan_quality),
                    subtitle = stringResource(R.string.settings_scan_quality_subtitle),
                    selectedLabel = stringResource(currentScanQuality.labelResId),
                    expanded = scanQualityExpanded,
                    onExpandedChange = { scanQualityExpanded = it }
                ) {
                    scanQualityOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResId)) },
                            onClick = {
                                viewModel.setScanQuality(option.value)
                                scanQualityExpanded = false
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_directory_management)) },
                    supportingContent = { Text(stringResource(R.string.settings_directory_management_subtitle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToDirectoryManagement() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_online_metadata)) {
                SettingsSubmenuRow(
                    title = stringResource(R.string.settings_source_group_metadata),
                    subtitle = stringResource(R.string.settings_source_group_metadata_subtitle),
                    onClick = { showMetadataSourceDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSubmenuRow(
                    title = stringResource(R.string.settings_source_group_lyrics),
                    subtitle = stringResource(R.string.settings_source_group_lyrics_subtitle),
                    onClick = { showLyricsSourceDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSubmenuRow(
                    title = stringResource(R.string.settings_source_group_cover),
                    subtitle = stringResource(R.string.settings_source_group_cover_subtitle),
                    onClick = { showCoverSourceDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsDropdownRow(
                    title = stringResource(R.string.settings_online_search_limit),
                    subtitle = stringResource(R.string.settings_online_search_limit_subtitle),
                    selectedLabel = currentSearchLimit.displayLabel(),
                    expanded = searchLimitExpanded,
                    onExpandedChange = { searchLimitExpanded = it }
                ) {
                    searchLimitOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayLabel()) },
                            onClick = {
                                viewModel.setOnlineSearchLimit(option.value)
                                searchLimitExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logging Section
            SettingsSection(title = stringResource(R.string.settings_section_logging)) {
                SettingsSwitch(
                    title = stringResource(R.string.settings_logging_enabled),
                    subtitle = stringResource(R.string.settings_logging_enabled_subtitle),
                    checked = loggingEnabled,
                    onCheckedChange = {
                        LogManager.isLoggingEnabled = it
                        viewModel.setLoggingEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_logging_file),
                    subtitle = stringResource(R.string.settings_logging_file_subtitle),
                    checked = fileLoggingEnabled,
                    onCheckedChange = {
                        LogManager.isFileLoggingEnabled = it
                        viewModel.setFileLoggingEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_logging_crash),
                    subtitle = stringResource(R.string.settings_logging_crash_subtitle),
                    checked = crashReportingEnabled,
                    onCheckedChange = {
                        LogManager.isCrashReportingEnabled = it
                        viewModel.setCrashReportingEnabled(it)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_logging_size)) },
                    supportingContent = { Text(LogManager.formatLogSize(LogManager.getLogDirectorySize())) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_logging_view)) },
                    supportingContent = { Text(stringResource(R.string.settings_logging_view_subtitle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onNavigateToLogViewer() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_logging_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_logging_export_subtitle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onExportLogs() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_logging_cleanup)) },
                    supportingContent = { Text(stringResource(R.string.settings_logging_cleanup_subtitle)) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onCleanupLogs() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ReplayGain Section
            var replayGainTargetLoudness by remember { mutableFloatStateOf(-18f) }
            var replayGainExpanded by remember { mutableStateOf(false) }
            
            SettingsSection(title = stringResource(R.string.replay_gain_settings)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.replay_gain_target_loudness)) },
                    supportingContent = { 
                        Text(stringResource(R.string.replay_gain_default_loudness))
                    },
                    trailingContent = {
                        Text(
                            text = String.format("%.1f LUFS", replayGainTargetLoudness),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
                
                if (replayGainExpanded) {
                    Slider(
                        value = replayGainTargetLoudness,
                        onValueChange = { replayGainTargetLoudness = it },
                        valueRange = -24f..-14f,
                        steps = 10,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.replay_gain_scanner_title)) },
                    supportingContent = { Text("View scan history and results") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { /* Navigate to scan history */ }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsInfoRow(title = stringResource(R.string.settings_developer_label), value = stringResource(R.string.settings_developer_value))
            }
        }
    }

    if (showMetadataSourceDialog) {
        SourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_metadata),
            priority = metadataSourcePriority,
            onDismiss = { showMetadataSourceDialog = false },
            onPriorityChange = viewModel::setMetadataSourcePriority,
            extraContent = {
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_musicbrainz),
                    subtitle = stringResource(R.string.settings_source_musicbrainz_subtitle),
                    checked = metadataSourceEnabledMusicBrainz,
                    onCheckedChange = { viewModel.setMetadataSourceEnabledMusicBrainz(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_apple_music),
                    subtitle = stringResource(R.string.settings_source_apple_music_subtitle),
                    checked = metadataSourceEnabledITunes,
                    onCheckedChange = { viewModel.setMetadataSourceEnabledITunes(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_netease),
                    subtitle = stringResource(R.string.settings_source_netease_subtitle),
                    checked = metadataSourceEnabledNetease,
                    onCheckedChange = { viewModel.setMetadataSourceEnabledNetease(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_qq_music),
                    subtitle = stringResource(R.string.settings_source_qq_music_subtitle),
                    checked = metadataSourceEnabledQQMusic,
                    onCheckedChange = { viewModel.setMetadataSourceEnabledQQMusic(it) }
                )
                HorizontalDivider()
                SettingsDropdownRow(
                    title = stringResource(R.string.settings_apple_country),
                    subtitle = stringResource(R.string.settings_apple_country_subtitle),
                    selectedLabel = stringResource(currentAppleCountry.labelResId),
                    expanded = appleCountryExpanded,
                    onExpandedChange = { appleCountryExpanded = it }
                ) {
                    appleCountryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResId)) },
                            onClick = {
                                viewModel.setAppleCountryCode(option.value)
                                appleCountryExpanded = false
                            }
                        )
                    }
                }
            }
        )
    }

    if (showLyricsSourceDialog) {
        SourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_lyrics),
            priority = lyricsSourcePriority,
            onDismiss = { showLyricsSourceDialog = false },
            onPriorityChange = viewModel::setLyricsSourcePriority,
            extraContent = {
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_musicbrainz),
                    subtitle = stringResource(R.string.settings_source_musicbrainz_subtitle),
                    checked = lyricsSourceEnabledMusicBrainz,
                    onCheckedChange = { viewModel.setLyricsSourceEnabledMusicBrainz(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_apple_music),
                    subtitle = stringResource(R.string.settings_source_apple_music_subtitle),
                    checked = lyricsSourceEnabledITunes,
                    onCheckedChange = { viewModel.setLyricsSourceEnabledITunes(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_netease),
                    subtitle = stringResource(R.string.settings_source_netease_subtitle),
                    checked = lyricsSourceEnabledNetease,
                    onCheckedChange = { viewModel.setLyricsSourceEnabledNetease(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_qq_music),
                    subtitle = stringResource(R.string.settings_source_qq_music_subtitle),
                    checked = lyricsSourceEnabledQQMusic,
                    onCheckedChange = { viewModel.setLyricsSourceEnabledQQMusic(it) }
                )
            }
        )
    }

    if (showCoverSourceDialog) {
        SourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_cover),
            priority = coverSourcePriority,
            onDismiss = { showCoverSourceDialog = false },
            onPriorityChange = viewModel::setCoverSourcePriority,
            extraContent = {
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_musicbrainz),
                    subtitle = stringResource(R.string.settings_source_musicbrainz_subtitle),
                    checked = coverSourceEnabledMusicBrainz,
                    onCheckedChange = { viewModel.setCoverSourceEnabledMusicBrainz(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_apple_music),
                    subtitle = stringResource(R.string.settings_source_apple_music_subtitle),
                    checked = coverSourceEnabledITunes,
                    onCheckedChange = { viewModel.setCoverSourceEnabledITunes(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_netease),
                    subtitle = stringResource(R.string.settings_source_netease_subtitle),
                    checked = coverSourceEnabledNetease,
                    onCheckedChange = { viewModel.setCoverSourceEnabledNetease(it) }
                )
                HorizontalDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_qq_music),
                    subtitle = stringResource(R.string.settings_source_qq_music_subtitle),
                    checked = coverSourceEnabledQQMusic,
                    onCheckedChange = { viewModel.setCoverSourceEnabledQQMusic(it) }
                )
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Text(text = value) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
private fun SettingsSubmenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = subtitle) },
        trailingContent = {
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

@Composable
private fun SourcePriorityDialog(
    title: String,
    priority: List<String>,
    onDismiss: () -> Unit,
    onPriorityChange: (List<String>) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_source_priority_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                priority.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(text = sourceToDisplayName(source))
                        Row {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val next = priority.toMutableList()
                                        val temp = next[index - 1]
                                        next[index - 1] = next[index]
                                        next[index] = temp
                                        onPriorityChange(next)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                            }
                            IconButton(
                                onClick = {
                                    if (index < priority.lastIndex) {
                                        val next = priority.toMutableList()
                                        val temp = next[index + 1]
                                        next[index + 1] = next[index]
                                        next[index] = temp
                                        onPriorityChange(next)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                            }
                        }
                    }
                }
                extraContent()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        }
    )
}

private fun sourceToDisplayName(source: String): String = when (source) {
    "itunes" -> "iTunes"
    "musicbrainz" -> "MusicBrainz"
    "netease" -> "NetEase"
    "qq_music" -> "QQ Music"
    else -> source
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownRow(
    title: String,
    subtitle: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .width(156.dp)
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    content = menuContent
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

private data class LanguageOption(
    @StringRes val labelResId: Int,
    val languageTag: String?
)

private data class ScanQualityOption(
    val value: String,
    @StringRes val labelResId: Int
)

private data class ThemeModeOption(
    val value: String,
    @StringRes val labelResId: Int
)

private data class AppleCountryOption(
    val value: String,
    @StringRes val labelResId: Int
)

private data class SearchLimitOption(
    val value: Int,
    @StringRes val labelResId: Int? = null
)

@Composable
private fun SearchLimitOption.displayLabel(): String {
    return labelResId?.let { stringResource(it) } ?: value.toString()
}

private fun resolveCurrentLanguageTag(): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) {
        return null
    }
    val firstTag = locales.toLanguageTags()
        .split(",")
        .firstOrNull()
        ?.trim()
        ?.ifBlank { null }
        ?: return null
    return when {
        firstTag.startsWith("zh", ignoreCase = true) -> "zh-CN"
        firstTag.startsWith("en", ignoreCase = true) -> "en"
        else -> firstTag
    }
}

private fun normalizeLanguageTag(tag: String?): String? = tag?.lowercase()
