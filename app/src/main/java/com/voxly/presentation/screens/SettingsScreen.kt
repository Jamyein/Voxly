package com.voxly.presentation.screens

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.annotation.StringRes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.presentation.components.CardPosition
import com.voxly.presentation.components.SettingsItemCard
import com.voxly.presentation.components.SettingsSection
import com.voxly.presentation.viewmodel.SettingsViewModel
import com.voxly.domain.model.DataSourceConfig
import com.voxly.domain.model.DataSourceType
import com.voxly.domain.model.SourceConfigurations
import com.voxly.domain.model.SourceTypeConfig
import kotlin.math.roundToInt

// ==================== Data Classes ====================

data class LanguageOption(
    @StringRes val labelResId: Int,
    val languageTag: String?
)

data class AppleCountryOption(
    val value: String,
    @StringRes val labelResId: Int
)

data class SearchLimitOption(
    val value: Int,
    @StringRes val labelResId: Int? = null
)

data class ScanModeOption(
    val value: String,
    @StringRes val labelResId: Int
)

data class ConnectedIconOption<T>(
    val value: T,
    val icon: ImageVector,
    val tooltip: String
)

private fun connectedGroupWidth(optionCount: Int): Dp {
    val perButtonBase = 40.dp
    val spacing = 2.dp
    val count = optionCount.coerceAtLeast(1)
    val width = perButtonBase * count + spacing * (count - 1)
    return width.coerceIn(124.dp, 220.dp)
}

// ==================== Helper Functions ====================

