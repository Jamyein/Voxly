package com.voxly.data.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.voxly.data.local.metadata.JaudiotaggerMetadataProcessor
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for scanning and accessing audio files from device storage.
 * Uses Android's MediaStore API for efficient file discovery.
 */
@Singleton
class AudioFileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: JaudiotaggerMetadataProcessor
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "AudioFileScanner"
        private val AUDIO_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

        private val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE
        )
    }

    /**
     * Scans all audio files from external storage.
     * @return Flow emitting lists of audio files as they're discovered
     */
    fun scanAllAudioFiles(): Flow<List<AudioFile>> = flow {
        val audioFiles = mutableListOf<AudioFile>()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = contentResolver.query(
            AUDIO_URI,
            PROJECTION,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val yearColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val bitrateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)

            while (it.moveToNext()) {
                val filePath = it.getString(dataColumn)
                val extension = filePath.substringAfterLast('.', "")

                // Only process supported audio formats
                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    // Read full metadata including lyrics from file tags
                    val fullMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                        ?: parseBasicMetadata(
                            title = it.getString(titleColumn),
                            artist = it.getString(artistColumn),
                            album = it.getString(albumColumn),
                            year = it.getString(yearColumn),
                            albumId = it.getLong(albumIdColumn)
                        )

                    val audioFile = AudioFile(
                        id = it.getLong(idColumn).toString(),
                        path = filePath,
                        name = it.getString(nameColumn) ?: filePath.substringAfterLast('/'),
                        size = it.getLong(sizeColumn),
                        duration = it.getLong(durationColumn),
                        format = extension.uppercase(),
                        bitrate = it.getInt(bitrateColumn),
                        sampleRate = 0,
                        channels = 0,
                        mediaStoreAlbumId = it.getLong(albumIdColumn).takeIf { albumId -> albumId > 0L },
                        metadata = fullMetadata
                    )
                    audioFiles.add(audioFile)
                }
            }
        }

        emit(audioFiles)
    }.flowOn(Dispatchers.IO)

    /**
     * Scans audio files within a specific directory.
     * @param directoryPath The directory path to scan
     * @return Flow emitting lists of audio files found
     */
    fun scanDirectory(directoryPath: String): Flow<List<AudioFile>> = flow {
        val audioFiles = mutableListOf<AudioFile>()
        val directory = File(directoryPath)

        if (directory.exists() && directory.isDirectory) {
            scanDirectoryRecursive(directory, audioFiles)
        }

        emit(audioFiles)
    }.flowOn(Dispatchers.IO)

    /**
     * Gets lyrics for a specific audio file.
     * @param filePath Path to the audio file
     * @return Lyrics string or null if not found
     */
    suspend fun getLyrics(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            metadataProcessor.readMetadata(filePath, includeAlbumArt = false)?.lyrics
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read lyrics from: $filePath", e)
            null
        }
    }

    /**
     * Recursively scans a directory for audio files.
     * Now reads full metadata including lyrics from file tags.
     */
    private suspend fun scanDirectoryRecursive(directory: File, audioFiles: MutableList<AudioFile>) {
        val audioExtensions = setOf("mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "ape", "opus")

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectoryRecursive(file, audioFiles)
            } else {
                val extension = file.name.substringAfterLast('.').lowercase()
                if (extension in audioExtensions && file.canRead()) {
                    val audioFile = createAudioFileFromPath(file.absolutePath)
                    audioFiles.add(audioFile)
                }
            }
        }
    }

    /**
     * Creates an AudioFile from a file path without querying MediaStore.
     * Reads full metadata including lyrics from file tags.
     * Used for files not in MediaStore database.
     */
    private suspend fun createAudioFileFromPath(filePath: String): AudioFile {
        val file = File(filePath)
        val extension = file.name.substringAfterLast('.').lowercase()

        // Read full metadata including lyrics from file tags
        val fullMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
            ?: com.voxly.domain.model.AudioMetadata()

        return AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = 0L,
            format = extension.uppercase(),
            bitrate = 0,
            sampleRate = 0,
            channels = 0,
            metadata = fullMetadata
        )
    }

    /**
     * Parses basic metadata from MediaStore cursor data.
     */
    private fun parseBasicMetadata(
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        albumId: Long
    ): com.voxly.domain.model.AudioMetadata {
        return com.voxly.domain.model.AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            // Album art URI will be resolved lazily
            albumArt = null
        )
    }

    /**
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
    }

    /**
     * Checks if a file exists and is readable.
     */
    suspend fun isFileAccessible(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.exists() && file.canRead()
        } catch (e: SecurityException) {
            false
        }
    }
}
