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
        RecentEditEntity::class,
        AlbumArtFileCacheEntity::class,
        AlbumInfoEntity::class  // Added for album year and audio info caching
    ],
    version = 5,  // Bumped from 4 to 5 for AlbumInfoEntity
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MusicCacheDatabase : RoomDatabase() {
    abstract fun audioFileDao(): CachedAudioFileDao
    abstract fun albumThumbnailDao(): AlbumThumbnailDao
    abstract fun recentEditDao(): RecentEditDao
    abstract fun albumArtFileCacheDao(): AlbumArtFileCacheDao
    abstract fun albumInfoDao(): AlbumInfoDao  // New DAO for album info caching

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
            val storedDataFormatVersion = prefs.getInt(KEY_DATA_FORMAT_VERSION, 1)
            if (storedDataFormatVersion < CURRENT_DATA_FORMAT_VERSION) {
                instance?.close()
                instance = null
                context.deleteDatabase(MusicCacheDatabase.DATABASE_NAME)
                prefs.edit().putInt(KEY_DATA_FORMAT_VERSION, CURRENT_DATA_FORMAT_VERSION).apply()
            }

            val builder = Room.databaseBuilder(
                context.applicationContext,
                MusicCacheDatabase::class.java,
                MusicCacheDatabase.DATABASE_NAME
            )
                // Enable WAL mode for better concurrent performance
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Destructive migration for cache database - data can be re-scanned
                .fallbackToDestructiveMigration(dropAllTables = true)
                // Migration from version 2 to 3: adds year index
                .addMigrations(object : Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create index on year column for faster year-based filtering
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_audio_files_year` ON `cached_audio_files` (`year`)")
                    }
                })
                // Migration from version 3 to 4: adds album art file cache table
                .addMigrations(object : Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create album art file cache table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `album_art_file_cache` (
                                `filePath` TEXT PRIMARY KEY NOT NULL,
                                `originalArtBytes` BLOB,
                                `thumbnailBytes` BLOB,
                                `lastModified` INTEGER NOT NULL,
                                `cacheTime` INTEGER NOT NULL DEFAULT 0,
                                `accessCount` INTEGER NOT NULL DEFAULT 0,
                                `lastAccessTime` INTEGER NOT NULL DEFAULT 0
                            )
                        """)
                        // Create indices for LRU queries
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_art_file_cache_access` ON `album_art_file_cache` (`accessCount`, `lastAccessTime`)")
                    }
                })
                // Migration from version 4 to 5: adds album info table for year and audio quality caching
                .addMigrations(object : Migration(4, 5) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create album_info table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `album_info` (
                                `id` TEXT PRIMARY KEY NOT NULL,
                                `albumName` TEXT NOT NULL,
                                `albumArtist` TEXT,
                                `year` TEXT,
                                `yearHash` TEXT NOT NULL,
                                `sampleRate` INTEGER NOT NULL DEFAULT 0,
                                `bitrate` INTEGER NOT NULL DEFAULT 0,
                                `contentHash` TEXT NOT NULL,
                                `songCount` INTEGER NOT NULL DEFAULT 0,
                                `lastUpdatedAt` INTEGER NOT NULL
                            )
                        """)
                        // Create indices for faster queries
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_album_info_name_artist` ON `album_info` (`albumName`, `albumArtist`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_info_year` ON `album_info` (`year`)")
                    }
                })

            val newInstance = builder.build()
            prefs.edit().putInt(KEY_DATA_FORMAT_VERSION, CURRENT_DATA_FORMAT_VERSION).apply()
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
        prefs.edit().putInt(KEY_DATA_FORMAT_VERSION, CURRENT_DATA_FORMAT_VERSION).apply()
    }

    companion object {
        private const val PREFS_NAME = "music_cache_meta"
        private const val KEY_DATA_FORMAT_VERSION = "data_format_version"
        private const val CURRENT_DATA_FORMAT_VERSION = 5  // Updated for AlbumInfoEntity
    }
}
