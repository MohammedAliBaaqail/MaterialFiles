package me.zhanghai.android.files.provider.common

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.database.AppDatabase
import me.zhanghai.android.files.database.VideoMetadata
import me.zhanghai.android.files.database.VideoMetadataDao
import me.zhanghai.android.files.util.pathString
import me.zhanghai.android.files.util.toHex
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.readAttributes

class VideoMetadataRepository(context: Context) {
    private val videoMetadataDao: VideoMetadataDao
    private val context = context.applicationContext

    // Use a limited thread pool specifically for metadata extraction
    private val executor = Executors.newFixedThreadPool(2)

    init {
        val database = AppDatabase.getDatabase(context)
        videoMetadataDao = database.videoMetadataDao()
    }

    suspend fun getVideoDuration(path: Path): Long? {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        if (metadata != null && metadata.lastModified == attributes.lastModifiedTime().toMillis()) {
            return metadata.durationMillis
        }

        // Extract duration
        return withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(pathString)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    
                    // Extract dimensions while we have the retriever open
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    
                    // Cache the result
                    if (duration != null) {
                        val newMetadata = VideoMetadata(
                            path = pathString,
                            lastModified = attributes.lastModifiedTime().toMillis(),
                            durationMillis = duration,
                            thumbnailPath = metadata?.thumbnailPath,
                            width = width,
                            height = height
                        )
                        videoMetadataDao.insertOrReplace(newMetadata)
                    }
                    
                    duration
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video duration", e)
                null
            }
        }
    }

    suspend fun getThumbnailPath(path: Path): String? {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        if (metadata?.thumbnailPath != null && metadata.lastModified == attributes.lastModifiedTime().toMillis()) {
            return metadata.thumbnailPath
        }

        // Generate thumbnail
        return withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(pathString)
                    val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        // Generate unique filename based on video path
                        val hash = MessageDigest.getInstance("SHA-256")
                            .digest(pathString.toByteArray())
                            .toHex()
                        val thumbnailFile = File(context.filesDir, "video_thumbnails/$hash.jpg")
                        thumbnailFile.parentFile?.mkdirs()
                        
                        // Save thumbnail
                        FileOutputStream(thumbnailFile).use { out ->
                            frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        // Extract dimensions while we have the retriever open
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                        val duration = metadata?.durationMillis ?: 
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        
                        // Update database
                        val newMetadata = VideoMetadata(
                            path = pathString,
                            lastModified = attributes.lastModifiedTime().toMillis(),
                            durationMillis = duration,
                            thumbnailPath = thumbnailFile.absolutePath,
                            width = width,
                            height = height
                        )
                        videoMetadataDao.insertOrReplace(newMetadata)
                        
                        thumbnailFile.absolutePath
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating video thumbnail", e)
                null
            }
        }
    }

    // Get video dimensions with efficient caching
    suspend fun getVideoDimensions(path: Path): Pair<Int, Int>? {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        if (metadata != null && metadata.lastModified == attributes.lastModifiedTime().toMillis() && 
            metadata.width != null && metadata.height != null) {
            return Pair(metadata.width, metadata.height)
        }

        // Extract dimensions if not cached
        return withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(pathString)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    
                    if (width != null && height != null) {
                        // Update cache with dimensions
                        if (metadata != null) {
                            val updatedMetadata = metadata.copy(
                                width = width,
                                height = height
                            )
                            videoMetadataDao.insertOrReplace(updatedMetadata)
                        } else {
                            // Create new record if none exists
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            val newMetadata = VideoMetadata(
                                path = pathString,
                                lastModified = attributes.lastModifiedTime().toMillis(),
                                durationMillis = duration,
                                thumbnailPath = null,
                                width = width,
                                height = height
                            )
                            videoMetadataDao.insertOrReplace(newMetadata)
                        }
                        
                        Pair(width, height)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting video dimensions", e)
                null
            }
        }
    }

    // Get video aspect ratio (width/height) with efficient caching
    suspend fun getVideoAspectRatio(path: Path): Float? {
        return getVideoDimensions(path)?.let { (width, height) ->
            if (height > 0) width.toFloat() / height.toFloat() else null
        }
    }

    // Alias for getVideoDuration for backward compatibility
    suspend fun getDurationMillis(path: Path): Long? = getVideoDuration(path)

    // Methods for managing thumbnail paths
    suspend fun getPersistedThumbnailPath(path: Path): String? {
        return videoMetadataDao.getByPath(path.pathString)?.thumbnailPath
    }

    suspend fun updateThumbnailPath(path: Path, thumbnailPath: String?) {
        val metadata = videoMetadataDao.getByPath(path.pathString)
        if (metadata != null) {
            val updatedMetadata = metadata.copy(thumbnailPath = thumbnailPath)
            videoMetadataDao.insertOrReplace(updatedMetadata)
        }
    }

    companion object {
        private const val TAG = "VideoMetadataRepository"
    }
} 