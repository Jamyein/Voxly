package com.voxly.presentation.screens

import android.app.Activity
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalFlexBoxApi
import androidx.compose.foundation.layout.FlexAlignItems
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexDirection
import androidx.compose.foundation.layout.FlexJustifyContent
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.core.util.LogManager
import com.voxly.domain.model.DataSourceConfig
import com.voxly.domain.model.DataSourceType
import com.voxly.domain.model.SourceConfigurations
import com.voxly.domain.model.SourceTypeConfig
import com.voxly.presentation.components.ConnectedButtonGroupRow
import com.voxly.presentation.components.ConnectedButtonGroupRowCompact
import com.voxly.presentation.components.ConnectedIconOnlyButtonGroupRow
import com.voxly.presentation.components.SegmentedClickableRow
import com.voxly.presentation.components.SegmentedInfoRow
import com.voxly.presentation.components.SegmentedOption
import com.voxly.presentation.components.SegmentedSwitchRow
import com.voxly.presentation.components.SettingsSection
import com.voxly.presentation.components.SortDropdownMenu
import com.voxly.presentation.components.SortMenuItem
import com.voxly.presentation.viewmodel.SettingsViewModel
import com.voxly.presentation.screens.settings.*
import com.voxly.presentation.viewmodel.DragDialogState
import com.voxly.presentation.viewmodel.DragDialogSourceItem

// Layout constants
private val HorizontalPadding = 16.dp
private val SectionSpacing = 16.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFlexBoxApi::class)
@Composable
private fun <T> ConnectedIconButtonGroup(
    options: List<ConnectedIconOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseWeight = 1.0f
    val selectedWeight = 1.24f
    val animatedWeights = options.map { option ->
        val animatedWeight by animateFloatAsState(
            targetValue = if (option.optionValue == selectedValue) selectedWeight else baseWeight,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "settings_connected_button_weight"
        )
        animatedWeight
    }
    ButtonGroup(
        modifier = modifier,
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.optionValue == selectedValue
            val itemModifier = Modifier
                .weight(animatedWeights[index])
                .defaultMinSize(minWidth = 56.dp, minHeight = 40.dp)
                .semantics { role = Role.RadioButton }
            customItem(
                buttonGroupContent = {
                    val tooltipState = rememberTooltipState()
                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Text(option.tooltip)
                                }
                            },
                            state = tooltipState
                        ) {
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) onSelected(option.optionValue)
                                },
                                modifier = itemModifier,
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (option.text != null) {
                                    Text(
                                        text = option.text,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.animateContentSize(
                                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                        )
                                    )
                                } else if (option.icon != null) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.tooltip,
                                        modifier = Modifier.animateContentSize(
                                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(option.tooltip) },
                        leadingIcon = option.icon?.let { icon ->
                            { Icon(imageVector = icon, contentDescription = option.tooltip) }
                        },
                        onClick = {
                            onSelected(option.optionValue)
                            state.dismiss()
                        }
                    )
                }
            )
        }
    }
}

