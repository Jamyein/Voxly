package com.voxly.data.local.cache

import android.content.Context
import androidx.room.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room Database for music library cache.
 * Provides fast access to cached audio files and album thumbnails.
 */
@Database(
    entities = [
        CachedAudioFileEntity::class,
        AlbumThumbnailEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MusicCacheDatabase : RoomDatabase() {
    abstract fun audioFileDao(): CachedAudioFileDao
    abstract fun albumThumbnailDao(): AlbumThumbnailDao

    companion object {
        const val DATABASE_NAME = "music_cache.db"
    }
}

/**
 * Type converters for complex data types.
 * Uses JSONObject instead of Gson to avoid TypeToken issues with obfuscation.
 */
class RoomTypeConverters {

    // Map<String, String> for customFields
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return value?.let {
            JSONObject(it).toString()
        }
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let {
            try {
                val json = JSONObject(it)
                val map = mutableMapOf<String, String>()
                json.keys().forEach { key ->
                    map[key] = json.getString(key)
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}

/**
 * Database provider using Hilt dependency injection.
 */
@Singleton
class MusicCacheDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var instance: MusicCacheDatabase? = null
    
    fun getDatabase(): MusicCacheDatabase {
        return instance ?: synchronized(this) {
            val newInstance = Room.databaseBuilder(
                context.applicationContext,
                MusicCacheDatabase::class.java,
                MusicCacheDatabase.DATABASE_NAME
            )
                // Enable WAL mode for better concurrent performance
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Fallback to destructive migration during development
                .fallbackToDestructiveMigration()
                .build()
            instance = newInstance
            newInstance
        }
    }
}
