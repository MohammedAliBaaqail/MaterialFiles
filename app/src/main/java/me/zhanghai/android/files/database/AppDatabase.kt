package me.zhanghai.android.files.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoMetadata::class,
        VideoThumbnail::class
    ],
    version = 5, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videoMetadataDao(): VideoMetadataDao
    abstract fun videoThumbnailDao(): VideoThumbnailDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add width and height columns
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add width column
                database.execSQL("ALTER TABLE video_metadata ADD COLUMN width INTEGER DEFAULT NULL")
                // Add height column
                database.execSQL("ALTER TABLE video_metadata ADD COLUMN height INTEGER DEFAULT NULL")
            }
        }

        // Migration from version 4 to 5: Add video_thumbnails table
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS video_thumbnails (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        video_path TEXT NOT NULL,
                        thumbnail_path TEXT NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        is_default INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        display_interval_ms INTEGER NOT NULL DEFAULT 5000,
                        FOREIGN KEY(video_path) REFERENCES video_metadata(path) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Add indices
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_video_thumbnails_video_path ON video_thumbnails(video_path)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_video_default_thumbnail ON video_thumbnails(video_path, is_default) WHERE is_default = 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "material_files_database" 
                )
                // Add all migrations in order
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_4_5
                )
                // Use destructive migration for any version mismatches except for the ones we have migrations for
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 