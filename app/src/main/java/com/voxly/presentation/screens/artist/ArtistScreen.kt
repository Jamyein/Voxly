package com.voxly.presentation.screens.artist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.voxly.R
import com.voxly.presentation.screens.filebrowser.ArtistTabContent
import com.voxly.presentation.viewmodel.ArtistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToArtistDetail: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val artists by viewModel.artists.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberLazyListState()

    val readPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPermission = granted
    }

    LaunchedEffect(hasReadPermission, lifecycleOwner) {
        if (!hasReadPermission) {
            readPermissionLauncher.launch(readPermission)
            return@LaunchedEffect
        }
        viewModel.refresh()
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.tab_artists)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                )
        ) {
            if (artists.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_artists_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ArtistTabContent(
                    artists = artists,
                    onArtistClick = { artist ->
                        onNavigateToArtistDetail(artist.name)
                    },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    listState = listState
                )
            }
        }
    }
}