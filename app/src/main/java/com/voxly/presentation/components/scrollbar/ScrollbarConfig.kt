package com.voxly.presentation.components.scrollbar

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for Material 3 Expressive scrollbar appearance and behavior.
 *
 * M3E-aligned defaults:
 * - 48dp touch area (M3 accessibility minimum)
 * - 12dp drag width for easy visual feedback
 * - 2000ms hide delay for comfortable reading
 * - Spring stiffness following M3E Fast/L1 schemes
 *
 * Stiffness values are based on Material 3 Expressive Motion specifications:
 * - FastSpatial: stiffness = 1400 (for resizing/dimensions)
 * - FastEffects: stiffness = 3800 (for color/opacity)
 *
 * @property thumbWidth Width of the scrollbar thumb in default state
 * @property thumbWidthDragging Width of the thumb when being dragged
 * @property thumbHeight Base height of the scrollbar thumb
 * @property minThumbHeight Minimum thumb height to ensure it's always tappable
 * @property touchAreaWidth Width of the touch area (M3 recommends 48dp min)
 * @property thumbCornerRadius Corner radius for scrollbar track
 * @property bubbleSize Size of the preview bubble
 * @property bubbleCornerRadius Corner radius of the preview bubble
 * @property hideDelayMillis Delay before hiding scrollbar after scroll stops
 * @property trackAlpha Alpha value for the scrollbar track in normal state
 * @property trackAlphaDragging Alpha value for the scrollbar track when dragging
 * @property thumbElevation Elevation for thumb shadow
 * @property bubbleElevation Elevation for bubble shadow
 * @property thumbStiffness Spring stiffness for thumb width animation (FastSpatial: 1400)
 * @property visualFeedbackStiffness Spring stiffness for visual feedback (FastEffects: 3800)
 */
data class ScrollbarConfig(
    val thumbWidth: Dp = 8.dp,
    val thumbWidthDragging: Dp = 8.dp,
    val thumbHeight: Dp = 48.dp,
    val minThumbHeight: Dp = 48.dp,
    val touchAreaWidth: Dp = 48.dp,
    val thumbCornerRadius: Dp = 2.dp,
    val bubbleSize: Dp = 56.dp,
    val bubbleCornerRadius: Dp = 28.dp,
    val hideDelayMillis: Long = 2000L,
    val trackAlpha: Float = 1f,
    val trackAlphaDragging: Float = 1f,
    val thumbElevation: Dp = 2.dp,
    val bubbleElevation: Dp = 6.dp,
    val thumbStiffness: Float = 1400f, // FastSpatial stiffness from M3E spec
    val visualFeedbackStiffness: Float = 3800f // FastEffects stiffness from M3E spec
) {
    companion object {
        /** Default M3 Expressive configuration */
        val Default = ScrollbarConfig()

        /** Compact config with smaller touch area for dense UIs */
        val Compact = ScrollbarConfig(
            touchAreaWidth = 24.dp,
            thumbWidth = 8.dp,
            thumbWidthDragging = 8.dp,
            bubbleSize = 48.dp,
            hideDelayMillis = 1200L
        )
    }
}
