package com.voxly.presentation.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.voxly.R

enum class AppIcon(
    @DrawableRes val resId: Int
) {
    Folder(R.drawable.ic_folder),
    History(android.R.drawable.ic_menu_recent_history),
    PlaylistAdd(android.R.drawable.ic_input_add),
    AudioFile(android.R.drawable.ic_media_play),
    FolderOpen(R.drawable.ic_folder_open),
    Equalizer(android.R.drawable.ic_media_ff),
    Image(android.R.drawable.ic_menu_gallery),
    HideImage(android.R.drawable.ic_menu_close_clear_cancel),
    ChevronRight(android.R.drawable.ic_media_next),
    Error(android.R.drawable.stat_notify_error),
    MusicNote(android.R.drawable.ic_media_play),
    Save(android.R.drawable.ic_menu_save),
    CloudDownload(android.R.drawable.stat_sys_download),
    Schedule(android.R.drawable.ic_menu_recent_history)
}

@Composable
fun appIconPainter(icon: AppIcon): Painter = painterResource(id = icon.resId)
