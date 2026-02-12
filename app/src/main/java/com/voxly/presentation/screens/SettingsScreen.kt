package com.voxly.presentation.screens

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onNavigateBack: () -> Unit,
    onNavigateToLogViewer: () -> Unit,
    onExportLogs: () -> Unit,
    onCleanupLogs: () -> Unit,
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
    val sourceEnabledMusicBrainz by viewModel.sourceEnabledMusicBrainz.collectAsState()
    val sourceEnabledITunes by viewModel.sourceEnabledITunes.collectAsState()
    val sourceEnabledNetease by viewModel.sourceEnabledNetease.collectAsState()
    val sourceEnabledQQMusic by viewModel.sourceEnabledQQMusic.collectAsState()
    
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
    val searchLimitOptions = remember {
        listOf(
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_online_metadata)) {
                SettingsSwitch(
                    title = stringResource(R.string.settings_source_musicbrainz),
                    subtitle = stringResource(R.string.settings_source_musicbrainz_subtitle),
                    checked = sourceEnabledMusicBrainz,
                    onCheckedChange = { viewModel.setSourceEnabledMusicBrainz(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_source_apple_music),
                    subtitle = stringResource(R.string.settings_source_apple_music_subtitle),
                    checked = sourceEnabledITunes,
                    onCheckedChange = { viewModel.setSourceEnabledITunes(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_source_netease),
                    subtitle = stringResource(R.string.settings_source_netease_subtitle),
                    checked = sourceEnabledNetease,
                    onCheckedChange = { viewModel.setSourceEnabledNetease(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_source_qq_music),
                    subtitle = stringResource(R.string.settings_source_qq_music_subtitle),
                    checked = sourceEnabledQQMusic,
                    onCheckedChange = { viewModel.setSourceEnabledQQMusic(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsDropdownRow(
                    title = stringResource(R.string.settings_online_search_limit),
                    subtitle = stringResource(R.string.settings_online_search_limit_subtitle),
                    selectedLabel = currentSearchLimit.value.toString(),
                    expanded = searchLimitExpanded,
                    onExpandedChange = { searchLimitExpanded = it }
                ) {
                    searchLimitOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.value.toString()) },
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
                    checked = LogManager.isLoggingEnabled,
                    onCheckedChange = { LogManager.isLoggingEnabled = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_logging_file),
                    subtitle = stringResource(R.string.settings_logging_file_subtitle),
                    checked = LogManager.isFileLoggingEnabled,
                    onCheckedChange = { LogManager.isFileLoggingEnabled = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsSwitch(
                    title = stringResource(R.string.settings_logging_crash),
                    subtitle = stringResource(R.string.settings_logging_crash_subtitle),
                    checked = LogManager.isCrashReportingEnabled,
                    onCheckedChange = { LogManager.isCrashReportingEnabled = it }
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

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsInfoRow(title = stringResource(R.string.settings_developer_label), value = stringResource(R.string.settings_developer_value))
            }
        }
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
    val value: Int
)

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
