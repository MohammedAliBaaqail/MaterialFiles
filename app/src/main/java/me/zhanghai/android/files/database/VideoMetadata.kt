package me.zhanghai.android.files.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_metadata")
data class VideoMetadata(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String, // Use String for path as Room doesn't support Path directly

    @ColumnInfo(name = "last_modified")
    val lastModified: Long, // Store last modified time to check for staleness

    @ColumnInfo(name = "duration_ms")
    val durationMillis: Long?, // Video duration in milliseconds

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?, // Path to the saved thumbnail file in local storage

    @ColumnInfo(name = "width")
    val width: Int? = null, // Video width in pixels

    @ColumnInfo(name = "height")
    val height: Int? = null // Video height in pixels
) 