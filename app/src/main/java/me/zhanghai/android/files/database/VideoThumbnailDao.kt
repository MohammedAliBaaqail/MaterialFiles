package me.zhanghai.android.files.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for video thumbnail operations.
 */
@Dao
interface VideoThumbnailDao {
    @Query("SELECT * FROM video_thumbnails WHERE video_path = :videoPath ORDER BY display_order, id")
    fun getThumbnailsForVideo(videoPath: String): Flow<List<VideoThumbnail>>

    @Query("SELECT * FROM video_thumbnails WHERE video_path = :videoPath AND is_default = 1 LIMIT 1")
    fun getDefaultThumbnail(videoPath: String): Flow<VideoThumbnail?>

    @Query("SELECT * FROM video_thumbnails WHERE video_path = :videoPath ORDER BY display_order, id LIMIT 1")
    suspend fun getFirstThumbnail(videoPath: String): VideoThumbnail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thumbnail: VideoThumbnail): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(thumbnails: List<VideoThumbnail>): List<Long>

    @Update
    suspend fun update(thumbnail: VideoThumbnail)

    @Update
    suspend fun updateAll(thumbnails: List<VideoThumbnail>)

    @Delete
    suspend fun delete(thumbnail: VideoThumbnail)

    @Query("DELETE FROM video_thumbnails WHERE video_path = :videoPath")
    suspend fun deleteAllForVideo(videoPath: String)

    @Query("UPDATE video_thumbnails SET is_default = 0 WHERE video_path = :videoPath")
    suspend fun clearDefaultFlag(videoPath: String)

    @Query("SELECT COUNT(*) FROM video_thumbnails WHERE video_path = :videoPath")
    suspend fun getThumbnailCount(videoPath: String): Int

    @Query("SELECT * FROM video_thumbnails WHERE video_path = :videoPath AND timestamp_ms <= :timestampMs ORDER BY timestamp_ms DESC LIMIT 1")
    suspend fun getNearestThumbnail(videoPath: String, timestampMs: Long): VideoThumbnail?
}
