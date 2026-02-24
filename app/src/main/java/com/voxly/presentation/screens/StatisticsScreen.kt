package com.voxly.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.components.ExpressiveCard
import com.voxly.presentation.components.ExpressiveContentContainer
import com.voxly.presentation.components.ExpressiveScaffoldWithTopBar
import com.voxly.presentation.theme.ContainerLevel
import com.voxly.presentation.viewmodel.StatisticsUiState
import com.voxly.presentation.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    ExpressiveScaffoldWithTopBar(
        title = stringResource(R.string.statistics_title),
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    painter = appIconPainter(AppIcon.Settings),
                    contentDescription = stringResource(R.string.nav_settings)
                )
            }
        }
    ) {
        ExpressiveContentContainer(
            modifier = Modifier.fillMaxSize(),
            containerLevel = ContainerLevel.Medium
        ) {
            when (val state = uiState) {
            is StatisticsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is StatisticsUiState.Empty -> {
                EmptyStatisticsContent()
            }

            is StatisticsUiState.Success -> {
                StatisticsContent(
                    state = state
                )
            }
        }
        }
    }
}

@Composable
private fun StatisticsContent(
    state: StatisticsUiState.Success,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.statistics_overview),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.total_files),
                    value = state.totalFiles.toString(),
                    icon = AppIcon.MusicNote,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.total_duration),
                    value = state.totalDurationFormatted,
                    icon = AppIcon.Schedule,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.total_size),
                    value = state.totalSizeFormatted,
                    icon = AppIcon.Folder,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.edited_files),
                    value = state.editedFilesCount.toString(),
                    icon = AppIcon.Edit,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.formatDistribution.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.statistics_format_distribution),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                FormatDistributionCard(
                    distribution = state.formatDistribution,
                    totalFiles = state.totalFiles
                )
            }
        }

        if (state.topArtists.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.statistics_top_artists),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { TopArtistsCard(artists = state.topArtists) }
        }

        if (state.topAlbums.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.statistics_top_albums),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { TopAlbumsCard(albums = state.topAlbums) }
        }

        item {
            Text(
                text = stringResource(R.string.statistics_recent_activity),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            RecentActivityCard(
                todayEdits = state.todayEdits,
                weekEdits = state.weekEdits,
                monthEdits = state.monthEdits
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: AppIcon,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier,
        containerLevel = ContainerLevel.Low
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = appIconPainter(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FormatDistributionCard(
    distribution: Map<String, Int>,
    totalFiles: Int
) {
    ExpressiveCard(
        containerLevel = ContainerLevel.Low
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            distribution.entries.sortedByDescending { it.value }.forEach { (format, count) ->
                val pct = if (totalFiles > 0) (count.toFloat() / totalFiles) else 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = format)
                    Text(
                        text = "$count (${String.format("%.1f", pct * 100)}%)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun TopArtistsCard(artists: List<Pair<String, Int>>) {
    ExpressiveCard(
        containerLevel = ContainerLevel.Low
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            artists.take(5).forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}.",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(text = item.first, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(text = item.second.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TopAlbumsCard(albums: List<Pair<String, Int>>) {
    ExpressiveCard(
        containerLevel = ContainerLevel.Low
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            albums.take(5).forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}.",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(text = item.first, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(text = item.second.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentActivityCard(todayEdits: Int, weekEdits: Int, monthEdits: Int) {
    ExpressiveCard(
        containerLevel = ContainerLevel.Low
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActivityStatItem(label = stringResource(R.string.today), value = todayEdits)
            ActivityStatItem(label = stringResource(R.string.this_week), value = weekEdits)
            ActivityStatItem(label = stringResource(R.string.this_month), value = monthEdits)
        }
    }
}

@Composable
private fun ActivityStatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStatisticsContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                painter = appIconPainter(AppIcon.BarChart),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.statistics_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.statistics_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
