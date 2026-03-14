package com.voxly.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import kotlinx.coroutines.launch

// ============ Helper Composable & Data ============

@Composable
private fun TitleSubtitleContent(
    title: String,
    subtitle: String?,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier
) = Column(modifier = modifier) {
    Text(text = title, style = titleStyle)
    subtitle?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        checked = false,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = modifier.fillMaxWidth(),
        content = {}
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Text(value) },
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
}

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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle) },
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth(),
        content = {}
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle, titleStyle ?: MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { btnIndex, option ->
                    SegmentedButton(
                        selected = option.value == selectedValue,
                        onClick = { onSelected(option.value) },
                        shape = SegmentedButtonDefaults.itemShape(btnIndex, options.size),
                        icon = {
                            option.icon?.let { icon ->
                                Icon(icon, iconContentDescription?.invoke(option), Modifier.size(18.dp))
                            }
                        }
                    ) { Text(option.label ?: "") }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        content = {}
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = 100.dp)) },
        trailingContent = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ButtonGroup {
                    options.forEachIndexed { btnIndex, option ->
                        val isSelected = option.value == selectedValue
                        val weight by animateFloatAsState(
                            targetValue = if (isSelected) 1.3f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "weight_anim"
                        )
                        val shapes = when {
                            options.size == 1 -> ToggleButtonDefaults.shapes()
                            btnIndex == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            btnIndex == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = { if (it) onSelected(option.value) },
                            modifier = Modifier.weight(weight),
                            shapes = shapes
                        ) {
                            option.icon?.let { Icon(it, option.label, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                            Text(option.label ?: "", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
}

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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = 100.dp)) },
        trailingContent = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                options.forEachIndexed { btnIndex, option ->
                    val isSelected = option.value == selectedValue
                    val shapes = when {
                        options.size == 1 -> ToggleButtonDefaults.shapes()
                        btnIndex == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        btnIndex == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = { if (it) onSelected(option.value) },
                        shapes = shapes
                    ) {
                        option.icon?.let { Icon(it, option.label, Modifier.size(16.dp)) }
                        option.label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
}

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
) = Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleSubtitleContent(title, subtitle, MaterialTheme.typography.titleMedium)
        ButtonGroup(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { btnIndex, option ->
                val isSelected = option.value == selectedValue
                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 1.3f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "weight_anim"
                )
                val buttonShapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapes()
                    btnIndex == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    btnIndex == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (it) onSelected(option.value) },
                    modifier = Modifier.weight(weight),
                    shapes = buttonShapes
                ) {
                    option.icon?.let { Icon(it, option.label, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(option.label ?: "", style = MaterialTheme.typography.labelMedium)
                }
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { TitleSubtitleContent(title, subtitle, MaterialTheme.typography.bodyLarge, Modifier.widthIn(max = 100.dp)) },
        trailingContent = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ButtonGroup {
                    options.forEachIndexed { btnIndex, option ->
                        val tooltipState = rememberTooltipState()
                        val isSelected = option.value == selectedValue
                        val weight by animateFloatAsState(
                            targetValue = if (isSelected) 1.3f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "weight_anim"
                        )
                        val shapes = when {
                            options.size == 1 -> ToggleButtonDefaults.shapes()
                            btnIndex == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            btnIndex == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        Box {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                tooltip = { PlainTooltip { Text(option.label ?: "") } },
                                state = tooltipState
                            ) {
                                ToggleButton(
                                    checked = isSelected,
                                    onCheckedChange = { if (it) onSelected(option.value) },
                                    modifier = Modifier.weight(weight).height(40.dp),
                                    shapes = shapes
                                ) {
                                    option.icon?.let { Icon(it, option.label, Modifier.size(22.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
}

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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(),
        leadingContent = leadingContent ?: { TitleSubtitleContent(title, subtitle) },
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
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
) = Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    ListItem(
        onClick = onClick,
        colors = ListItemDefaults.colors(),
        leadingContent = leadingContent ?: { TitleSubtitleContent(title, subtitle) },
        trailingContent = menuContent,
        modifier = modifier.fillMaxWidth(),
        content = {}
    )
}

// ============ Audio File Standard Components ============

/**
 * Standard audio file list item (full mode).
 * Uses M3E Standard ListItem.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioFileStandardRow(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) = Surface(
    modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surface
) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtImage(
            filePath = audioFile.path,
            mediaStoreAlbumId = audioFile.mediaStoreAlbumId,
            contentDescription = null,
            size = 64.dp,
            modifier = Modifier.clip(MaterialTheme.shapes.medium)
        ) {
            Icon(appIconPainter(AppIcon.MusicNote), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    val artist = audioFile.metadata.artist; val album = audioFile.metadata.album
                    when { artist != null && album != null -> append("$artist - $album"); artist != null -> append(artist); album != null -> append(album) }
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${audioFile.format} • ${audioFile.getFormattedDuration()} • ${audioFile.getFormattedSize()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
    }
}

/**
 * Actions menu for audio file items (three dots menu).
 */
@Composable
private fun AudioFileActionsMenu(
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = true }) { Icon(appIconPainter(AppIcon.MoreVert), null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit Metadata") },
                leadingIcon = { Icon(appIconPainter(AppIcon.Edit), null) },
                onClick = { expanded = false; onEditMetadata() }
            )
            DropdownMenuItem(
                text = { Text("Fetch Online Metadata") },
                leadingIcon = { Icon(appIconPainter(AppIcon.CloudDownload), null) },
                onClick = { expanded = false; onFetchOnlineMetadata() }
            )
            DropdownMenuItem(
                text = { Text("Fix Metadata") },
                leadingIcon = { Icon(appIconPainter(AppIcon.AutoFix), null) },
                onClick = { expanded = false; onFixMetadata() }
            )
            Surface(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant) { Spacer(Modifier.height(1.dp)) }
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(appIconPainter(AppIcon.Rename), null) },
                onClick = { expanded = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(appIconPainter(AppIcon.Close), null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

/**
 * Standard audio file list item with actions menu (full mode).
 * Uses M3E Standard ListItem with three-dot menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioFileStandardRowWithMenu(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onEditMetadata: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFetchOnlineMetadata: () -> Unit,
    onFixMetadata: () -> Unit,
    modifier: Modifier = Modifier
) = Surface(
    modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surface
) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtImage(filePath = audioFile.path, mediaStoreAlbumId = audioFile.mediaStoreAlbumId, contentDescription = null, size = 64.dp, modifier = Modifier.clip(MaterialTheme.shapes.medium)) {
            Icon(appIconPainter(AppIcon.MusicNote), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString { val a = audioFile.metadata.artist; val b = audioFile.metadata.album; when { a != null && b != null -> append("$a - $b"); a != null -> append(a); b != null -> append(b) } },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${audioFile.format} • ${audioFile.getFormattedDuration()} • ${audioFile.getFormattedSize()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
        else AudioFileActionsMenu(onEditMetadata, onRename, onDelete, onFetchOnlineMetadata, onFixMetadata)
    }
}

/**
 * Standard audio file list item (compact mode).
 * Uses Card with smaller layout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioFileStandardRowCompact(
    audioFile: AudioFile,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    onClick = onClick
) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.extraSmall), contentAlignment = Alignment.Center) {
            AlbumArtImage(filePath = audioFile.path, mediaStoreAlbumId = audioFile.mediaStoreAlbumId, contentDescription = null, size = 40.dp, modifier = Modifier.fillMaxSize()) {
                Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(audioFile.metadata.getDisplayTitle(audioFile.name), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(audioFile.metadata.album ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(audioFile.getFormattedDuration(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        if (isSelected) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
    }
}
