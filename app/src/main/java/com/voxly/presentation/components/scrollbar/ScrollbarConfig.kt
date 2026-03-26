package com.voxly.presentation.components.scrollbar

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp

/**
 * Configuration for Material 3 Expressive scrollbar appearance and behavior.
 *
 * Enhanced for better responsiveness and visual appeal.
 *
 * @property thumbWidth Width of the scrollbar thumb in default state
 * @property thumbWidthDragging Width of the thumb when being dragged (wider for better visibility)
 * @property thumbHeight Base height of the scrollbar thumb
 * @property minThumbHeight Minimum thumb height to ensure it's always tappable
 * @property touchAreaWidth Width of the touch area for easier interaction
 * @property thumbCornerRadius Corner radius for rounded scrollbar thumb
 * @property bubbleSize Size of the preview bubble
 * @property bubbleCornerRadius Corner radius of the preview bubble
 * @property hideDelayMillis Delay before hiding scrollbar after scroll stops
 * @property trackAlpha Alpha value for the scrollbar track in normal state
 * @property trackAlphaDragging Alpha value for the scrollbar track when dragging
 * @property thumbElevation Elevation for thumb shadow
 * @property bubbleElevation Elevation for bubble shadow
 * @property thumbStiffness Spring stiffness for thumb width animation (higher = snappier)
 * @property visualFeedbackStiffness Spring stiffness for visual feedback (track, bubble)
 */
data class ScrollbarConfig(
    val thumbWidth: androidx.compose.ui.unit.Dp = 6.dp,
    val thumbWidthDragging: androidx.compose.ui.unit.Dp = 10.dp,
    val thumbHeight: androidx.compose.ui.unit.Dp = 48.dp,
    val minThumbHeight: androidx.compose.ui.unit.Dp = 32.dp,
    val touchAreaWidth: androidx.compose.ui.unit.Dp = 24.dp,
    val thumbCornerRadius: androidx.compose.ui.unit.Dp = 3.dp,
    val bubbleSize: androidx.compose.ui.unit.Dp = 56.dp,
    val bubbleCornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    val hideDelayMillis: Long = 1200L,
    val trackAlpha: Float = 0.12f,
    val trackAlphaDragging: Float = 0.25f,
    val thumbElevation: androidx.compose.ui.unit.Dp = 2.dp,
    val bubbleElevation: androidx.compose.ui.unit.Dp = 6.dp,
    val thumbStiffness: Float = Spring.StiffnessHigh,
    val visualFeedbackStiffness: Float = Spring.StiffnessMedium
) {
    companion object {
        /** Default configuration following Material 3 Expressive guidelines */
        val Default = ScrollbarConfig()

        /** High responsiveness config for better touch feedback */
        val Responsive = ScrollbarConfig(
            thumbStiffness = Spring.StiffnessHigh * 1.5f,
            visualFeedbackStiffness = Spring.StiffnessHigh
        )
    }
}
