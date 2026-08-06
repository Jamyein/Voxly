package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonGroupMenuState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.MaterialShapes
import com.voxly.presentation.theme.VoxlyStyles
import com.voxly.presentation.theme.emphasizedTitleMedium
import com.voxly.presentation.theme.rememberCoverMorphShape
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import kotlinx.coroutines.launch

// ============ Constants ============

private val TitleMaxWidth = 100.dp
// Icon sizes
private val IconSizeLarge = 24.dp   // 占位符
private val IconSizeMedium = 18.dp  // 普通按钮
private val IconSizeSmall = 16.dp   // 紧凑模式
private val IconSizeCompact = 22.dp // 仅图标按钮

// List item padding
private val ListItemPaddingHorizontal = 12.dp
private val ListItemPaddingVertical = 12.dp
private val CompactPadding = 8.dp
private val IconPadding = 8.dp
private val IconTextSpacing = 4.dp
private val ContentSpacing = 10.dp
private val ButtonHeight = 40.dp
private val ConnectedButtonMinWidth = 56.dp
private val ConnectedCompactButtonMinWidth = 48.dp
private val ConnectedButtonHorizontalPadding = 12.dp
private const val SelectedButtonWeight = 1.24f
private const val UnselectedButtonWeight = 1.0f
private val VerticalItemSpacing = 12.dp
private val MenuDividerPadding = 4.dp

// ============ Helper Functions ============

@Composable
private fun getConnectedButtonShapes(options: List<*>, btnIndex: Int) = when {
    options.size == 1 -> ToggleButtonShapes(
        shape = ToggleButtonDefaults.shape,
        pressedShape = ToggleButtonDefaults.pressedShape,
        checkedShape = ToggleButtonDefaults.checkedShape
    )
    btnIndex == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    btnIndex == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DefaultButtonGroupOverflowIndicator(menuState: ButtonGroupMenuState) {
    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
}

private val ConnectedButtonArrangement =
    Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)

@Composable
private fun <T> rememberConnectedButtonWeights(
    options: List<T>,
    selectedValue: T,
    selectedWeight: Float = SelectedButtonWeight,
    unselectedWeight: Float = UnselectedButtonWeight
): List<Float> = options.map { option ->
    val animatedWeight by animateFloatAsState(
        targetValue = if (option == selectedValue) selectedWeight else unselectedWeight,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "connected_button_weight"
    )
    animatedWeight
}

// ============ Extension Functions ============

/**
 * Get display string for artist and album combination.
 * Returns: "artist - album" or "artist" or "album" or ""
 */
private fun AudioFile.getDisplayArtistAlbum(): String = buildString {
    val artist = metadata.artist
    val album = metadata.album
    when {
        artist != null && album != null -> append("$artist - $album")
        artist != null -> append(artist)
        album != null -> append(album)
    }
}

/**
 * Sealed class for audio file actions to simplify callback parameters.
 */
sealed class AudioFileAction {
    data object EditMetadata : AudioFileAction()
    data object FetchOnlineMetadata : AudioFileAction()
    data object FixMetadata : AudioFileAction()
    data object Rename : AudioFileAction()
    data object Delete : AudioFileAction()
}

@Composable
private fun TitleSubtitleContent(
    title: String,
    subtitle: String?,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier
) = Column(modifier = modifier) {
    Text(text = title, style = titleStyle)
    subtitle?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true
        )
    }
}

