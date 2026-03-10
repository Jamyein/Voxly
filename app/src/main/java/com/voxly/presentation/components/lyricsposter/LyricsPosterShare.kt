package com.voxly.presentation.components.lyricsposter

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handles sharing of lyrics poster images.
 */
object LyricsPosterShare {

    private const val POSTER_FILE_NAME = "lyrics_poster.png"
    private const val POSTER_FOLDER = "lyrics_posters"

    /**
     * Shares a lyrics poster bitmap to other apps.
     *
     * @param context Android context
     * @param bitmap The poster bitmap to share
     * @param title Song title for naming the share
     */
    fun sharePoster(context: Context, bitmap: Bitmap, title: String) {
        val file = saveBitmapToCache(context, bitmap, title)
        val uri = getUriForFile(context, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Lyrics Poster - $title")
            putExtra(Intent.EXTRA_TEXT, "Check out the lyrics for $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Lyrics Poster")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /**
     * Saves bitmap to cache directory.
     */
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, title: String): File {
        val cacheDir = File(context.cacheDir, POSTER_FOLDER)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
        val fileName = "${sanitizedTitle}_$POSTER_FILE_NAME"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file
    }

    /**
     * Gets a content URI for the file using FileProvider.
     */
    private fun getUriForFile(context: Context, file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
