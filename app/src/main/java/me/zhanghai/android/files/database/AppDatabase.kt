package me.zhanghai.android.files.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoMetadata::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videoMetadataDao(): VideoMetadataDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "material_files_database" // Database file name
                )
                // Add only the known working migrations
                .addMigrations(MIGRATION_1_2)
                // Use destructive migration when downgrading from version 3 to any lower version
                .fallbackToDestructiveMigrationFrom(3)
                // Also use destructive migration for any other version mismatches
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 