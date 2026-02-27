package com.voxly.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Position of a card within a list of settings items.
 * Used to determine corner radius for visual grouping.
 */
enum class CardPosition {
    /** First item in the list - large top corners, small bottom corners */
    FIRST,
    /** Last item in the list - small top corners, large bottom corners */
    LAST,
    /** Middle item in the list - all corners are small */
    MIDDLE,
    /** Single item in the list - all corners are large */
    SINGLE
}

/**
 * Settings item card with position-aware corner radius.
 *
 * - FIRST: top-left/top-right = extraLarge, bottom-left/bottom-right = 4dp
 * - LAST: top-left/top-right = 4dp, bottom-left/bottom-right = extraLarge
 * - MIDDLE: all corners = 4dp
 * - SINGLE: all corners = extraLarge
 *
 * @param position The position of this card within the settings section
 * @param modifier Modifier for the card
 * @param content Content to display inside the card
 */
@Composable
fun SettingsItemCard(
    position: CardPosition,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = when (position) {
        CardPosition.FIRST -> RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraLarge.topStart,
            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
            bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
            bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd
        )
        CardPosition.LAST -> RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraSmall.topStart,
            topEnd = MaterialTheme.shapes.extraSmall.topEnd,
            bottomStart = MaterialTheme.shapes.extraLarge.bottomStart,
            bottomEnd = MaterialTheme.shapes.extraLarge.bottomEnd
        )
        CardPosition.MIDDLE -> MaterialTheme.shapes.extraSmall
        CardPosition.SINGLE -> MaterialTheme.shapes.extraLarge
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
    ) {
        content()
    }
}