data class SegmentedOption<T>(val value: T, val icon: ImageVector? = null, val label: String? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun SegmentedSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    val styleState = rememberUpdatedStyleState(null) { it.isChecked = checked }
    SegmentedListItem(
        checked = false,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = modifier.fillMaxWidth().styleable(styleState, VoxlyStyles.settingsRowStyle, style),
        content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedButtonRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedButtonImpl(
        title = title,
        subtitle = subtitle,
        options = options,
        selectedValue = selectedValue,
        onSelected = onSelected,
        index = index,
        count = count,
        modifier = modifier,
        titleStyle = null,
        iconContentDescription = null,
        style = style
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun SegmentedInfoRow(
    title: String,
    value: String,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Text(value) },
        modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
        content = {}
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun SegmentedClickableRow(
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
        content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

/**
 * Segmented row with segmented button group for selecting one option.
 * Uses SingleChoiceSegmentedButtonRow with SegmentedButton for M3E Expressive style.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedButton(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedButtonImpl(
        title = title,
        subtitle = subtitle,
        options = options,
        selectedValue = selectedValue,
        onSelected = onSelected,
        index = index,
        count = count,
        modifier = modifier,
        titleStyle = MaterialTheme.typography.bodyLarge,
        iconContentDescription = { it.label ?: "" },
        style = style
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
private fun <T> SegmentedButtonImpl(
    title: String,
    subtitle: String?,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle?,
    iconContentDescription: ((SegmentedOption<T>) -> String)?,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        trailingContent = {
            val animatedWeights = rememberConnectedButtonWeights(
                options = options.map { it.value },
                selectedValue = selectedValue
            )
            // Using ButtonGroup with ToggleButton for M3E Connected style (replaces deprecated SegmentedButton)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        DefaultButtonGroupOverflowIndicator(menuState)
                    },
                    horizontalArrangement = ConnectedButtonArrangement
                ) {
                    options.forEachIndexed { btnIndex, option ->
                        val isSelected = option.value == selectedValue
                        val buttonModifier = Modifier
                            .weight(animatedWeights[btnIndex])
                            .defaultMinSize(
                                minWidth = ConnectedButtonMinWidth,
                                minHeight = ButtonHeight
                            )
                        customItem(
                            buttonGroupContent = {
                                val shapes = getConnectedButtonShapes(options, btnIndex)
                                ToggleButton(
                                    checked = isSelected,
                                    onCheckedChange = { if (it) onSelected(option.value) },
                                    modifier = buttonModifier,
                                    contentPadding = PaddingValues(
                                        horizontal = ConnectedButtonHorizontalPadding,
                                        vertical = CompactPadding
                                    ),
                                    shapes = shapes
                                ) {
                                    option.icon?.let { icon ->
                                        Icon(icon, iconContentDescription?.invoke(option), Modifier.size(IconSizeMedium))
                                        Spacer(Modifier.width(IconTextSpacing))
                                    }
                                    Text(option.label ?: "", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(option.label ?: "") },
                                    leadingIcon = option.icon?.let { icon ->
                                        {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = iconContentDescription?.invoke(option)
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelected(option.value)
                                        state.dismiss()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
        content = { Text(text = title, style = titleStyle ?: MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}


/**
 * Segmented row with connected button group for selecting one option.
 * Uses ButtonGroup with ToggleButton for M3E Expressive Connected style.
 * Features weight animation for expressive feel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun <T> ConnectedButtonGroupRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = TitleMaxWidth)) },
    trailingContent = {
        val animatedWeights = rememberConnectedButtonWeights(
            options = options.map { it.value },
            selectedValue = selectedValue
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    DefaultButtonGroupOverflowIndicator(menuState)
                },
                horizontalArrangement = ConnectedButtonArrangement
            ) {
                options.forEachIndexed { btnIndex, option ->
                    val isSelected = option.value == selectedValue
                    val buttonModifier = Modifier
                        .weight(animatedWeights[btnIndex])
                        .defaultMinSize(
                            minWidth = ConnectedButtonMinWidth,
                            minHeight = ButtonHeight
                        )
                    customItem(
                        buttonGroupContent = {
                            val shapes = getConnectedButtonShapes(options, btnIndex)
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { if (it) onSelected(option.value) },
                                modifier = buttonModifier,
                                contentPadding = PaddingValues(
                                    horizontal = ConnectedButtonHorizontalPadding,
                                    vertical = CompactPadding
                                ),
                                shapes = shapes
                            ) {
                                option.icon?.let { Icon(it, option.label, Modifier.size(IconSizeMedium)); Spacer(Modifier.width(IconTextSpacing)) }
                                Text(option.label ?: "", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        menuContent = { state ->
                            DropdownMenuItem(
                                text = { Text(option.label ?: "") },
                                leadingIcon = option.icon?.let { icon ->
                                    { Icon(icon, option.label) }
                                },
                                onClick = {
                                    onSelected(option.value)
                                    state.dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    },
    modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
    content = {}
    )
}

/**
 * Segmented row with compact connected button group - no spacing between buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun <T> ConnectedButtonGroupRowCompact(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = TitleMaxWidth)) },
    trailingContent = {
        val animatedWeights = rememberConnectedButtonWeights(
            options = options.map { it.value },
            selectedValue = selectedValue,
            selectedWeight = 1.16f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            // Compact mode: no spacing between buttons
            ButtonGroup(
                overflowIndicator = { menuState ->
                    DefaultButtonGroupOverflowIndicator(menuState)
                },
                horizontalArrangement = ConnectedButtonArrangement
            ) {
                options.forEachIndexed { btnIndex, option ->
                    val isSelected = option.value == selectedValue
                    val buttonModifier = Modifier
                        .weight(animatedWeights[btnIndex])
                        .defaultMinSize(
                            minWidth = ConnectedCompactButtonMinWidth,
                            minHeight = ButtonHeight
                        )
                    customItem(
                        buttonGroupContent = {
                            val shapes = getConnectedButtonShapes(options, btnIndex)
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { if (it) onSelected(option.value) },
                                modifier = buttonModifier,
                                contentPadding = PaddingValues(
                                    horizontal = CompactPadding,
                                    vertical = CompactPadding
                                ),
                                shapes = shapes
                            ) {
                                option.icon?.let { Icon(it, option.label, Modifier.size(IconSizeSmall)) }
                                option.label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                        },
                        menuContent = { state ->
                            DropdownMenuItem(
                                text = { Text(option.label ?: "") },
                                leadingIcon = option.icon?.let { icon ->
                                    { Icon(icon, option.label) }
                                },
                                onClick = {
                                    onSelected(option.value)
                                    state.dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    },
    modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
    content = {}
    )
}

/**
 * Segmented row with vertical layout - title on top, buttons below.
 * For settings like ReplayGain with longer option labels.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun <T> ConnectedButtonGroupVerticalRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    // Styles API owns the container: shape / surfaceContainer background / 16dp padding
    // (previously a `Surface(onClick = {})` hack).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .styleable(null, VoxlyStyles.verticalSettingsCard, style)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VerticalItemSpacing)) {
        TitleSubtitleContent(title, subtitle, MaterialTheme.typography.titleMedium)
        val animatedWeights = rememberConnectedButtonWeights(
            options = options.map { it.value },
            selectedValue = selectedValue
        )
        ButtonGroup(
            overflowIndicator = { menuState ->
                DefaultButtonGroupOverflowIndicator(menuState)
            },
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = ConnectedButtonArrangement
        ) {
            options.forEachIndexed { btnIndex, option ->
                val isSelected = option.value == selectedValue
                val buttonModifier = Modifier
                    .weight(animatedWeights[btnIndex])
                    .defaultMinSize(
                        minWidth = ConnectedButtonMinWidth,
                        minHeight = ButtonHeight
                    )
                customItem(
                    buttonGroupContent = {
                        val buttonShapes = getConnectedButtonShapes(options, btnIndex)
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = { if (it) onSelected(option.value) },
                            modifier = buttonModifier,
                            contentPadding = PaddingValues(
                                horizontal = ConnectedButtonHorizontalPadding,
                                vertical = CompactPadding
                            ),
                            shapes = buttonShapes
                        ) {
                            option.icon?.let { Icon(it, option.label, Modifier.size(IconSizeSmall)); Spacer(Modifier.width(IconTextSpacing)) }
                            Text(option.label ?: "", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    menuContent = { state ->
                        DropdownMenuItem(
                            text = { Text(option.label ?: "") },
                            leadingIcon = option.icon?.let { icon ->
                                { Icon(icon, option.label) }
                            },
                            onClick = {
                                onSelected(option.value)
                                state.dismiss()
                            }
                        )
                    }
                )
            }
        }
    }
    }
}

/**
 * Segmented row with icon-only connected button group.
 * Shows only icons with tooltips for each option.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun <T> ConnectedIconOnlyButtonGroupRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = TitleMaxWidth)) },
    trailingContent = {
        val animatedWeights = rememberConnectedButtonWeights(
            options = options.map { it.value },
            selectedValue = selectedValue,
            selectedWeight = 1.18f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    DefaultButtonGroupOverflowIndicator(menuState)
                },
                horizontalArrangement = ConnectedButtonArrangement
            ) {
                options.forEachIndexed { btnIndex, option ->
                    val isSelected = option.value == selectedValue
                    val buttonModifier = Modifier
                        .weight(animatedWeights[btnIndex])
                        .defaultMinSize(
                            minWidth = ConnectedCompactButtonMinWidth,
                            minHeight = ButtonHeight
                        )
                    customItem(
                        buttonGroupContent = {
                            val tooltipState = rememberTooltipState()
                            val shapes = getConnectedButtonShapes(options, btnIndex)
                            Box {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(option.label ?: "") } },
                                    state = tooltipState
                                ) {
                                    ToggleButton(
                                        checked = isSelected,
                                        onCheckedChange = { if (it) onSelected(option.value) },
                                        modifier = buttonModifier,
                                        contentPadding = PaddingValues(CompactPadding),
                                        shapes = shapes
                                    ) {
                                        option.icon?.let { Icon(it, option.label, Modifier.size(IconSizeCompact)) }
                                    }
                                }
                            }
                        },
                        menuContent = { state ->
                            DropdownMenuItem(
                                text = { Text(option.label ?: "") },
                                leadingIcon = option.icon?.let { icon ->
                                    { Icon(icon, option.label) }
                                },
                                onClick = {
                                    onSelected(option.value)
                                    state.dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    },
    modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style),
    content = {}
    )
}

// ============ Standard List Components (non-segmented) ============

/**
 * Standard list item with click action.
 * Uses ListItem with default shapes (not segmented).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun StandardListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val styleState = rememberUpdatedStyleState(null) { it.isEnabled = enabled }
    ListItem(
        onClick = onClick,
        modifier = modifier.styleable(styleState, VoxlyStyles.settingsRowStyle, style),
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        content = content
    )
}

/** Standard clickable row - clickable list item without selection. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun StandardClickableRow(
    title: String,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Standard clickable row with more (three dots) menu button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationStyleApi::class)
@Composable
fun StandardClickableRowWithMenu(
    title: String,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    menuContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style
) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = leadingContent,
        trailingContent = menuContent,
        modifier = modifier.fillMaxWidth().styleable(null, VoxlyStyles.settingsRowStyle, style)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============ Audio File Standard Components ============

/**
 * Standard audio file list item (full mode).
 * Uses M3E Standard ListItem.
 *
 * @param sharedElementKey Optional key for shared element transition (Container Transform).
 *                         When provided, the row will participate in shared element animations.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AudioFileStandardRow(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAction: ((com.voxly.presentation.components.AudioFileAction) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    // 无封面占位用角色渐变，title hash 保证同一首歌颜色稳定（同紧凑行）
    val title = remember(audioFile) { audioFile.metadata.getDisplayTitle(audioFile.name) }
    val roleAccent = rememberRoleAccent(title)
    val coverSize = 72.dp

    ListItem(
        modifier = rowModifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        content = {
        Text(title, style = emphasizedTitleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    supportingContent = {
        val artistAlbum = remember(audioFile) { audioFile.getDisplayArtistAlbum() }
        val fileInfo = remember(audioFile) {
            "${audioFile.format} • ${audioFile.getFormattedDuration()} • ${audioFile.getFormattedSize()}"
        }
        Column {
            Text(
                artistAlbum,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(fileInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    },
    leadingContent = {
        val albumArtKey = sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
        // 形状渐变源端（Level 2）：pop 时从目标页返回，匹配行需"从目标端圆角方形渐变回 Cookie"。
        // 不用 scope 级 transition.animateFloat（非匹配行会集体形变）——用行级 Animatable：
        // 初始 Cookie（settled）；match 形成瞬间 snapTo 圆角方形（此刻仍被目标页盖住、不可见），
        // 再以与 bounds 相同的 spring 渐变回 Cookie——overlay 首帧形状连续，无跳变/闪。
        val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
        val coverSharedState = if (canUseSharedTransition) {
            with(sharedTransitionScope) { rememberSharedContentState(key = albumArtKey) }
        } else {
            null
        }
        val isCoverMatching = coverSharedState?.isMatchFound == true
        val settledCookieShape = MaterialShapes.Cookie9Sided.toShape()
        val coverShape = if (coverSharedState != null) {
            val shapeProgress = remember { Animatable(0f) }
            val morphSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            LaunchedEffect(isCoverMatching) {
                if (isCoverMatching) {
                    shapeProgress.snapTo(1f)
                    shapeProgress.animateTo(0f, morphSpec)
                }
            }
            rememberCoverMorphShape(shapeProgress.value)
        } else {
            settledCookieShape
        }
        val outerModifier = if (canUseSharedTransition) {
            with(sharedTransitionScope) {
                Modifier
                    .size(coverSize)
                    .sharedElement(
                        coverSharedState!!,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .clip(coverShape)
            }
        } else {
            Modifier
                .size(coverSize)
                .clip(coverShape)
        }
        Box(
            modifier = outerModifier,
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                filePath = audioFile.path,
                albumId = audioFile.mediaStoreAlbumId,
                contentDescription = null,
                size = coverSize,
                modifier = Modifier.fillMaxSize(),
                clipShape = coverShape
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(roleAccentGradient(roleAccent.accent)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(appIconPainter(AppIcon.MusicNote), null, tint = roleAccent.onAccent, modifier = Modifier.size(IconSizeLarge))
                }
            }
        }
    },
    trailingContent = {
        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(IconPadding))
        else if (onAction != null) AudioFileActionsMenu(onAction)
    }
)
}

/**
 * Actions menu for audio file items (three dots menu).
 * Uses sealed class for type-safe action handling.
 */
@Composable
private fun AudioFileActionsMenu(
    onAction: (AudioFileAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                painter = appIconPainter(AppIcon.MoreVert),
                contentDescription = stringResource(R.string.file_item_actions),
                tint = if (expanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp),
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.edit_metadata),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = { Icon(appIconPainter(AppIcon.Edit), stringResource(R.string.cd_edit_file)) },
                colors = MenuDefaults.itemColors(),
                onClick = { expanded = false; onAction(AudioFileAction.EditMetadata) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.fetch_online_metadata),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        appIconPainter(AppIcon.CloudDownload),
                        stringResource(R.string.cd_online_metadata)
                    )
                },
                colors = MenuDefaults.itemColors(),
                onClick = { expanded = false; onAction(AudioFileAction.FetchOnlineMetadata) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.fix_metadata),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = { Icon(appIconPainter(AppIcon.AutoFix), stringResource(R.string.cd_batch_fix)) },
                colors = MenuDefaults.itemColors(),
                onClick = { expanded = false; onAction(AudioFileAction.FixMetadata) }
            )
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(vertical = MenuDividerPadding)
            ) {}
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.rename_file),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = { Icon(appIconPainter(AppIcon.Rename), stringResource(R.string.cd_batch_rename)) },
                colors = MenuDefaults.itemColors(),
                onClick = { expanded = false; onAction(AudioFileAction.Rename) }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.log_viewer_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        appIconPainter(AppIcon.Close),
                        stringResource(R.string.cd_delete_file),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                ),
                onClick = { expanded = false; onAction(AudioFileAction.Delete) }
            )
        }
    }
}

