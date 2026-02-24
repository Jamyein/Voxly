package com.voxly.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * MD3 Expressive Components - Usage Examples and Documentation
 * 
 * This file provides examples demonstrating how to use Material Design 3 Expressive
 * components in the Voxly app. MD3 Expressive is characterized by:
 * 
 * 1. **Expressive Shapes**: Larger corner radii (extraLarge = 28dp) for a playful,
 *    friendly appearance. The shape scale uses: 4dp, 8dp, 12dp, 16dp, 28dp.
 * 
 * 2. **Tonal Surface System**: Instead of elevation-based surfaces, MD3 Expressive
 *    uses tonal surface containers that provide visual separation through color
 *    rather than shadows:
 *    - surfaceContainerLowest (lightest)
 *    - surfaceContainerLow
 *    - surfaceContainer (baseline)
 *    - surfaceContainerHigh
 *    - surfaceContainerHighest (darkest in light theme)
 * 
 * 3. **Color Mapping**: Expressive color schemes use the source color's hue but
 *    with different tones, creating a cohesive yet vibrant palette.
 * 
 * For more information, see:
 * - https://m3.material.io/styles/shape
 * - https://m3.material.io/components/button-groups
 * - https://m3.material.io/components/toggle-buttons
 */

// ============================================================================
// BUTTON GROUP EXAMPLES
// ============================================================================

/**
 * Example demonstrating ButtonGroup (formerly SegmentedButton) usage.
 * 
 * Button Groups organize related buttons and provide selection interaction.
 * They come in two types:
 * - **Standard**: Buttons with spacing between them
 * - **Connected**: Buttons that share borders, creating a unified control
 * 
 * Supported selection modes:
 * - Single-select: Only one option can be selected
 * - Multi-select: Multiple options can be selected
 * - Selection-required: At least one option must be selected
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonGroupExamples() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Button Group Examples",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // -------------------------------------------------------------------------
        // Example 1: Single-Select Button Group (Filter Chips as alternative)
        // -------------------------------------------------------------------------
        Text(
            text = "Single-Select Filter Chips",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Use FilterChip for single-selection groups with labels",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        var selectedIndex by remember { mutableIntStateOf(0) }
        val options = listOf("All", "Music", "Podcasts", "Audiobooks")
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    label = { Text(label) },
                    leadingIcon = if (selectedIndex == index) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // -------------------------------------------------------------------------
        // Example 2: Multi-Select Button Group
        // -------------------------------------------------------------------------
        Text(
            text = "Multi-Select Filter Chips",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Use multiple FilterChips for multi-selection groups",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        val selectedGenres = remember { mutableStateListOf("Rock") }
        val genres = listOf("Rock", "Pop", "Jazz", "Classical", "Electronic")
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                FilterChip(
                    selected = selectedGenres.contains(genre),
                    onClick = {
                        if (selectedGenres.contains(genre)) {
                            selectedGenres.remove(genre)
                        } else {
                            selectedGenres.add(genre)
                        }
                    },
                    label = { Text(genre) },
                    leadingIcon = if (selectedGenres.contains(genre)) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
        
        Text(
            text = "Selected: ${selectedGenres.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ============================================================================
// TOGGLE BUTTON EXAMPLES
// ============================================================================

/**
 * Example demonstrating IconToggleButton usage.
 * 
 * IconToggleButtons are used for binary on/off states or exclusive selections.
 * They support:
 * - Icon-only toggle buttons
 * - Text and icon toggle buttons
 * - Custom styling with Material 3 colors
 */
@Composable
fun IconToggleButtonExamples() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Toggle Button Examples",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // -------------------------------------------------------------------------
        // Example 1: Basic Toggle Button
        // -------------------------------------------------------------------------
        Text(
            text = "Basic Toggle Button",
            style = MaterialTheme.typography.titleMedium
        )
        
        var isFavorite by remember { mutableStateOf(false) }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var isFavorite by remember { mutableStateOf(false) }
            
            IconToggleButton(
                checked = isFavorite,
                onCheckedChange = { isFavorite = it }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.Favorite,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isFavorite) "Favorited" else "Not favorited",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // -------------------------------------------------------------------------
        // Example 2: Toggle Button Group (Exclusive Selection)
        // -------------------------------------------------------------------------
        Text(
            text = "Toggle Button Group - Text Alignment",
            style = MaterialTheme.typography.titleMedium
        )
        
        var alignment by remember { mutableStateOf("left") }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconToggleButton(
                checked = alignment == "left",
                onCheckedChange = { alignment = "left" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = "Align left"
                )
            }
            IconToggleButton(
                checked = alignment == "center",
                onCheckedChange = { alignment = "center" }
            ) {
                Icon(
                    imageVector = Icons.Default.FormatAlignCenter,
                    contentDescription = "Align center"
                )
            }
            IconToggleButton(
                checked = alignment == "right",
                onCheckedChange = { alignment = "right" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                    contentDescription = "Align right"
                )
            }
        }
        
        Text(
            text = "Current alignment: $alignment",
            style = MaterialTheme.typography.bodyMedium
        )
        
        // -------------------------------------------------------------------------
        // Example 3: Icon Toggle Buttons with Labels
        // -------------------------------------------------------------------------
        Text(
            text = "Icon Toggle with Custom Styling",
            style = MaterialTheme.typography.titleMedium
        )
        
        var isEnabled by remember { mutableStateOf(true) }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var isEnabled by remember { mutableStateOf(true) }
            
            IconToggleButton(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it }
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isEnabled) "Enabled" else "Disabled")
            }
        }
    }
}

// ============================================================================
// SURFACE CONTAINER COLOR EXAMPLES
// ============================================================================

