package com.voxly.presentation.components.lyricsposter

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Handles sharing of lyrics poster images with preview support.
 */
object LyricsPosterShare {

    private const val POSTER_FILE_NAME = "lyrics_poster.png"
    private const val POSTER_FOLDER = "lyrics_posters"

    /**
     * Shares a lyrics poster bitmap to other apps with image preview.
     *
     * @param context Android context
     * @param bitmap The poster bitmap to share
     * @param title Song title for naming the share
     */
    fun sharePoster(context: Context, bitmap: Bitmap, title: String) {
        // Save full resolution image
        val posterFile = saveBitmapToCache(context, bitmap, title, POSTER_FILE_NAME)
        val posterUri = getUriForFile(context, posterFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"

            // Set the main image to share
            putExtra(Intent.EXTRA_STREAM, posterUri)

            // Add subject and text (optional)
            putExtra(Intent.EXTRA_SUBJECT, "Lyrics Poster - $title")

            // Use ClipData for proper image sharing with preview
            clipData = ClipData.newUri(
                context.contentResolver,
                "Lyrics Poster",
                posterUri
            )

            // Grant read permission for the URI
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Use createChooser with the poster URI to show preview
        val chooserIntent = Intent.createChooser(shareIntent, "Share Lyrics Poster")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(chooserIntent)
    }

    /**
     * Saves bitmap to cache directory.
     */
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, title: String, fileName: String): File {
        val cacheDir = File(context.cacheDir, POSTER_FOLDER)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_").take(30)
        val finalFileName = "${sanitizedTitle}_$fileName"
        val file = File(cacheDir, finalFileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file
    }
    
    /**
     * Gets a content URI for the file using FileProvider.
     */
    private fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
