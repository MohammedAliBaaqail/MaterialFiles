package me.zhanghai.android.files.database

import androidx.room.*
import java.time.Instant

/**
 * Represents a thumbnail for a video file.
 * 
 * @property id Auto-generated unique ID for the thumbnail
 * @property videoPath Path to the video file this thumbnail belongs to
 * @property thumbnailPath Path to the thumbnail image file in local storage
 * @property timestampMs The timestamp in the video (in milliseconds) this thumbnail represents
 * @property displayOrder The order in which thumbnails should be displayed
 * @property isDefault Whether this is the default/primary thumbnail
 * @property createdAt When this thumbnail was created
 * @property displayIntervalMs How long to display this thumbnail in the slideshow (in milliseconds)
 */
@Entity(
    tableName = "video_thumbnails",
    foreignKeys = [
        ForeignKey(
            entity = VideoMetadata::class,
            parentColumns = ["path"],
            childColumns = ["video_path"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("video_path"),
        Index(value = ["video_path", "is_default"], unique = true, name = "idx_video_default_thumbnail")
    ]
)
data class VideoThumbnail(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "video_path")
    val videoPath: String,

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "display_interval_ms")
    val displayIntervalMs: Long = 1000L // Default 1 second
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 1000L // 1 second
    }
}
