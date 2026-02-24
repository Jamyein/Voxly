package com.voxly.data.local.cache

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
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
 */
class RoomTypeConverters {
    private val gson = Gson()
    
    // Map<String, String> for customFields
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return value?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let {
            val type = object : TypeToken<Map<String, String>>() {}.type
            try {
                gson.fromJson(it, type)
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
