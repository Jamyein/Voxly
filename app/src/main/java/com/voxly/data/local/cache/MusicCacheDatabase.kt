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
        CachedAudioFileFts::class,
        AlbumThumbnailEntity::class,
        ArtistLinkEntity::class,
        RecentEditEntity::class,
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MusicCacheDatabase : RoomDatabase() {
    abstract fun audioFileDao(): CachedAudioFileDao
    abstract fun albumThumbnailDao(): AlbumThumbnailDao
    abstract fun artistLinkDao(): ArtistLinkDao
    abstract fun albumSummaryDao(): AlbumSummaryDao
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
                // Migration from version 3 to 5: skip v4 (album_art_file_cache table was removed)
                .addMigrations(object : Migration(3, 5) {
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
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_album_info_name_artist` ON `album_info` (`albumName`, `albumArtist`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_info_year` ON `album_info` (`year`)")
                    }
                })
                // Migration from version 5 to 6: adds artistId, mimeType, dateAdded columns to cached_audio_files
                .addMigrations(object : Migration(5, 6) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE `cached_audio_files` ADD COLUMN `mimeType` TEXT")
                        db.execSQL("ALTER TABLE `cached_audio_files` ADD COLUMN `artistId` INTEGER")
                        db.execSQL("ALTER TABLE `cached_audio_files` ADD COLUMN `dateAdded` INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_audio_files_artistId` ON `cached_audio_files` (`artistId`)")
                    }
                })
                // Migration from version 6 to 7: adds album_sort_order table for cached sort orders
                .addMigrations(object : Migration(6, 7) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `album_sort_order` (
                                `sortOption` TEXT PRIMARY KEY NOT NULL,
                                `albumIds` TEXT NOT NULL,
                                `contentHash` TEXT NOT NULL,
                                `lastUpdatedAt` INTEGER NOT NULL
                            )
                        """)
                    }
                })
                // Migration from version 7 to 8: adds artist_links table and album_summary_view
                .addMigrations(object : Migration(7, 8) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create artist_links table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `artist_links` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `trackId` TEXT NOT NULL,
                                `artistName` TEXT NOT NULL
                            )
                        """)
                        // Create indices for artist_links
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_artist_links_artistName` ON `artist_links` (`artistName`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_artist_links_trackId` ON `artist_links` (`trackId`)")
                        
                        // Create album_summary_view
                        db.execSQL("""
                            CREATE VIEW IF NOT EXISTS `album_summary_view` AS
                            SELECT 
                                SUBSTR(MD5(album_artist || album), 1, 16) AS albumKey,
                                album AS albumTitle,
                                album_artist AS albumArtist,
                                COUNT(*) AS songCount,
                                MAX(year) AS year,
                                MAX(sample_rate) AS maxSampleRate,
                                MAX(id) AS coverId
                            FROM cached_audio_files
                            WHERE album IS NOT NULL AND album != ''
                            GROUP BY album_artist, album
                        """)
                    }
                })
                // Migration from version 9 to 10: adds FTS4 table for full-text search
                .addMigrations(object : Migration(9, 10) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create FTS4 virtual table for full-text search on title, artist, album
                        db.execSQL("""
                            CREATE VIRTUAL TABLE IF NOT EXISTS `cached_audio_files_fts` 
                            USING fts4(content='cached_audio_files', title, artist, album)
                        """)
                        // Populate FTS table with existing data
                        db.execSQL("""
                            INSERT INTO cached_audio_files_fts(rowid, title, artist, album)
                            SELECT rowid, title, artist, album FROM cached_audio_files
                        """)
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
        private const val CURRENT_DATA_FORMAT_VERSION = 10  // FTS4 full-text search
    }
}