/**
 * Draggable source priority dialog with inline switches and extra options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraggableSourcePriorityDialog(
    title: String,
    dialogState: DragDialogState,
    appleCountryOptions: List<AppleCountryOption>,
    currentAppleCountry: AppleCountryOption,
    onDismiss: () -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onSourceReorder: (List<String>) -> Unit,
    onAppleCountryChange: (String) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Offset, Float) -> Unit
) {
    val density = LocalDensity.current
    val itemHeight = 80.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }
    var localAppleCountry by remember(currentAppleCountry) { mutableStateOf(currentAppleCountry) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_source_priority_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                dialogState.sources.forEachIndexed { index, sourceItem ->
                    SourceItemCard(
                        index = index,
                        sourceItem = sourceItem,
                        isDragging = dialogState.draggedIndex == index,
                        dragOffset = if (dialogState.draggedIndex == index) dialogState.dragOffset else 0f,
                        appleCountryOptions = appleCountryOptions,
                        currentAppleCountry = localAppleCountry,
                        onDragStart = { onDragStart(index) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onDrag = { dragAmount ->
                            onDrag(Offset(0f, dragAmount), itemHeightPx)
                        },
                        onSourceEnabledChange = onSourceEnabledChange,
                        onAppleCountryChange = { countryCode ->
                            localAppleCountry = appleCountryOptions.firstOrNull {
                                it.countryValue == countryCode
                            } ?: localAppleCountry
                            onAppleCountryChange(countryCode)
                        }
                    )
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

/**
 * Individual source item card within the draggable priority dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceItemCard(
    index: Int,
    sourceItem: DragDialogSourceItem,
    isDragging: Boolean,
    dragOffset: Float,
    appleCountryOptions: List<AppleCountryOption>,
    currentAppleCountry: AppleCountryOption,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onSourceEnabledChange: (String, Boolean) -> Unit,
    onAppleCountryChange: (String) -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "scale"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "elevation"
    )

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
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
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
            draggedElevation = animatedElevation
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )

                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.settings_drag_handle),
                    tint = if (isDragging)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text(
                    text = sourceToDisplayName(sourceItem.sourceId),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Switch(
                    checked = sourceItem.enabled,
                    onCheckedChange = { enabled ->
                        onSourceEnabledChange(sourceItem.sourceId, enabled)
                    }
                )

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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (sourceItem.enabled)
                                        stringResource(R.string.settings_source_disable)
                                    else
                                        stringResource(R.string.settings_source_enable)
                                )
                            },
                            onClick = {
                                onSourceEnabledChange(sourceItem.sourceId, !sourceItem.enabled)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (sourceItem.enabled)
                                        Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = null
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (sourceHasExtraOptions(sourceItem.sourceId)) {
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
                                        onAppleCountryChange(option.countryValue)
                                        showMenu = false
                                    },
                                    trailingIcon = {
                                        if (option.countryValue == currentAppleCountry.countryValue) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!isDragging) {
                Text(
                    text = stringResource(R.string.settings_drag_hint),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerSourceSearchLimitRow(
    title: String,
    currentLimit: Int,
    searchLimitOptions: List<SearchLimitOption>,
    onLimitChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = searchLimitOptions.firstOrNull { it.limitValue == currentLimit }
        ?: searchLimitOptions.firstOrNull { it.limitValue == 0 }
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
                                    onLimitChange(option.limitValue)
                                    expanded = false
                                }
                            )
                        }
                    }
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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

                Text(
                    text = stringResource(R.string.settings_online_search_limit_subtitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_musicbrainz),
                    currentLimit = musicBrainzLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onMusicBrainzLimitChange
                )

                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_itunes),
                    currentLimit = itunesLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onItunesLimitChange
                )

                SearchLimitRow(
                    text = stringResource(R.string.settings_online_search_limit_netease),
                    currentLimit = neteaseLimit,
                    searchLimitOptions = searchLimitOptions,
                    onLimitChange = onNeteaseLimitChange
                )

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
                optionValue = option.limitValue,
                icon = if (option.limitValue == 0) Icons.Default.AllInclusive else null,
                text = if (option.limitValue != 0) option.limitValue.toString() else null,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * Settings screen for application preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToDirectoryManagement: () -> Unit = {},
    onNavigateToScanDirectorySettings: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    onExportLogs: () -> Unit = {},
    onCleanupLogs: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var languageExpanded by remember { mutableStateOf(false) }
    val effectiveLanguageTag = uiState.savedLanguageTag ?: resolveCurrentLanguageTag()

    var showMetadataSourceDialog by remember { mutableStateOf(false) }
    var showLyricsSourceDialog by remember { mutableStateOf(false) }
    var showCoverSourceDialog by remember { mutableStateOf(false) }
    var showSearchLimitsDialog by remember { mutableStateOf(false) }
    var showSeparatorDialog by remember { mutableStateOf(false) }
    var separatorInput by remember { mutableStateOf("") }
    var pendingDeleteSeparator by remember { mutableStateOf<String?>(null) }
    var dialogSeparatorTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loudnessExpanded by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
    val currentAppleCountry = appleCountryOptions.firstOrNull { it.countryValue == uiState.appleCountryCode.lowercase() }
        ?: appleCountryOptions.first()

    val searchLimitOptions = remember {
        listOf(
            SearchLimitOption(0, R.string.settings_online_search_limit_unlimited),
            SearchLimitOption(10),
            SearchLimitOption(25),
            SearchLimitOption(50)
        )
    }

    val currentSeparators by viewModel.artistSeparatorsSet.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                )
                .padding(horizontal = HorizontalPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            AppearanceSettingsSection(
                themeMode = uiState.themeMode,
                dynamicColors = uiState.dynamicColors,
                metadataEditorDynamicAlbumColor = uiState.metadataEditorDynamicAlbumColor,
                savedLanguageTag = effectiveLanguageTag,
                languageExpanded = languageExpanded,
                onLanguageExpandedChange = { languageExpanded = it },
                onSetThemeMode = viewModel::setThemeMode,
                onSetDynamicColors = viewModel::setDynamicColors,
                onSetMetadataEditorDynamicAlbumColor = viewModel::setMetadataEditorDynamicAlbumColor,
                onSetLanguage = { tag ->
                    viewModel.setLanguage(tag)
                    languageExpanded = false
                    activity?.recreate()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            ScanningSettingsSection(
                currentSeparators = currentSeparators,
                minDurationFilterEnabled = uiState.minDurationFilterEnabled,
                scanMode = uiState.scanMode,
                replayGainTargetLoudness = uiState.replayGainTargetLoudness,
                loudnessExpanded = loudnessExpanded,
                onLoudnessExpandedChange = { loudnessExpanded = it },
                onNavigateToScanDirectorySettings = onNavigateToScanDirectorySettings,
                onShowSeparatorDialog = { tags ->
                    dialogSeparatorTags = tags
                    separatorInput = ""
                    showSeparatorDialog = true
                },
                onSetMinDurationFilter = viewModel::setMinDurationFilterEnabled,
                onSetScanMode = viewModel::setScanMode,
                onSetReplayGainTargetLoudness = viewModel::setReplayGainTargetLoudness,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            OnlineSettingsSection(
                onlineSearchLimit = uiState.onlineSearchLimit,
                lyricsTimestampFormatEnabled = uiState.lyricsTimestampFormatEnabled,
                onShowMetadataSourceDialog = { showMetadataSourceDialog = true },
                onShowLyricsSourceDialog = { showLyricsSourceDialog = true },
                onShowCoverSourceDialog = { showCoverSourceDialog = true },
                onShowSearchLimitsDialog = { showSearchLimitsDialog = true },
                onSetLyricsTimestampFormatEnabled = viewModel::setLyricsTimestampFormatEnabled,
                onSetOnlineSearchLimit = viewModel::setOnlineSearchLimit,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            LoggingSettingsSection(
                loggingEnabled = uiState.loggingEnabled,
                fileLoggingEnabled = uiState.fileLoggingEnabled,
                consoleLoggingEnabled = uiState.consoleLoggingEnabled,
                crashReportingEnabled = uiState.crashReportingEnabled,
                onSetLoggingEnabled = {
                    LogManager.isLoggingEnabled = it
                    viewModel.setLoggingEnabled(it)
                },
                onSetFileLoggingEnabled = {
                    LogManager.isFileLoggingEnabled = it
                    viewModel.setFileLoggingEnabled(it)
                },
                onSetConsoleLoggingEnabled = {
                    LogManager.isConsoleLoggingEnabled = it
                    viewModel.setConsoleLoggingEnabled(it)
                },
                onSetCrashReportingEnabled = {
                    LogManager.isCrashReportingEnabled = it
                    viewModel.setCrashReportingEnabled(it)
                },
                onNavigateToLogViewer = onNavigateToLogViewer,
                onExportLogs = onExportLogs,
                onCleanupLogs = onCleanupLogs,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(SectionSpacing))

            AboutSettingsSection(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val dragDialogState by viewModel.dragDialogState.collectAsStateWithLifecycle()
    var activeDialogType by remember { mutableStateOf<DataSourceType?>(null) }

    SettingsSourceDialogs(
        showMetadataSourceDialog = showMetadataSourceDialog,
        onShowMetadataSourceDialogChange = { showMetadataSourceDialog = it },
        showLyricsSourceDialog = showLyricsSourceDialog,
        onShowLyricsSourceDialogChange = { showLyricsSourceDialog = it },
        showCoverSourceDialog = showCoverSourceDialog,
        onShowCoverSourceDialogChange = { showCoverSourceDialog = it },
        dragDialogState = dragDialogState,
        activeDialogType = activeDialogType,
        onActiveDialogTypeChange = { activeDialogType = it },
        appleCountryOptions = appleCountryOptions,
        currentAppleCountry = currentAppleCountry,
        viewModel = viewModel
    )

    SettingsInlineDialogs(
        showSeparatorDialog = showSeparatorDialog,
        onShowSeparatorDialogChange = { showSeparatorDialog = it },
        dialogSeparatorTags = dialogSeparatorTags,
        onDialogSeparatorTagsChange = { dialogSeparatorTags = it },
        separatorInput = separatorInput,
        onSeparatorInputChange = { separatorInput = it },
        pendingDeleteSeparator = pendingDeleteSeparator,
        onPendingDeleteSeparatorChange = { pendingDeleteSeparator = it },
        onSetArtistSeparators = viewModel::setArtistSeparators
    )

    if (showSearchLimitsDialog) {
        SearchLimitDialog(
            globalLimit = uiState.onlineSearchLimit,
            musicBrainzLimit = uiState.onlineSearchLimitMusicBrainz,
            itunesLimit = uiState.onlineSearchLimitITunes,
            neteaseLimit = uiState.onlineSearchLimitNetease,
            qqMusicLimit = uiState.onlineSearchLimitQQMusic,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsSection(
    themeMode: String,
    dynamicColors: Boolean,
    metadataEditorDynamicAlbumColor: Boolean,
    savedLanguageTag: String?,
    languageExpanded: Boolean,
    onLanguageExpandedChange: (Boolean) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetDynamicColors: (Boolean) -> Unit,
    onSetMetadataEditorDynamicAlbumColor: (Boolean) -> Unit,
    onSetLanguage: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val languageOptions = remember {
        listOf(
            LanguageOption(R.string.settings_language_system, null),
            LanguageOption(R.string.settings_language_english, "en"),
            LanguageOption(R.string.settings_language_chinese_simplified, "zh-CN")
        )
    }
    val currentLanguageOption = languageOptions.firstOrNull {
        normalizeLanguageTag(it.languageTag) == normalizeLanguageTag(savedLanguageTag)
    } ?: languageOptions.first()

    SettingsSection(title = stringResource(R.string.settings_section_appearance), modifier = modifier) {
        ConnectedIconOnlyButtonGroupRow(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                SegmentedOption("system", Icons.Default.BrightnessAuto, stringResource(R.string.settings_theme_system)),
                SegmentedOption("light", Icons.Default.LightMode, stringResource(R.string.settings_theme_light)),
                SegmentedOption("dark", Icons.Default.DarkMode, stringResource(R.string.settings_theme_dark))
            ),
            selectedValue = themeMode,
            onSelected = onSetThemeMode,
            index = 0,
            count = 3
        )

        SegmentedSwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
            checked = dynamicColors,
            onCheckedChange = onSetDynamicColors,
            index = 1,
            count = 3
        )

        SegmentedSwitchRow(
            title = stringResource(R.string.settings_metadata_editor_dynamic_album_color),
            subtitle = stringResource(R.string.settings_metadata_editor_dynamic_album_color_subtitle),
            checked = metadataEditorDynamicAlbumColor,
            onCheckedChange = onSetMetadataEditorDynamicAlbumColor,
            index = 2,
            count = 4
        )

        SegmentedClickableRow(
            title = stringResource(R.string.settings_language),
            subtitle = stringResource(currentLanguageOption.labelResId),
            trailingContent = {
                val arrowRotation by animateFloatAsState(
                    targetValue = if (languageExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "language_dropdown_arrow"
                )
                SortDropdownMenu(
                    expanded = languageExpanded,
                    onExpandedChange = onLanguageExpandedChange,
                    anchor = {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(currentLanguageOption.labelResId),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                            )
                        }
                    }
                ) {
                    languageOptions.forEach { option ->
                        SortMenuItem(
                            option = option,
                            labelResId = option.labelResId,
                            currentSortOption = currentLanguageOption,
                            onSortOptionChange = { selected ->
                                onSetLanguage(selected.languageTag)
                            },
                            onDismiss = { onLanguageExpandedChange(false) }
                        )
                    }
                }
            },
            onClick = { },
            index = 3,
            count = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanningSettingsSection(
    currentSeparators: Set<String>,
    minDurationFilterEnabled: Boolean,
    scanMode: String,
    replayGainTargetLoudness: Float,
    loudnessExpanded: Boolean,
    onLoudnessExpandedChange: (Boolean) -> Unit,
    onNavigateToScanDirectorySettings: () -> Unit,
    onShowSeparatorDialog: (Set<String>) -> Unit,
    onSetMinDurationFilter: (Boolean) -> Unit,
    onSetScanMode: (String) -> Unit,
    onSetReplayGainTargetLoudness: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val loudnessOptions = remember {
        listOf(
            LoudnessOption(-23f, R.string.replay_gain_loudness_ebu_r128),
            LoudnessOption(-18f, R.string.replay_gain_loudness_streaming),
            LoudnessOption(-16f, R.string.replay_gain_loudness_cd),
            LoudnessOption(-14f, R.string.replay_gain_loudness_loud)
        )
    }
    val currentLoudnessOption = loudnessOptions.firstOrNull { it.loudnessValue == replayGainTargetLoudness }
        ?: loudnessOptions.first()

    val scanModeOptions = remember {
        listOf(
            ScanModeOption("TRACK_ONLY", R.string.settings_scan_mode_track_only),
            ScanModeOption("SINGLE_ALBUM", R.string.settings_scan_mode_album_only),
            ScanModeOption("ALBUMS", R.string.settings_scan_mode_track_and_album)
        )
    }

    SettingsSection(title = stringResource(R.string.settings_section_scanning), modifier = modifier) {
        SegmentedClickableRow(
            title = stringResource(R.string.settings_scan_directory_settings),
            subtitle = stringResource(R.string.settings_scan_directory_settings_subtitle),
            onClick = onNavigateToScanDirectorySettings,
            index = 0,
            count = 6
        )

        SegmentedClickableRow(
            title = stringResource(R.string.artist_separators),
            subtitle = currentSeparators.joinToString(" "),
            onClick = { onShowSeparatorDialog(currentSeparators) },
            index = 1,
            count = 6
        )

        SegmentedSwitchRow(
            title = stringResource(R.string.settings_min_duration_filter),
            subtitle = stringResource(R.string.settings_min_duration_filter_subtitle),
            checked = minDurationFilterEnabled,
            onCheckedChange = onSetMinDurationFilter,
            index = 3,
            count = 6
        )

        ConnectedIconOnlyButtonGroupRow(
            title = stringResource(R.string.settings_scan_mode),
            options = scanModeOptions.map { option ->
                SegmentedOption(
                    value = option.modeValue,
                    icon = when (option.modeValue) {
                        "TRACK_ONLY" -> Icons.Default.MusicNote
                        "SINGLE_ALBUM" -> Icons.Default.Album
                        else -> Icons.Default.LibraryMusic
                    },
                    label = stringResource(option.labelResId)
                )
            },
            selectedValue = scanMode,
            onSelected = onSetScanMode,
            index = 4,
            count = 6
        )

        SegmentedClickableRow(
            title = stringResource(R.string.replay_gain_target_loudness),
            subtitle = stringResource(R.string.replay_gain_default_loudness),
            trailingContent = {
                val arrowRotation by animateFloatAsState(
                    targetValue = if (loudnessExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "loudness_dropdown_arrow"
                )
                SortDropdownMenu(
                    expanded = loudnessExpanded,
                    onExpandedChange = onLoudnessExpandedChange,
                    anchor = {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(currentLoudnessOption.labelResId),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                            )
                        }
                    }
                ) {
                    loudnessOptions.forEach { option ->
                        SortMenuItem(
                            option = option,
                            labelResId = option.labelResId,
                            currentSortOption = currentLoudnessOption,
                            onSortOptionChange = { selected ->
                                onSetReplayGainTargetLoudness(selected.loudnessValue)
                                onLoudnessExpandedChange(false)
                            },
                            onDismiss = { onLoudnessExpandedChange(false) }
                        )
                    }
                }
            },
            onClick = { },
            index = 5,
            count = 6
        )
    }
}

@Composable
private fun OnlineSettingsSection(
    onlineSearchLimit: Int,
    lyricsTimestampFormatEnabled: Boolean,
    onShowMetadataSourceDialog: () -> Unit,
    onShowLyricsSourceDialog: () -> Unit,
    onShowCoverSourceDialog: () -> Unit,
    onShowSearchLimitsDialog: () -> Unit,
    onSetLyricsTimestampFormatEnabled: (Boolean) -> Unit,
    onSetOnlineSearchLimit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchLimitOptions = remember {
        listOf(
            SearchLimitOption(0, R.string.settings_online_search_limit_unlimited),
            SearchLimitOption(10),
            SearchLimitOption(25),
            SearchLimitOption(50)
        )
    }
    val searchLimitSegmentedOptions = remember(searchLimitOptions) {
        searchLimitOptions.map { option ->
            SegmentedOption(
                value = option.limitValue,
                label = if (option.limitValue == 0) "∞" else option.limitValue.toString()
            )
        }
    }

    SettingsSection(title = stringResource(R.string.settings_section_online_metadata), modifier = modifier) {
        SegmentedClickableRow(
            title = stringResource(R.string.settings_source_group_metadata),
            subtitle = stringResource(R.string.settings_source_group_metadata_subtitle),
            onClick = onShowMetadataSourceDialog,
            index = 0,
            count = 5
        )
        SegmentedClickableRow(
            title = stringResource(R.string.settings_source_group_lyrics),
            subtitle = stringResource(R.string.settings_source_group_lyrics_subtitle),
            onClick = onShowLyricsSourceDialog,
            index = 1,
            count = 5
        )
        SegmentedClickableRow(
            title = stringResource(R.string.settings_source_group_cover),
            subtitle = stringResource(R.string.settings_source_group_cover_subtitle),
            onClick = onShowCoverSourceDialog,
            index = 2,
            count = 5
        )
        SegmentedSwitchRow(
            title = stringResource(R.string.settings_lyrics_timestamp_format),
            subtitle = stringResource(R.string.settings_lyrics_timestamp_format_subtitle),
            checked = lyricsTimestampFormatEnabled,
            onCheckedChange = onSetLyricsTimestampFormatEnabled,
            index = 3,
            count = 5
        )
        ConnectedButtonGroupRow(
            title = stringResource(R.string.settings_online_search_limit),
            options = searchLimitSegmentedOptions,
            selectedValue = onlineSearchLimit,
            onSelected = onSetOnlineSearchLimit,
            index = 4,
            count = 5
        )
    }
}

@Composable
private fun LoggingSettingsSection(
    loggingEnabled: Boolean,
    fileLoggingEnabled: Boolean,
    consoleLoggingEnabled: Boolean,
    crashReportingEnabled: Boolean,
    onSetLoggingEnabled: (Boolean) -> Unit,
    onSetFileLoggingEnabled: (Boolean) -> Unit,
    onSetConsoleLoggingEnabled: (Boolean) -> Unit,
    onSetCrashReportingEnabled: (Boolean) -> Unit,
    onNavigateToLogViewer: () -> Unit,
    onExportLogs: () -> Unit,
    onCleanupLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSection(title = stringResource(R.string.settings_section_logging), modifier = modifier) {
        SegmentedSwitchRow(
            title = stringResource(R.string.settings_logging_enabled),
            subtitle = stringResource(R.string.settings_logging_enabled_subtitle),
            checked = loggingEnabled,
            onCheckedChange = onSetLoggingEnabled,
            index = 0,
            count = 8
        )
        SegmentedSwitchRow(
            title = stringResource(R.string.settings_logging_file),
            subtitle = stringResource(R.string.settings_logging_file_subtitle),
            checked = fileLoggingEnabled,
            onCheckedChange = onSetFileLoggingEnabled,
            index = 1,
            count = 8
        )
        SegmentedSwitchRow(
            title = stringResource(R.string.settings_logging_console),
            subtitle = stringResource(R.string.settings_logging_console_subtitle),
            checked = consoleLoggingEnabled,
            onCheckedChange = onSetConsoleLoggingEnabled,
            index = 2,
            count = 8
        )
        SegmentedSwitchRow(
            title = stringResource(R.string.settings_logging_crash),
            subtitle = stringResource(R.string.settings_logging_crash_subtitle),
            checked = crashReportingEnabled,
            onCheckedChange = onSetCrashReportingEnabled,
            index = 3,
            count = 8
        )
        SegmentedInfoRow(
            title = stringResource(R.string.settings_logging_size),
            value = LogManager.formatLogSize(LogManager.getLogDirectorySize()),
            index = 4,
            count = 8
        )
        SegmentedClickableRow(
            title = stringResource(R.string.settings_logging_view),
            subtitle = stringResource(R.string.settings_logging_view_subtitle),
            onClick = onNavigateToLogViewer,
            index = 5,
            count = 8
        )
        SegmentedClickableRow(
            title = stringResource(R.string.settings_logging_export),
            subtitle = stringResource(R.string.settings_logging_export_subtitle),
            onClick = onExportLogs,
            index = 6,
            count = 8
        )
        SegmentedClickableRow(
            title = stringResource(R.string.settings_logging_cleanup),
            subtitle = stringResource(R.string.settings_logging_cleanup_subtitle),
            onClick = onCleanupLogs,
            index = 7,
            count = 8
        )
    }
}

@Composable
private fun AboutSettingsSection(
    modifier: Modifier = Modifier
) {
    SettingsSection(title = stringResource(R.string.settings_section_about), modifier = modifier) {
        SegmentedInfoRow(
            title = stringResource(R.string.settings_version_label),
            value = BuildConfig.VERSION_NAME,
            index = 0,
            count = 2
        )
        SegmentedInfoRow(
            title = stringResource(R.string.settings_developer_label),
            value = stringResource(R.string.settings_developer_value),
            index = 1,
            count = 2
        )
    }
}

@Composable
private fun SettingsSourceDialogs(
    showMetadataSourceDialog: Boolean,
    onShowMetadataSourceDialogChange: (Boolean) -> Unit,
    showLyricsSourceDialog: Boolean,
    onShowLyricsSourceDialogChange: (Boolean) -> Unit,
    showCoverSourceDialog: Boolean,
    onShowCoverSourceDialogChange: (Boolean) -> Unit,
    dragDialogState: DragDialogState?,
    activeDialogType: DataSourceType?,
    onActiveDialogTypeChange: (DataSourceType?) -> Unit,
    appleCountryOptions: List<AppleCountryOption>,
    currentAppleCountry: AppleCountryOption,
    viewModel: SettingsViewModel
) {
    if (showMetadataSourceDialog) {
        LaunchedEffect(Unit) {
            if (activeDialogType != DataSourceType.METADATA) {
                viewModel.initDragDialogState(DataSourceType.METADATA)
                onActiveDialogTypeChange(DataSourceType.METADATA)
            }
        }
        dragDialogState?.let { state ->
            DraggableSourcePriorityDialog(
                title = stringResource(R.string.settings_source_group_metadata),
                dialogState = state,
                appleCountryOptions = appleCountryOptions,
                currentAppleCountry = currentAppleCountry,
                onDismiss = {
                    viewModel.clearDragDialogState()
                    onActiveDialogTypeChange(null)
                    onShowMetadataSourceDialogChange(false)
                },
                onSourceEnabledChange = { sourceId, enabled ->
                    viewModel.setSourceEnabled(DataSourceType.METADATA, sourceId, enabled)
                },
                onSourceReorder = { orderedIds ->
                    viewModel.reorderSources(DataSourceType.METADATA, orderedIds)
                },
                onAppleCountryChange = { countryCode ->
                    viewModel.setSourceExtraOption(DataSourceType.METADATA, "itunes", "country", countryCode)
                    viewModel.setAppleCountryCode(countryCode)
                },
                onDragStart = { index -> viewModel.startDragging(index) },
                onDragEnd = { viewModel.endDragging() },
                onDragCancel = { viewModel.cancelDragging() },
                onDrag = { offset, itemHeightPx -> viewModel.updateDragOffset(offset.y, itemHeightPx) }
            )
        }
    }

    if (showLyricsSourceDialog) {
        LaunchedEffect(Unit) {
            if (activeDialogType != DataSourceType.LYRICS) {
                viewModel.initDragDialogState(DataSourceType.LYRICS)
                onActiveDialogTypeChange(DataSourceType.LYRICS)
            }
        }
        dragDialogState?.let { state ->
            DraggableSourcePriorityDialog(
                title = stringResource(R.string.settings_source_group_lyrics),
                dialogState = state,
                appleCountryOptions = appleCountryOptions,
                currentAppleCountry = currentAppleCountry,
                onDismiss = {
                    viewModel.clearDragDialogState()
                    onActiveDialogTypeChange(null)
                    onShowLyricsSourceDialogChange(false)
                },
                onSourceEnabledChange = { sourceId, enabled ->
                    viewModel.setSourceEnabled(DataSourceType.LYRICS, sourceId, enabled)
                },
                onSourceReorder = { orderedIds ->
                    viewModel.reorderSources(DataSourceType.LYRICS, orderedIds)
                },
                onAppleCountryChange = { countryCode ->
                    viewModel.setSourceExtraOption(DataSourceType.LYRICS, "itunes", "country", countryCode)
                    viewModel.setAppleCountryCode(countryCode)
                },
                onDragStart = { index -> viewModel.startDragging(index) },
                onDragEnd = { viewModel.endDragging() },
                onDragCancel = { viewModel.cancelDragging() },
                onDrag = { offset, itemHeightPx -> viewModel.updateDragOffset(offset.y, itemHeightPx) }
            )
        }
    }

    if (showCoverSourceDialog) {
        LaunchedEffect(Unit) {
            if (activeDialogType != DataSourceType.COVER) {
                viewModel.initDragDialogState(DataSourceType.COVER)
                onActiveDialogTypeChange(DataSourceType.COVER)
            }
        }
        dragDialogState?.let { state ->
            DraggableSourcePriorityDialog(
                title = stringResource(R.string.settings_source_group_cover),
                dialogState = state,
                appleCountryOptions = appleCountryOptions,
                currentAppleCountry = currentAppleCountry,
                onDismiss = {
                    viewModel.clearDragDialogState()
                    onActiveDialogTypeChange(null)
                    onShowCoverSourceDialogChange(false)
                },
                onSourceEnabledChange = { sourceId, enabled ->
                    viewModel.setSourceEnabled(DataSourceType.COVER, sourceId, enabled)
                },
                onSourceReorder = { orderedIds ->
                    viewModel.reorderSources(DataSourceType.COVER, orderedIds)
                },
                onAppleCountryChange = { countryCode ->
                    viewModel.setSourceExtraOption(DataSourceType.COVER, "itunes", "country", countryCode)
                    viewModel.setAppleCountryCode(countryCode)
                },
                onDragStart = { index -> viewModel.startDragging(index) },
                onDragEnd = { viewModel.endDragging() },
                onDragCancel = { viewModel.cancelDragging() },
                onDrag = { offset, itemHeightPx -> viewModel.updateDragOffset(offset.y, itemHeightPx) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFlexBoxApi::class)
@Composable
private fun SettingsInlineDialogs(
    showSeparatorDialog: Boolean,
    onShowSeparatorDialogChange: (Boolean) -> Unit,
    dialogSeparatorTags: Set<String>,
    onDialogSeparatorTagsChange: (Set<String>) -> Unit,
    separatorInput: String,
    onSeparatorInputChange: (String) -> Unit,
    pendingDeleteSeparator: String?,
    onPendingDeleteSeparatorChange: (String?) -> Unit,
    onSetArtistSeparators: (Set<String>) -> Unit
) {
    if (showSeparatorDialog) {
        AlertDialog(
            onDismissRequest = {
                onDialogSeparatorTagsChange(emptySet())
                onShowSeparatorDialogChange(false)
            },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.artist_separators)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlexBox(
                        modifier = Modifier.fillMaxWidth(),
                        config = {
                            direction(FlexDirection.Row)
                            wrap(FlexWrap.Wrap)
                            justifyContent(FlexJustifyContent.Start)
                            alignItems(FlexAlignItems.Center)
                            gap(8.dp)
                        }
                    ) {
                        dialogSeparatorTags.forEach { separator ->
                            SeparatorChip(
                                separator = separator,
                                onDelete = { onDialogSeparatorTagsChange(dialogSeparatorTags - separator) },
                                onLongPress = { onPendingDeleteSeparatorChange(separator) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = separatorInput,
                            onValueChange = onSeparatorInputChange,
                            label = { Text(stringResource(R.string.artist_separators)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val trimmed = separatorInput.trim()
                                if (trimmed.isNotBlank() && trimmed !in dialogSeparatorTags) {
                                    onDialogSeparatorTagsChange(dialogSeparatorTags + trimmed)
                                    onSeparatorInputChange("")
                                }
                            },
                            enabled = separatorInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.settings_separator_add))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetArtistSeparators(dialogSeparatorTags)
                        onShowSeparatorDialogChange(false)
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDialogSeparatorTagsChange(emptySet())
                    onShowSeparatorDialogChange(false)
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (pendingDeleteSeparator != null) {
        AlertDialog(
            onDismissRequest = { onPendingDeleteSeparatorChange(null) },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_separator_delete_title)) },
            text = { Text(stringResource(R.string.settings_separator_delete_message, pendingDeleteSeparator)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDialogSeparatorTagsChange(dialogSeparatorTags - pendingDeleteSeparator)
                        onPendingDeleteSeparatorChange(null)
                    }
                ) {
                    Text(stringResource(R.string.settings_separator_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onPendingDeleteSeparatorChange(null) }) {
                    Text(stringResource(R.string.settings_separator_cancel))
                }
            }
        )
    }
}

@Composable
private fun SeparatorChip(
    separator: String,
    onDelete: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        onClick = {},
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.pointerInput(separator) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = separator,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.settings_separator_delete_cd),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
