package com.voxly.presentation.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

enum class AppIcon(
    val vector: ImageVector
) {
    Folder(Icons.Filled.Folder),
    FolderOutlined(Icons.Outlined.Folder),
    History(Icons.Filled.History),
    HistoryOutlined(Icons.Outlined.History),
    PlaylistAdd(Icons.Filled.PlaylistAdd),
    PlaylistAddOutlined(Icons.Outlined.PlaylistAdd),
    AudioFile(Icons.Filled.MusicNote),
    FolderOpen(Icons.Filled.FolderOpen),
    Equalizer(Icons.Filled.GraphicEq),
    Image(Icons.Filled.Image),
    HideImage(Icons.Filled.Image),
    ChevronRight(Icons.Filled.ChevronRight),
    Error(Icons.Filled.Error),
    MusicNote(Icons.Filled.MusicNote),
    Save(Icons.Filled.Save),
    CloudDownload(Icons.Filled.CloudDownload),
    Schedule(Icons.Filled.Schedule)
}

@Composable
fun appIconPainter(icon: AppIcon): Painter = rememberVectorPainter(image = icon.vector)
