package me.zhanghai.android.files.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoMetadataDao {
    // Get metadata by the original video file path
    @Query("SELECT * FROM video_metadata WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): VideoMetadata?

    // Insert or replace existing metadata based on the path (Primary Key)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metadata: VideoMetadata)

    // Delete metadata by path (useful if the original file is deleted)
    @Query("DELETE FROM video_metadata WHERE path = :path")
    suspend fun deleteByPath(path: String)

    // Query to get the stored thumbnail path for a video file
    @Query("SELECT thumbnail_path FROM video_metadata WHERE path = :path LIMIT 1")
    suspend fun getThumbnailPath(path: String): String?

    // Query to specifically update the thumbnail path for an existing entry
    @Query("UPDATE video_metadata SET thumbnail_path = :thumbnailPath, last_modified = :lastModified WHERE path = :path")
    suspend fun updateThumbnailPath(path: String, thumbnailPath: String?, lastModified: Long)

    // Get all metadata entries for export/backup purposes
    @Query("SELECT * FROM video_metadata")
    suspend fun getAll(): List<VideoMetadata>
} 