/**
 * Standard audio file list item with actions menu (full mode).
 * 与 [AudioFileStandardRow] 共用实现，仅固定展示 ⋮ 菜单。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AudioFileStandardRowWithMenu(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAction: (AudioFileAction) -> Unit,
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    AudioFileStandardRow(
        audioFile = audioFile,
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
        onAction = onAction,
        modifier = modifier,
        sharedElementKey = sharedElementKey,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}

/**
 * Standard audio file list item (compact mode).
 * 所有音频页面的紧凑列表项：surfaceContainer 卡片化 + 圆角、时长 pill、
 * 无封面时角色渐变占位（title hash 保证同一首歌颜色稳定）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AudioFileStandardRowCompact(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = remember(audioFile) { audioFile.metadata.getDisplayTitle(audioFile.name) }
    // 无封面占位用角色渐变，title hash 保证同一首歌颜色稳定
    val roleAccent = rememberRoleAccent(title)
    // 封面主导：72dp 封面作为视觉主角，颜色来自封面/占位 + pill
    val coverSize = 72.dp

    ListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        // 扁平行：无卡片背景，靠封面/占位 + 时长 pill 提供颜色
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        content = {
        Text(title, style = emphasizedTitleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    supportingContent = {
        val displayText = remember(audioFile) {
            buildString {
                audioFile.metadata.artist?.let { append(it) }
                audioFile.metadata.album?.let {
                    if (isNotEmpty()) append(" - ")
                    append(it)
                }
            }
        }
        val duration = remember(audioFile) { audioFile.getFormattedDuration() }
        Column {
            if (displayText.isNotEmpty()) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    },
    leadingContent = {
        val albumArtKey = sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
        // 形状渐变源端（Level 2，紧凑行）：pop 时从目标页返回，匹配行需"从目标端圆角方形渐变回 Cookie"。
        // 行级 Animatable：match 形成瞬间 snapTo 圆角方形（此刻被目标页盖住、不可见），
        // 再以与 bounds 相同的 spring 渐变回 Cookie——overlay 首帧形状连续，无跳变。
        val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
        val coverSharedState = if (canUseSharedTransition) {
            with(sharedTransitionScope) { rememberSharedContentState(key = albumArtKey) }
        } else {
            null
        }
        val isCoverMatching = coverSharedState?.isMatchFound == true
        val settledCookieShape = MaterialShapes.Cookie9Sided.toShape()
        val coverShape = if (coverSharedState != null) {
            val shapeProgress = remember { Animatable(0f) }
            val morphSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            LaunchedEffect(isCoverMatching) {
                if (isCoverMatching) {
                    shapeProgress.snapTo(1f)
                    shapeProgress.animateTo(0f, morphSpec)
                }
            }
            rememberCoverMorphShape(shapeProgress.value)
        } else {
            settledCookieShape
        }
            val outerModifier = if (canUseSharedTransition) {
                with(sharedTransitionScope) {
                    Modifier
                        .size(coverSize)
                        .sharedElement(
                            coverSharedState!!,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .clip(coverShape)
                }
            } else {
                Modifier
                    .size(coverSize)
                    .clip(coverShape)
            }
            Box(
                modifier = outerModifier,
                contentAlignment = Alignment.Center
            ) {
                    AlbumArtImage(
                        filePath = audioFile.path,
                        albumId = audioFile.mediaStoreAlbumId,
                        contentDescription = null,
                        size = coverSize,
                        modifier = Modifier.fillMaxSize(),
                        clipShape = coverShape
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(roleAccentGradient(roleAccent.accent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null, tint = roleAccent.onAccent, modifier = Modifier.size(IconSizeLarge))
                        }
                    }
            }
    },
    trailingContent = {
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, null, tint = colorScheme.primary, modifier = Modifier.size(IconSizeMedium))
        }
    }
)
}
