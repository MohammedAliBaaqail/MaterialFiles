package me.zhanghai.android.files.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.database.VideoThumbnail
import me.zhanghai.android.files.database.VideoThumbnailDao
import java8.nio.file.Path
import java8.nio.file.Paths
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * Repository for managing video thumbnails.
 */
class VideoThumbnailRepository(
    private val videoThumbnailDao: VideoThumbnailDao,
    private val context: Context
) {
    /**
     * Get all thumbnails for a video.
     * @return Flow that emits the list of thumbnails whenever they change
     */
    fun getThumbnailsForVideo(videoPath: String): Flow<List<VideoThumbnail>> {
        return videoThumbnailDao.getThumbnailsForVideo(videoPath)
    }

    /**
     * Get the default thumbnail for a video.
     * @return Flow that emits the default thumbnail whenever it changes.
     *         Will emit null if no default thumbnail is set.
     */
    fun getDefaultThumbnailFlow(videoPath: String): Flow<VideoThumbnail?> {
        return videoThumbnailDao.getDefaultThumbnail(videoPath)
    }

    /**
     * Get the default thumbnail for a video.
     * Falls back to the first thumbnail if no default is set.
     * @return The default thumbnail or null if none exists
     */
    suspend fun getDefaultThumbnail(videoPath: String): VideoThumbnail? {
        return videoThumbnailDao.getDefaultThumbnail(videoPath).firstOrNull()
            ?: videoThumbnailDao.getThumbnailsForVideo(videoPath).firstOrNull()?.firstOrNull()
    }

    /**
     * Delete a thumbnail.
     */
    suspend fun deleteThumbnail(originalPath: Path, position: Long) {
        // Get the thumbnail first using nearest timestamp
        val thumbnail = videoThumbnailDao.getNearestThumbnail(originalPath.toString(), position)
        if (thumbnail != null) {
            // Delete the thumbnail file
            try {
                val thumbnailFile = File(thumbnail.thumbnailPath)
                thumbnailFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Delete from database
            videoThumbnailDao.delete(thumbnail)
        }
    }

    /**
     * Set a thumbnail as the default.
     */
    suspend fun setDefaultThumbnail(originalPath: Path, position: Long) {
        // Get the current default and unset it
        val currentDefault = videoThumbnailDao.getDefaultThumbnail(originalPath.toString()).firstOrNull()
        currentDefault?.let {
            videoThumbnailDao.update(it.copy(isDefault = false))
        }
        
        // Set the new default using nearest timestamp
        val newDefault = videoThumbnailDao.getNearestThumbnail(originalPath.toString(), position)
        newDefault?.let {
            videoThumbnailDao.update(it.copy(isDefault = true))
        }
    }

    /**
     * Add a new thumbnail for a video.
     * @param videoPath Path to the video file
     * @param thumbnailUri URI of the thumbnail image
     * @param timestampMs Timestamp in the video this thumbnail represents
     * @param isDefault Whether this should be the default thumbnail
     * @return The created thumbnail or null if failed
     */
    suspend fun addThumbnail(
        videoPath: String,
        thumbnailUri: Uri,
        timestampMs: Long,
        isDefault: Boolean = false
    ): VideoThumbnail? = withContext(Dispatchers.IO) {
        try {
            // Create thumbnails directory if it doesn't exist
            val thumbnailsDir = File(context.cacheDir, "video_thumbnails")
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs()
            }

            // Generate a unique filename for the thumbnail
            val videoFile = File(videoPath)
            val timestampStr = "%06d".format(timestampMs / 1000) // Format as seconds with leading zeros
            val thumbnailFile = File(
                thumbnailsDir,
                "${videoFile.nameWithoutExtension}_${timestampStr}_${UUID.randomUUID().toString().take(8)}.jpg"
            )

            // Copy the thumbnail to our directory
            context.contentResolver.openInputStream(thumbnailUri)?.use { input ->
                thumbnailFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Get the next display order
            val nextOrder = videoThumbnailDao.getThumbnailCount(videoPath)

            // If this is set as default, clear the default flag from other thumbnails
            if (isDefault) {
                videoThumbnailDao.clearDefaultFlag(videoPath)
            }

            // Create and insert the thumbnail
            val thumbnail = VideoThumbnail(
                videoPath = videoPath,
                thumbnailPath = thumbnailFile.absolutePath,
                timestampMs = timestampMs,
                displayOrder = nextOrder,
                isDefault = isDefault
            )

            val id = videoThumbnailDao.insert(thumbnail)
            thumbnail.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Set a thumbnail as the default.
     */
    suspend fun setDefaultThumbnail(thumbnail: VideoThumbnail) = withContext(Dispatchers.IO) {
        videoThumbnailDao.clearDefaultFlag(thumbnail.videoPath)
        videoThumbnailDao.update(thumbnail.copy(isDefault = true))
    }

    /**
     * Delete a thumbnail.
     */
    suspend fun deleteThumbnail(thumbnail: VideoThumbnail) = withContext(Dispatchers.IO) {
        // Delete the thumbnail file
        try {
            File(thumbnail.thumbnailPath).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Delete from database
        videoThumbnailDao.delete(thumbnail)
    }

    /**
     * Delete all thumbnails for a video.
     */
    suspend fun deleteAllThumbnails(videoPath: String) = withContext(Dispatchers.IO) {
        // Get all thumbnails first to delete their files
        val thumbnails = videoThumbnailDao.getThumbnailsForVideo(videoPath).first()
        
        // Delete all thumbnail files
        thumbnails.forEach { thumbnail ->
            try {
                File(thumbnail.thumbnailPath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Delete from database
        videoThumbnailDao.deleteAllForVideo(videoPath)
    }

    /**
     * Update the display interval for a thumbnail.
     */
    suspend fun updateThumbnailInterval(thumbnail: VideoThumbnail, intervalMs: Long) = withContext(Dispatchers.IO) {
        videoThumbnailDao.update(thumbnail.copy(displayIntervalMs = intervalMs))
    }

    /**
     * Update the display order of thumbnails.
     */
    suspend fun updateThumbnailsOrder(thumbnails: List<VideoThumbnail>) = withContext(Dispatchers.IO) {
        val updated = thumbnails.mapIndexed { index, thumbnail ->
            thumbnail.copy(displayOrder = index)
        }
        videoThumbnailDao.updateAll(updated)
    }

    companion object {
        @Volatile
        private var instance: VideoThumbnailRepository? = null

        fun getInstance(videoThumbnailDao: VideoThumbnailDao, context: Context): VideoThumbnailRepository {
            return instance ?: synchronized(this) {
                instance ?: VideoThumbnailRepository(videoThumbnailDao, context).also { instance = it }
            }
        }
    }
}
