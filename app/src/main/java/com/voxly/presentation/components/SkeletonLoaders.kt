package com.voxly.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.toShape
import com.voxly.presentation.theme.MaterialShapes

/**
 * Material Design 3 Skeleton Loader components.
 *
 * Uses shimmer animation to indicate content loading state.
 * Follows M3 Expressive spacing grid (4dp base).
 */

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    (-200f at 0) using LinearEasing
                    (400f at 1200) using LinearEasing
                },
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(surfaceColor, highlightColor, surfaceColor),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + 200f, 0f)
                )
            )
    )
}

/**
 * Skeleton list item matching AudioFileStandardRow layout.
 * Displays a shimmer placeholder for a file list item.
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    showTrailingAction: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art placeholder
        ShimmerBox(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text placeholders
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Subtitle (artist/album)
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Meta info (format/duration/size)
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
            )
        }

        if (showTrailingAction) {
            Spacer(modifier = Modifier.width(8.dp))
            // Action icon placeholder
            ShimmerBox(
                modifier = Modifier.size(24.dp),
                shape = CircleShape
            )
        }
    }
}

/**
 * Skeleton screen for list views (FileBrowser, Albums, Artists).
 */
@Composable
fun SkeletonListScreen(
    itemCount: Int = 8,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(count = itemCount, key = { it }) {
            SkeletonListItem()
        }
    }
}

/**
 * Skeleton detail screen for Album/Artist detail pages.
 */
@Composable
fun SkeletonDetailScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Large cover art placeholder
        ShimmerBox(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.large)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(24.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(16.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Song list items
        repeat(6) {
            SkeletonListItem()
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Skeleton screen for Metadata Editor.
 */
@Composable
fun SkeletonMetadataEditor(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cover art placeholder
        ShimmerBox(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.CenterHorizontally)
                .clip(MaterialTheme.shapes.large)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Form fields
        repeat(5) { index ->
            // Label
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(12.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Input field
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            if (index < 4) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
