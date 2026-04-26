package com.voxly.presentation.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
private val AlbumArtSizeLarge = 64.dp
private val AlbumArtSizeSmall = 40.dp
private val VerticalLayoutPadding = 16.dp
private val VerticalItemSpacing = 12.dp
private val MenuDividerPadding = 4.dp

// ============ Helper Functions ============

@Composable
private fun getConnectedButtonShapes(options: List<*>, btnIndex: Int) = when {
    options.size == 1 -> ToggleButtonDefaults.shapes()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
    checked = false,
    onCheckedChange = onCheckedChange,
    shapes = ListItemDefaults.segmentedShapes(index, count),
    colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    modifier = modifier.fillMaxWidth(),
    content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
    supportingContent = subtitle?.let {
        { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
)

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
    modifier: Modifier = Modifier
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
        iconContentDescription = null
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedInfoRow(
    title: String,
    value: String,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
    onClick = {},
    shapes = ListItemDefaults.segmentedShapes(index, count),
    colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    leadingContent = { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    trailingContent = { Text(value) },
    modifier = modifier.fillMaxWidth(),
    content = {}
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedClickableRow(
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
    onClick = onClick,
    shapes = ListItemDefaults.segmentedShapes(index, count),
    colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    trailingContent = trailingContent,
    modifier = modifier.fillMaxWidth(),
    content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
    supportingContent = subtitle?.let {
        { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
)

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
    modifier: Modifier = Modifier
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
        iconContentDescription = { it.label ?: "" }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    iconContentDescription: ((SegmentedOption<T>) -> String)?
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
        modifier = modifier.fillMaxWidth(),
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroupRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
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
    modifier = modifier.fillMaxWidth(),
    content = {}
)

/**
 * Segmented row with compact connected button group - no spacing between buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroupRowCompact(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
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
    modifier = modifier.fillMaxWidth(),
    content = {}
)

/**
 * Segmented row with vertical layout - title on top, buttons below.
 * For settings like ReplayGain with longer option labels.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedButtonGroupVerticalRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    onClick = {} // Required for Surface to have proper shape
) {
    Column(modifier = Modifier.fillMaxWidth().padding(VerticalLayoutPadding), verticalArrangement = Arrangement.spacedBy(VerticalItemSpacing)) {
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

/**
 * Segmented row with icon-only connected button group.
 * Shows only icons with tooltips for each option.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedIconOnlyButtonGroupRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier
) = SegmentedListItem(
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
    modifier = modifier.fillMaxWidth(),
    content = {}
)

// ============ Standard List Components (non-segmented) ============

/**
 * Standard list item with click action.
 * Uses ListItem with default shapes (not segmented).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) = ListItem(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    overlineContent = overlineContent,
    supportingContent = supportingContent,
    content = content
)

/** Standard clickable row - clickable list item without selection. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardClickableRow(
    title: String,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth()
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardClickableRowWithMenu(
    title: String,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    menuContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        leadingContent = leadingContent,
        trailingContent = menuContent,
        modifier = modifier.fillMaxWidth()
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
    sharedElementKey: String? = null
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    val rowModifier = modifier
        .fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    ListItem(
        modifier = rowModifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
    headlineContent = {
        Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    supportingContent = {
        Column {
            Text(
                audioFile.getDisplayArtistAlbum(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${audioFile.format} • ${audioFile.getFormattedDuration()} • ${audioFile.getFormattedSize()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    },
    leadingContent = {
        val albumArtKey = sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
        val cookieShape = MaterialShapes.Cookie9Sided.toShape()
        val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = albumArtKey),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(AlbumArtSizeLarge)
                .clip(cookieShape)
                .then(sharedModifier),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                filePath = audioFile.path,
                albumId = audioFile.mediaStoreAlbumId,
                contentDescription = null,
                size = AlbumArtSizeLarge,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(appIconPainter(AppIcon.MusicNote), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(IconSizeLarge))
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
 * Uses M3E Standard ListItem with three-dot menu.
 * Uses sealed class [AudioFileAction] for type-safe action handling.
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
    sharedElementKey: String? = null
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        headlineContent = {
            Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    audioFile.getDisplayArtistAlbum(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${audioFile.format} • ${audioFile.getFormattedDuration()} • ${audioFile.getFormattedSize()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        leadingContent = {
            val albumArtKey = sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
            val cookieShape = MaterialShapes.Cookie9Sided.toShape()
            val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = albumArtKey),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                }
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(AlbumArtSizeLarge)
                    .clip(cookieShape)
                    .then(sharedModifier),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    filePath = audioFile.path,
                    albumId = audioFile.mediaStoreAlbumId,
                    contentDescription = null,
                    size = AlbumArtSizeLarge,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(appIconPainter(AppIcon.MusicNote), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(IconSizeLarge))
                }
            }
        },
        trailingContent = {
            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(IconPadding))
            else AudioFileActionsMenu(onAction)
        }
    )
}

/**
 * Standard audio file list item (compact mode).
 * Uses Surface with smaller layout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AudioFileStandardRowCompact(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    sharedElementKey: String? = null
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current
    ListItem(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    colors = ListItemDefaults.colors(
        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    ),
    headlineContent = {
        Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    supportingContent = {
        val displayText = buildString {
            audioFile.metadata.artist?.let { append(it) }
            audioFile.metadata.album?.let {
                if (isNotEmpty()) append(" - ")
                append(it)
            }
        }
        if (displayText.isNotEmpty()) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    leadingContent = {
        val albumArtKey = sharedElementKey ?: createAlbumArtSharedElementKey(audioFile.path)
        val cookieShape = MaterialShapes.Cookie9Sided.toShape()
        val sharedModifier = if (sharedElementKey != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = albumArtKey),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .size(AlbumArtSizeLarge)
                .clip(cookieShape)
                .then(sharedModifier),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtImage(
                filePath = audioFile.path,
                albumId = audioFile.mediaStoreAlbumId,
                contentDescription = null,
                size = AlbumArtSizeSmall,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Surface(modifier = Modifier.fillMaxSize(), shape = cookieShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSizeSmall)) }
                }
            }
        }
    },
    trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(audioFile.getFormattedDuration(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (isSelected) { Spacer(Modifier.width(CompactPadding)); Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSizeSmall)) }
        }
    }
)
}