/**
 * Example demonstrating Surface Container color usage.
 * 
 * MD3 Expressive uses a tonal surface system instead of elevation-based surfaces.
 * The surface container colors provide visual hierarchy through color differences
 * rather than shadows:
 * 
 * Light Theme:
 * - surfaceContainerLowest: #FFFFFF (lightest)
 * - surfaceContainerLow: #F7F2F8
 * - surfaceContainer: #F3EDF7 (baseline)
 * - surfaceContainerHigh: #EDE6F2
 * - surfaceContainerHighest: #E8E2EC (darkest)
 * 
 * Dark Theme:
 * - surfaceContainerLowest: #0F0D13 (darkest)
 * - surfaceContainerLow: #1D1B20
 * - surfaceContainer: #211F26 (baseline)
 * - surfaceContainerHigh: #2B292F
 * - surfaceContainerHighest: #363339 (lightest)
 * 
 * Usage Guidelines:
 * - Use surfaceContainer for main content areas
 * - Use surfaceContainerLow/surfaceContainerLowest for elevated cards
 * - Use surfaceContainerHigh/surfaceContainerHighest for emphasized areas
 */
@Composable
fun SurfaceContainerExamples() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Surface Container Color Examples",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = "MD3 Expressive uses tonal surfaces instead of elevation shadows",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // -------------------------------------------------------------------------
        // Example 1: Surface Container Levels
        // -------------------------------------------------------------------------
        Text(
            text = "Surface Container Levels",
            style = MaterialTheme.typography.titleMedium
        )
        
        SurfaceContainerDemo()
        
        // -------------------------------------------------------------------------
        // Example 2: Card with Surface Container Background
        // -------------------------------------------------------------------------
        Text(
            text = "Card with Surface Container Background",
            style = MaterialTheme.typography.titleMedium
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Surface Container Low",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Use this for cards that need subtle elevation",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // -------------------------------------------------------------------------
        // Example 3: Content Sections with Different Container Levels
        // -------------------------------------------------------------------------
        Text(
            text = "Content Sections",
            style = MaterialTheme.typography.titleMedium
        )
        
        // Main content area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Surface Container (main content)",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // Highlighted section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Surface Container High (emphasized)",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // -------------------------------------------------------------------------
        // Example 4: Surface Container in List Items
        // -------------------------------------------------------------------------
        Text(
            text = "List Item Containers",
            style = MaterialTheme.typography.titleMedium
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "List Item - Lowest",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "List Item - Default",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "List Item - Highest (selected)",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Demo component showing all surface container levels.
 */
@Composable
private fun SurfaceContainerDemo() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val containers = listOf(
            "surfaceContainerLowest" to MaterialTheme.colorScheme.surfaceContainerLowest,
            "surfaceContainerLow" to MaterialTheme.colorScheme.surfaceContainerLow,
            "surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
            "surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
            "surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest
        )
        
        containers.forEach { (name, color) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = color,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (name.contains("Highest")) "← More emphasis" 
                               else if (name.contains("Lowest")) "Less emphasis →" 
                               else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// COMPLETE EXAMPLE: COMBINED USAGE
// ============================================================================

/**
 * Complete example showing MD3 Expressive components used together.
 * 
 * This example demonstrates:
 * 1. Filter chips for selection
 * 2. Toggle buttons for binary states
 * 3. Surface containers for visual hierarchy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteMD3ExpressiveExample() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Complete MD3 Expressive Example",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // -------------------------------------------------------------------------
        // Settings-like UI using Expressive components
        // -------------------------------------------------------------------------
        
        // Section 1: Filter chips for category selection
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium
        )
        
        var selectedCategory by remember { mutableStateOf("All") }
        val categories = listOf("All", "Songs", "Albums", "Artists")
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    leadingIcon = if (selectedCategory == category) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
        
        // Section 2: Toggle buttons for options
        Text(
            text = "Display Options",
            style = MaterialTheme.typography.titleMedium
        )
        
        var showArtwork by remember { mutableStateOf(true) }
        var showDuration by remember { mutableStateOf(true) }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Album Artwork",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconToggleButton(
                        checked = showArtwork,
                        onCheckedChange = { showArtwork = it }
                    ) {
                        Icon(
                            imageVector = if (showArtwork) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Duration",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconToggleButton(
                        checked = showDuration,
                        onCheckedChange = { showDuration = it }
                    ) {
                        Icon(
                            imageVector = if (showDuration) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                }
            }
        }
        
        // Section 3: Action buttons with expressive styling
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { /* Apply filters */ },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Apply")
            }
            
            TextButton(
                onClick = { /* Reset filters */ },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
        }
    }
}

// ============================================================================
// SUMMARY: KEY TAKEAWAYS
// ============================================================================

/**
 * Summary of MD3 Expressive Design Principles
 * 
 * 1. SHAPES
 *    - Use larger corner radii: extraLarge = 28dp
 *    - Shapes: 4dp, 8dp, 12dp, 16dp, 28dp
 *    - Creates a playful, friendly appearance
 * 
 * 2. SURFACES
 *    - Use tonal surface containers instead of elevation
 *    - surfaceContainer for main content
 *    - surfaceContainerLow/Lowest for cards
 *    - surfaceContainerHigh/Highest for emphasis
 * 
 * 3. SELECTION CONTROLS
 *    - FilterChip for single/multi-select groups
 *    - IconToggleButton for binary states
 *    - Button groups for related actions
 * 
 * 4. COLOR
 *    - Expressive schemes use source color hue with varied tones
 *    - Primary container colors are more vibrant
 *    - Better contrast for accessibility
 * 
 * See:
 * - com.voxly.presentation.theme.Shapes
 * - com.voxly.presentation.theme.ColorSchemes
 */
@Composable
fun MD3ExpressiveSummary() {
    // This is a documentation composable - no UI to display
    // See the summary above for key points
}
