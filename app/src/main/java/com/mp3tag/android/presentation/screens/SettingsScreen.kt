package com.mp3tag.android.presentation.screens

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.annotation.StringRes
import androidx.hilt.navigation.compose.hiltViewModel
import com.mp3tag.android.BuildConfig
import com.mp3tag.android.R
import com.mp3tag.android.presentation.viewmodel.SettingsViewModel

/**
 * Settings screen for application preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val scanQuality by viewModel.scanQuality.collectAsState()
    val savedLanguageTag by viewModel.languageTag.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_theme_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = stringResource(currentTheme.labelResId),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier.menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
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
                    }
                }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_language),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_language_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = stringResource(currentLanguageOption.labelResId),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier.menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            languageOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelResId)) },
                                    onClick = {
                                        viewModel.setLanguage(option.languageTag)
                                        languageExpanded = false
                                        // Recreate activity to apply language change
                                        activity?.recreate()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning Section
            SettingsSection(title = stringResource(R.string.settings_section_scanning)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_scan_quality),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_scan_quality_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = scanQualityExpanded,
                        onExpandedChange = { scanQualityExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = stringResource(currentScanQuality.labelResId),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scanQualityExpanded) },
                            modifier = Modifier.menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = scanQualityExpanded,
                            onDismissRequest = { scanQualityExpanded = false }
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
