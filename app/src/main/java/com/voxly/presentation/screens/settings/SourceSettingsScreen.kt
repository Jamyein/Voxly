package com.voxly.presentation.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxly.R
import com.voxly.domain.model.DataSourceType
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyScaffold
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.theme.emphasizedTitleMedium
import com.voxly.presentation.viewmodel.SettingsViewModel
import com.voxly.presentation.viewmodel.SourceCardItem
import kotlin.math.roundToInt

// Layout constants
private val HorizontalPadding = 16.dp
private val CardSpacing = 10.dp
private val CardHeight = 116.dp

/**
 * Data source settings subpage.
 *
 * Each online source (iTunes, MusicBrainz, NetEase, QQ Music) is a card. Dragging a
 * card reorders the GLOBAL priority shared by all three groups (combined / lyrics /
 * cover); the per-group toggles inside the card decide whether that source is used
 * for each group. The effective priority of a group = global order restricted to the
 * sources enabled for that group.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SourceSettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceOrderState by viewModel.sourceOrderState.collectAsStateWithLifecycle()

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
    val currentAppleCountry = appleCountryOptions.firstOrNull {
        it.countryValue == uiState.appleCountryCode.lowercase()
    } ?: appleCountryOptions.first()

    // Snapshot the current global order once when the page opens.
    LaunchedEffect(Unit) {
        viewModel.openSourceOrder()
    }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { CardHeight.toPx() }

    VoxlyScaffold(
        topBar = {
            VoxlyTopAppBar(
                large = true,
                theme = TopBarTheme.Library,
                title = { Text(stringResource(R.string.settings_source_panel_title)) },
                onBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HorizontalPadding)
        ) {
            // Guidance banner
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_source_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card list (LazyColumn owns scrolling). animateItem gives smooth
            // slot hand-off when a dragged card swaps positions.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = sourceOrderState != null,
                    enter = ExpressiveAnimations.listItemEnter(),
                    exit = ExpressiveAnimations.fadeExit()
                ) {
                    val state = sourceOrderState
                    if (state != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(CardSpacing),
                            contentPadding = PaddingValues(
                                bottom = WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() + 24.dp
                            )
                        ) {
                        items(state.sources, key = { it.sourceId }) { item ->
                            val displayIndex = state.sources.indexOfFirst { it.sourceId == item.sourceId }
                            val isDragging = state.draggedIndex == displayIndex
                            SourceCard(
                                index = displayIndex,
                                item = item,
                                isDragging = isDragging,
                                dragOffset = if (isDragging) state.dragOffset else 0f,
                                appleCountryOptions = appleCountryOptions,
                                currentAppleCountry = currentAppleCountry,
                                onDragStart = { viewModel.startDragging(displayIndex) },
                                onDragEnd = viewModel::endDragging,
                                onDragCancel = viewModel::cancelDragging,
                                onDrag = { dragAmount ->
                                    viewModel.updateDragOffset(dragAmount, itemHeightPx)
                                },
                                onGroupToggle = { type, enabled ->
                                    viewModel.setSourceEnabled(type, item.sourceId, enabled)
                                },
                                onAppleCountryChange = viewModel::setAppleCountryCode,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = if (isDragging) {
                                        null // dragged card follows the finger via dragOffset
                                    } else {
                                        MaterialTheme.motionScheme.defaultSpatialSpec()
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * One source card: rank badge + name + actions (country capsule / drag handle) on
 * the header row, multi-select group toggles (combined / lyrics / cover) below.
 * Long-press anywhere to start dragging.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceCard(
    index: Int,
    item: SourceCardItem,
    isDragging: Boolean,
    dragOffset: Float,
    appleCountryOptions: List<AppleCountryOption>,
    currentAppleCountry: AppleCountryOption,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onGroupToggle: (DataSourceType, Boolean) -> Unit,
    onAppleCountryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "source_card_scale"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "source_card_elevation"
    )
    val allDisabled = !item.enabledMetadata && !item.enabledLyrics && !item.enabledCover
    var showCountryMenu by remember { mutableStateOf(false) }
    // pointerInput(Unit) runs its block once on first composition, so it captures the
    // callbacks as they were THEN — including the stale displayIndex baked into
    // onDragStart. rememberUpdatedState swaps in the latest callback, so a long-press
    // always starts dragging the source under the finger, even after cards reordered.
    val currentOnDragStart by rememberUpdatedState(onDragStart)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardHeight)
            .scale(animatedScale)
            .alpha(if (allDisabled) 0.55f else 1f)
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
                    onDragStart = { currentOnDragStart() },
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = animatedElevation,
            pressedElevation = animatedElevation,
            draggedElevation = animatedElevation
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header row: rank badge | name (+ disabled label) | country | drag handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RankBadge(rank = index + 1)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sourceToDisplayName(item.sourceId),
                        style = emphasizedTitleMedium,
                        maxLines = 1
                    )
                    if (allDisabled) {
                        Text(
                            text = stringResource(R.string.settings_source_disabled_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (sourceHasExtraOptions(item.sourceId)) {
                    CountryCapsule(
                        currentAppleCountry = currentAppleCountry,
                        appleCountryOptions = appleCountryOptions,
                        expanded = showCountryMenu,
                        onExpandedChange = { showCountryMenu = it },
                        onCountrySelected = onAppleCountryChange
                    )
                }
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.settings_drag_handle),
                    tint = if (isDragging)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Group toggles: multi-select segmented buttons.
            GroupToggleRow(
                item = item,
                onGroupToggle = onGroupToggle
            )
        }
    }
}

/** Round rank badge — filled for first place, subtle for the rest. */
@Composable
private fun RankBadge(rank: Int) {
    val isFirst = rank == 1
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isFirst) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isFirst)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.primary
        )
    }
}

/** iTunes store-region capsule: region name + dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCapsule(
    currentAppleCountry: AppleCountryOption,
    appleCountryOptions: List<AppleCountryOption>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCountrySelected: (String) -> Unit
) {
    Box {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(currentAppleCountry.labelResId),
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            appleCountryOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelResId)) },
                    onClick = {
                        onCountrySelected(option.countryValue)
                        onExpandedChange(false)
                    },
                    colors = MenuDefaults.itemColors(),
                    trailingIcon = {
                        if (option.countryValue == currentAppleCountry.countryValue) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

/** Multi-select segmented buttons: combined / lyrics / cover. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupToggleRow(
    item: SourceCardItem,
    onGroupToggle: (DataSourceType, Boolean) -> Unit
) {
    val groups = listOf(
        Triple(DataSourceType.METADATA, R.string.settings_source_panel_metadata, Icons.Default.AutoFixHigh),
        Triple(DataSourceType.LYRICS, R.string.settings_source_panel_lyrics, Icons.Default.MusicNote),
        Triple(DataSourceType.COVER, R.string.settings_source_panel_cover, Icons.Default.Album)
    )
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        groups.forEachIndexed { btnIndex, (type, labelRes, icon) ->
            val checked = when (type) {
                DataSourceType.METADATA -> item.enabledMetadata
                DataSourceType.LYRICS -> item.enabledLyrics
                DataSourceType.COVER -> item.enabledCover
            }
            val buttonModifier = Modifier
                .weight(1f)
                .height(40.dp)
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { newChecked -> onGroupToggle(type, newChecked) },
                        modifier = buttonModifier,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shapes = when (btnIndex) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            groups.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        leadingIcon = { Icon(icon, null) },
                        onClick = {
                            onGroupToggle(type, !checked)
                            state.dismiss()
                        }
                    )
                }
            )
        }
    }
}