fun resolveCurrentLanguageTag(): String? {
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

fun normalizeLanguageTag(tag: String?): String? = tag?.lowercase()



fun sourceToDisplayName(source: String): String = when (source) {
    "itunes" -> "iTunes"
    "musicbrainz" -> "MusicBrainz"
    "netease" -> "NetEase"
    "qq_music" -> "QQ Music"
    else -> source
}

/**
 * Check if a source has extra options (like country code for iTunes)
 */
fun sourceHasExtraOptions(sourceId: String): Boolean = sourceId == "itunes"

/**
 * Get extra option display label for a source
 */
fun getExtraOptionLabel(sourceId: String): String = when (sourceId) {
    "itunes" -> "Country Code"
    else -> ""
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun <T> ConnectedIconButtonGroup(
    options: List<ConnectedIconOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val mediumHeight = ButtonDefaults.MediumContainerHeight

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val tooltipState = rememberTooltipState()
            Box(modifier = Modifier.weight(1f)) {
                TooltipBox(
                    modifier = Modifier.fillMaxWidth(),
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip {
                            Text(option.tooltip)
                        }
                    },
                    state = tooltipState
                ) {
                    ToggleButton(
                        checked = option.value == selectedValue,
                        onCheckedChange = { checked ->
                            if (checked) onSelected(option.value)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mediumHeight)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.tooltip
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data class for source item state in the draggable dialog
 */
data class SourceItemState(
    val sourceId: String,
    val enabled: Boolean,
    val extraOptions: Map<String, String>,
    val expanded: Boolean = false
)

/**
 * Draggable source priority dialog with inline switches and extra options.
 * Each source item shows: sequence number, drag handle, source name, switch, and more options menu.
 * Supports drag-and-drop reordering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraggableSourcePriorityDialog(
    title: String,
    sourceTypeConfig: SourceTypeConfig,
    appleCountryOptions: List<AppleCountryOption>,
    currentAppleCountry: AppleCountryOption,
    onDismiss: () -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onSourceReorder: (List<String>) -> Unit,
    onAppleCountryChange: (String) -> Unit
) {
    // Get sources directly from sourceTypeConfig (observing StateFlow changes)
    val sources = remember(sourceTypeConfig) {
        sourceTypeConfig.sources.sortedBy { it.order }
    }

    // Local drag visual state - only used for visual feedback during drag
    // This is set when drag starts and cleared when drag ends
    var localDragList by remember { mutableStateOf<List<SourceItemState>?>(null) }

    // Track original drag start position
    var originalDragIndex by remember { mutableStateOf<Int?>(null) }

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val itemHeight = 80.dp // Approximate item height
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Use local drag list if available, otherwise use the actual sources
    // Note: The position in the list (index) represents the order, not a separate field
    val displayList = localDragList ?: sources.map { source ->
        SourceItemState(
            sourceId = source.sourceId,
            enabled = source.enabled,
            extraOptions = source.extraOptions
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_source_priority_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                displayList.forEachIndexed { index, sourceState ->
                    val isDragging = draggedIndex == index

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDragging) {
                                    Modifier
                                        .zIndex(1f)
                                        .offset { IntOffset(0, dragOffset.roundToInt()) }
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        originalDragIndex = index
                                        draggedIndex = index
                                        dragOffset = 0f
                                        // Initialize local drag list for visual feedback
                                        localDragList = displayList.toList()
                                    },
                                    onDragEnd = {
                                        // Use draggedIndex (current position in list) to check if position changed
                                        // draggedIndex is updated during drag to reflect the actual position in localDragList
                                        if (originalDragIndex != null && originalDragIndex != draggedIndex) {
                                            // Save the reordered list
                                            onSourceReorder(localDragList?.map { it.sourceId } ?: displayList.map { it.sourceId })
                                        }
                                        // Clear local drag list
                                        localDragList = null
                                        originalDragIndex = null
                                        draggedIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        // Clear local drag list
                                        localDragList = null
                                        originalDragIndex = null
                                        draggedIndex = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        // Calculate target position
                                        val offsetInItems = dragOffset / with(density) { itemHeight.toPx() }
                                        val newTargetIndex = (draggedIndex!! + offsetInItems.roundToInt())
                                            .coerceIn(0, localDragList!!.lastIndex)
                                        if (newTargetIndex != index && newTargetIndex in localDragList!!.indices) {
                                            // Swap items in the list for visual feedback
                                            val newList = localDragList!!.toMutableList()
                                            val item = newList.removeAt(draggedIndex!!)
                                            newList.add(newTargetIndex, item)
                                            localDragList = newList
                                            draggedIndex = newTargetIndex
                                            dragOffset = 0f
                                        }
                                    }
                                )
                            },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Main row: sequence, drag handle, name, switch, reorder buttons, more menu
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sequence number
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(24.dp)
                                )

                                // Drag handle icon (visual indicator for draggable)
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.settings_drag_handle),
                                    tint = if (isDragging) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                // Source name
                                Text(
                                    text = sourceToDisplayName(sourceState.sourceId),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )

                                // Switch
                                Switch(
                                    checked = sourceState.enabled,
                                    onCheckedChange = { enabled ->
                                        // Directly trigger ViewModel callback for real-time save
                                        onSourceEnabledChange(sourceState.sourceId, enabled)
                                    }
                                )

                                // More options menu button
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        // Enable/Disable toggle
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (sourceState.enabled) "禁用" else "启用"
                                                )
                                            },
                                            onClick = {
                                                // Directly trigger ViewModel callback for real-time save
                                                onSourceEnabledChange(sourceState.sourceId, !sourceState.enabled)
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (sourceState.enabled)
                                                        Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                    contentDescription = null
                                                )
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Extra options (iTunes country code)
                                        if (sourceHasExtraOptions(sourceState.sourceId)) {
                                            Text(
                                                text = stringResource(R.string.settings_apple_country),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                            appleCountryOptions.forEach { option ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(option.labelResId)) },
                                                    onClick = {
                                                        onAppleCountryChange(option.value)
                                                        showMenu = false
                                                    },
                                                    trailingIcon = {
                                                        if (option.value == currentAppleCountry.value) {
                                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Drag hint
                            if (!isDragging) {
                                Text(
                                    text = "长按拖拽排序",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        }
    )
}

// ==================== Composable Helpers ====================

// SettingsSection is imported from com.voxly.presentation.components

@Composable
fun SettingsSwitch(
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
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    )
}

@Composable
fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    valueLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier else Modifier.alpha(0.38f)
            )
    ) {
        ListItem(
            headlineContent = { Text(text = title) },
            supportingContent = { Text(text = subtitle) },
            trailingContent = {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun SettingsInfoRow(title: String, value: String) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Text(text = value) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f))
    )
}

@Composable
fun SettingsSubmenuRow(
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
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
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
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    content = menuContent
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerSourceSearchLimitRow(
    title: String,
    currentLimit: Int,
    searchLimitOptions: List<SearchLimitOption>,
    onLimitChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = searchLimitOptions.firstOrNull { it.value == currentLimit }
        ?: searchLimitOptions.firstOrNull { it.value == 0 }
        ?: searchLimitOptions[0]
    val displayLabel = if (currentLimit <= 0) {
        stringResource(R.string.settings_online_search_limit_per_source_subtitle)
    } else {
        currentOption.displayLabel()
    }

    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Text(
                text = displayLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = if (currentLimit <= 0) stringResource(R.string.settings_online_search_limit_unlimited) else currentLimit.toString(),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .width(120.dp)
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    content = {
                        searchLimitOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayLabel()) },
                                onClick = {
                                    onLimitChange(option.value)
                                    expanded = false
                                }
                            )
                        }
                    }
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    )
}

@Composable
fun SourcePriorityDialog(
    title: String,
    priority: List<String>,
    onDismiss: () -> Unit,
    onPriorityChange: (List<String>) -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_source_priority_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                priority.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

@Composable
fun SearchLimitDialog(
    globalLimit: Int,
    musicBrainzLimit: Int,
    itunesLimit: Int,
    neteaseLimit: Int,
    qqMusicLimit: Int,
    searchLimitOptions: List<SearchLimitOption>,
    onDismiss: () -> Unit,
    onGlobalLimitChange: (Int) -> Unit,
    onMusicBrainzLimitChange: (Int) -> Unit,
    onItunesLimitChange: (Int) -> Unit,
    onNeteaseLimitChange: (Int) -> Unit,
    onQQMusicLimitChange: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.settings_online_search_limits_submenu)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit),
                    currentLimit = globalLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onGlobalLimitChange
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Per-source limits
                Text(
                    text = stringResource(R.string.settings_online_search_limit_subtitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // MusicBrainz
                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_musicbrainz),
                    currentLimit = musicBrainzLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onMusicBrainzLimitChange
                )

                // iTunes
                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_itunes),
                    currentLimit = itunesLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onItunesLimitChange
                )

                // NetEase
                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_netease),
                    currentLimit = neteaseLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onNeteaseLimitChange
                )

                // QQ Music
                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_qq_music),
                    currentLimit = qqMusicLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onQQMusicLimitChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLimitDropdown(
    currentLimit: Int,
    searchLimitOptions: List<SearchLimitOption>,
    onLimitChange: (Int) -> Unit
) {
    ConnectedIconButtonGroup(
        options = searchLimitOptions.map { option ->
            ConnectedIconOption(
                value = option.value,
                icon = when (option.value) {
                    0 -> Icons.Default.AllInclusive
                    10 -> Icons.Default.LooksOne
                    25 -> Icons.Default.LooksTwo
                    else -> Icons.Default.Looks3
                },
                tooltip = option.displayLabel()
            )
        },
        selectedValue = currentLimit,
        onSelected = onLimitChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLimitRow(
    text: String,
    currentLimit: Int,
    searchLimitOptions: List<SearchLimitOption>,
    onLimitChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            SearchLimitDropdown(
                currentLimit = currentLimit,
                searchLimitOptions = searchLimitOptions,
                onLimitChange = onLimitChange
            )
        }
    }
}

@Composable
fun SearchLimitOption.displayLabel(): String {
    return labelResId?.let { stringResource(it) } ?: value.toString()
}

// ==================== Main Settings Screen ====================

/**
 * Settings screen for application preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDirectoryManagement: () -> Unit = {},
    onNavigateToScanDirectorySettings: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onCleanupLogs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val dynamicColors by viewModel.dynamicColors.collectAsState()
    val savedLanguageTag by viewModel.languageTag.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appleCountryCode by viewModel.appleCountryCode.collectAsState()
    val onlineSearchLimit by viewModel.onlineSearchLimit.collectAsState()
    val onlineSearchLimitMusicBrainz by viewModel.onlineSearchLimitMusicBrainz.collectAsState()
    val onlineSearchLimitITunes by viewModel.onlineSearchLimitITunes.collectAsState()
    val onlineSearchLimitNetease by viewModel.onlineSearchLimitNetease.collectAsState()
    val onlineSearchLimitQQMusic by viewModel.onlineSearchLimitQQMusic.collectAsState()
    val metadataSourceEnabledMusicBrainz by viewModel.metadataSourceEnabledMusicBrainz.collectAsState()
    val metadataSourceEnabledITunes by viewModel.metadataSourceEnabledITunes.collectAsState()
    val metadataSourceEnabledNetease by viewModel.metadataSourceEnabledNetease.collectAsState()
    val metadataSourceEnabledQQMusic by viewModel.metadataSourceEnabledQQMusic.collectAsState()
    val lyricsSourceEnabledMusicBrainz by viewModel.lyricsSourceEnabledMusicBrainz.collectAsState()
    val lyricsSourceEnabledNetease by viewModel.lyricsSourceEnabledNetease.collectAsState()
    val lyricsSourceEnabledQQMusic by viewModel.lyricsSourceEnabledQQMusic.collectAsState()
    val coverSourceEnabledMusicBrainz by viewModel.coverSourceEnabledMusicBrainz.collectAsState()
    val coverSourceEnabledITunes by viewModel.coverSourceEnabledITunes.collectAsState()
    val coverSourceEnabledNetease by viewModel.coverSourceEnabledNetease.collectAsState()
    val coverSourceEnabledQQMusic by viewModel.coverSourceEnabledQQMusic.collectAsState()
    val sourceConfigurations by viewModel.sourceConfigurations.collectAsState()
    val metadataSourcePriority by viewModel.metadataSourcePriority.collectAsState()
    val lyricsSourcePriority by viewModel.lyricsSourcePriority.collectAsState()
    val coverSourcePriority by viewModel.coverSourcePriority.collectAsState()
    val loggingEnabled by viewModel.loggingEnabled.collectAsState()
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    val consoleLoggingEnabled by viewModel.consoleLoggingEnabled.collectAsState()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsState()
    val replayGainTargetLoudness by viewModel.replayGainTargetLoudness.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val minDurationFilterEnabled by viewModel.minDurationFilterEnabled.collectAsState()

    
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

    var showMetadataSourceDialog by remember { mutableStateOf(false) }
    var showLyricsSourceDialog by remember { mutableStateOf(false) }
    var showCoverSourceDialog by remember { mutableStateOf(false) }
    var showSearchLimitsDialog by remember { mutableStateOf(false) }

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

    val scanModeOptions = remember {
        listOf(
            ScanModeOption("TRACK_ONLY", R.string.settings_scan_mode_track_only),
            ScanModeOption("SINGLE_ALBUM", R.string.settings_scan_mode_album_only),
            ScanModeOption("ALBUMS", R.string.settings_scan_mode_track_and_album)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(),
                windowInsets = WindowInsets(0.dp) 
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance Section
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                // Theme Segmented Buttons - Single item
                SettingsItemCard(position = CardPosition.FIRST) {
                    ListItem(
                        headlineContent = {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_theme))
                            }
                        },
                        trailingContent = {
                            ConnectedIconButtonGroup(
                                options = listOf(
                                    ConnectedIconOption("system", Icons.Default.BrightnessAuto, stringResource(R.string.settings_theme_system)),
                                    ConnectedIconOption("light", Icons.Default.LightMode, stringResource(R.string.settings_theme_light)),
                                    ConnectedIconOption("dark", Icons.Default.DarkMode, stringResource(R.string.settings_theme_dark))
                                ),
                                selectedValue = themeMode,
                                onSelected = viewModel::setThemeMode
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    )
                }

                // SettingsSwitch - Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = dynamicColors,
                        onCheckedChange = { viewModel.setDynamicColors(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language Section
            SettingsSection(title = stringResource(R.string.settings_section_language)) {
                // Single item - use SINGLE position
                SettingsItemCard(position = CardPosition.SINGLE) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning Section
            SettingsSection(title = stringResource(R.string.settings_section_scanning)) {
                // Scan Mode Segmented Buttons - Single item
                SettingsItemCard(position = CardPosition.FIRST) {
                    ListItem(
                        headlineContent = {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_scan_mode))
                            }
                        },
                        trailingContent = {
                            ConnectedIconButtonGroup(
                                options = scanModeOptions.map { option ->
                                    ConnectedIconOption(
                                        value = option.value,
                                        icon = when (option.value) {
                                            "TRACK_ONLY" -> Icons.Default.MusicNote
                                            "SINGLE_ALBUM" -> Icons.Default.Album
                                            else -> Icons.Default.LibraryMusic
                                        },
                                        tooltip = stringResource(option.labelResId)
                                    )
                                },
                                selectedValue = scanMode,
                                onSelected = viewModel::setScanMode
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    )
                }

                // SettingsSwitch - Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_min_duration_filter),
                        subtitle = stringResource(R.string.settings_min_duration_filter_subtitle),
                        checked = minDurationFilterEnabled,
                        onCheckedChange = { viewModel.setMinDurationFilterEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_scan_directory_settings)) {
                // Single item - use SINGLE position
                SettingsItemCard(position = CardPosition.SINGLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_scan_directory_settings)) },
                        supportingContent = { Text(stringResource(R.string.settings_scan_directory_settings_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { onNavigateToScanDirectorySettings() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection(title = stringResource(R.string.settings_section_online_metadata)) {
                // First item
                SettingsItemCard(position = CardPosition.FIRST) {
                    SettingsSubmenuRow(
                        title = stringResource(R.string.settings_source_group_metadata),
                        subtitle = stringResource(R.string.settings_source_group_metadata_subtitle),
                        onClick = { showMetadataSourceDialog = true }
                    )
                }
                // Middle items
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    SettingsSubmenuRow(
                        title = stringResource(R.string.settings_source_group_lyrics),
                        subtitle = stringResource(R.string.settings_source_group_lyrics_subtitle),
                        onClick = { showLyricsSourceDialog = true }
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    SettingsSubmenuRow(
                        title = stringResource(R.string.settings_source_group_cover),
                        subtitle = stringResource(R.string.settings_source_group_cover_subtitle),
                        onClick = { showCoverSourceDialog = true }
                    )
                }
                // Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    SettingsSubmenuRow(
                        title = stringResource(R.string.settings_online_search_limits_submenu),
                        subtitle = stringResource(R.string.settings_online_search_limits_submenu_subtitle),
                        onClick = { showSearchLimitsDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logging Section
            SettingsSection(title = stringResource(R.string.settings_section_logging)) {
                // First item
                SettingsItemCard(position = CardPosition.FIRST) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_logging_enabled),
                        subtitle = stringResource(R.string.settings_logging_enabled_subtitle),
                        checked = loggingEnabled,
                        onCheckedChange = {
                            LogManager.isLoggingEnabled = it
                            viewModel.setLoggingEnabled(it)
                        }
                    )
                }
                // Middle items
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_logging_file),
                        subtitle = stringResource(R.string.settings_logging_file_subtitle),
                        checked = fileLoggingEnabled,
                        onCheckedChange = {
                            LogManager.isFileLoggingEnabled = it
                            viewModel.setFileLoggingEnabled(it)
                        }
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_logging_console),
                        subtitle = stringResource(R.string.settings_logging_console_subtitle),
                        checked = consoleLoggingEnabled,
                        onCheckedChange = {
                            LogManager.isConsoleLoggingEnabled = it
                            viewModel.setConsoleLoggingEnabled(it)
                        }
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_logging_crash),
                        subtitle = stringResource(R.string.settings_logging_crash_subtitle),
                        checked = crashReportingEnabled,
                        onCheckedChange = {
                            LogManager.isCrashReportingEnabled = it
                            viewModel.setCrashReportingEnabled(it)
                        }
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_logging_size)) },
                        supportingContent = { Text(LogManager.formatLogSize(LogManager.getLogDirectorySize())) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f))
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_logging_view)) },
                        supportingContent = { Text(stringResource(R.string.settings_logging_view_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { onNavigateToLogViewer() }
                    )
                }
                SettingsItemCard(position = CardPosition.MIDDLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_logging_export)) },
                        supportingContent = { Text(stringResource(R.string.settings_logging_export_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { onExportLogs() }
                    )
                }
                // Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_logging_cleanup)) },
                        supportingContent = { Text(stringResource(R.string.settings_logging_cleanup_subtitle)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { onCleanupLogs() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ReplayGain Section
            // ReplayGain target loudness preset values
            val replayGainOptions = listOf(
                -23f to "EBU R128 (-23 LUFS)",
                -18f to "Streaming (-18 LUFS)",
                -16f to "CD Standard (-16 LUFS)",
                -14f to "Loud (-14 LUFS)"
            )
            var replayGainExpanded by remember { mutableStateOf(false) }
            var showReplayGainDialog by remember { mutableStateOf(false) }
            
            SettingsSection(title = stringResource(R.string.replay_gain_settings)) {
                // Single item - use SINGLE position
                SettingsItemCard(position = CardPosition.SINGLE) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.replay_gain_target_loudness)) },
                        supportingContent = {
                            Text(stringResource(R.string.replay_gain_default_loudness))
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = String.format("%.0f LUFS", replayGainTargetLoudness),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (replayGainExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (replayGainExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable { showReplayGainDialog = true }
                    )
                }

                // ReplayGain target loudness selection dialog
                if (showReplayGainDialog) {
                    AlertDialog(
                        onDismissRequest = { showReplayGainDialog = false },
                        shape = MaterialTheme.shapes.large,
                        title = { Text(stringResource(R.string.replay_gain_target_loudness)) },
                        text = {
                            Column {
                                replayGainOptions.forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setReplayGainTargetLoudness(value)
                                                showReplayGainDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = replayGainTargetLoudness == value,
                                            onClick = {
                                                viewModel.setReplayGainTargetLoudness(value)
                                                showReplayGainDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showReplayGainDialog = false }) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                // First item
                SettingsItemCard(position = CardPosition.FIRST) {
                    SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                }
                // Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    SettingsInfoRow(title = stringResource(R.string.settings_developer_label), value = stringResource(R.string.settings_developer_value))
                }
            }
        }
    }

    if (showMetadataSourceDialog) {
        val metadataConfig = sourceConfigurations.metadata
        DraggableSourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_metadata),
            sourceTypeConfig = metadataConfig,
            appleCountryOptions = appleCountryOptions,
            currentAppleCountry = currentAppleCountry,
            onDismiss = { showMetadataSourceDialog = false },
            onSourceEnabledChange = { sourceId, enabled ->
                viewModel.setSourceEnabled(DataSourceType.METADATA, sourceId, enabled)
            },
            onSourceReorder = { orderedIds ->
                viewModel.reorderSources(DataSourceType.METADATA, orderedIds)
            },
            onAppleCountryChange = { countryCode ->
                viewModel.setSourceExtraOption(DataSourceType.METADATA, "itunes", "country", countryCode)
            }
        )
    }

    if (showLyricsSourceDialog) {
        val lyricsConfig = sourceConfigurations.lyrics
        DraggableSourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_lyrics),
            sourceTypeConfig = lyricsConfig,
            appleCountryOptions = appleCountryOptions,
            currentAppleCountry = currentAppleCountry,
            onDismiss = { showLyricsSourceDialog = false },
            onSourceEnabledChange = { sourceId, enabled ->
                viewModel.setSourceEnabled(DataSourceType.LYRICS, sourceId, enabled)
            },
            onSourceReorder = { orderedIds ->
                viewModel.reorderSources(DataSourceType.LYRICS, orderedIds)
            },
            onAppleCountryChange = { countryCode ->
                viewModel.setSourceExtraOption(DataSourceType.LYRICS, "itunes", "country", countryCode)
            }
        )
    }

    if (showCoverSourceDialog) {
        val coverConfig = sourceConfigurations.cover
        DraggableSourcePriorityDialog(
            title = stringResource(R.string.settings_source_group_cover),
            sourceTypeConfig = coverConfig,
            appleCountryOptions = appleCountryOptions,
            currentAppleCountry = currentAppleCountry,
            onDismiss = { showCoverSourceDialog = false },
            onSourceEnabledChange = { sourceId, enabled ->
                viewModel.setSourceEnabled(DataSourceType.COVER, sourceId, enabled)
            },
            onSourceReorder = { orderedIds ->
                viewModel.reorderSources(DataSourceType.COVER, orderedIds)
            },
            onAppleCountryChange = { countryCode ->
                viewModel.setSourceExtraOption(DataSourceType.COVER, "itunes", "country", countryCode)
            }
        )
    }

    if (showSearchLimitsDialog) {
        SearchLimitDialog(
            globalLimit = onlineSearchLimit,
            musicBrainzLimit = onlineSearchLimitMusicBrainz,
            itunesLimit = onlineSearchLimitITunes,
            neteaseLimit = onlineSearchLimitNetease,
            qqMusicLimit = onlineSearchLimitQQMusic,
            searchLimitOptions = searchLimitOptions,
            onDismiss = { showSearchLimitsDialog = false },
            onGlobalLimitChange = { viewModel.setOnlineSearchLimit(it) },
            onMusicBrainzLimitChange = { viewModel.setOnlineSearchLimitMusicBrainz(it) },
            onItunesLimitChange = { viewModel.setOnlineSearchLimitITunes(it) },
            onNeteaseLimitChange = { viewModel.setOnlineSearchLimitNetease(it) },
            onQQMusicLimitChange = { viewModel.setOnlineSearchLimitQQMusic(it) }
        )
    }

}
