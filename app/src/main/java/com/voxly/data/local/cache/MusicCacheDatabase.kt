package com.voxly.data.local.cache

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import androidx.room.migration.Migration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room Database for music library cache.
 * Provides fast access to cached audio files and album thumbnails.
 */
@Database(
    entities = [
        CachedAudioFileEntity::class,
        AlbumThumbnailEntity::class,
        RecentEditEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MusicCacheDatabase : RoomDatabase() {
    abstract fun audioFileDao(): CachedAudioFileDao
    abstract fun albumThumbnailDao(): AlbumThumbnailDao
    abstract fun recentEditDao(): RecentEditDao

    companion object {
        const val DATABASE_NAME = "music_cache.db"
    }
}

/**
 * Type converters for complex data types.
 * Uses Kotlinx Serialization instead of Gson TypeToken to avoid obfuscation issues.
 */
class RoomTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Map<String, String> for customFields
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return value?.let {
            json.encodeToString(it)
        }
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}

/**
 * Database provider using Hilt dependency injection.
 * Uses SharedPreferences to track data format version for smart migration.
 */
@Singleton
class MusicCacheDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var instance: MusicCacheDatabase? = null

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDatabase(): MusicCacheDatabase {
        return instance ?: synchronized(this) {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                MusicCacheDatabase::class.java,
                MusicCacheDatabase.DATABASE_NAME
            )
                // Enable WAL mode for better concurrent performance
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Migration from version 2 to 3: adds year index
                .addMigrations(object : Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create index on year column for faster year-based filtering
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_audio_files_year` ON `cached_audio_files` (`year`)")
                    }
                })

            // Conditional destructive migration: only when data format version is old
            if (prefs.getInt(KEY_DATA_FORMAT_VERSION, 1) < CURRENT_DATA_FORMAT_VERSION) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }

            val newInstance = builder.build()
            instance = newInstance
            newInstance
        }
    }

    /**
     * Get current data format version.
     */
    fun getDataFormatVersion(): Int = prefs.getInt(KEY_DATA_FORMAT_VERSION, 1)

    /**
     * Clear all data from the database and reset data format version.
     */
    suspend fun clearAllData() {
        getDatabase().clearAllTables()
        prefs.edit().putInt(KEY_DATA_FORMAT_VERSION, 1).apply()
    }

    companion object {
        private const val PREFS_NAME = "music_cache_meta"
        private const val KEY_DATA_FORMAT_VERSION = "data_format_version"
        private const val CURRENT_DATA_FORMAT_VERSION = 2
    }
}
