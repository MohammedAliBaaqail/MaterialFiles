package me.zhanghai.android.files.provider.common

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import me.zhanghai.android.files.database.AppDatabase
import me.zhanghai.android.files.database.VideoMetadata
import me.zhanghai.android.files.database.VideoMetadataDao
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.pathString
import me.zhanghai.android.files.util.toHex
import me.zhanghai.android.files.provider.common.setDataSource
import me.zhanghai.android.files.util.valueCompat
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.readAttributes
import kotlin.math.max

class VideoMetadataRepository(context: Context) {
    private val videoMetadataDao: VideoMetadataDao
    private val context = context.applicationContext

    // Use a limited thread pool specifically for metadata extraction to avoid contention on encrypted storage
    private val executor = Executors.newFixedThreadPool(2)
    private val metadataDispatcher = executor.asCoroutineDispatcher()

    // Single-threaded dispatcher for VeraCrypt volumes to prevent Stagefright binder timeouts (0x80000000)
    private val veraCryptExecutor = Executors.newSingleThreadExecutor()
    private val veraCryptDispatcher = veraCryptExecutor.asCoroutineDispatcher()

    init {
        val database = AppDatabase.getDatabase(context)
        videoMetadataDao = database.videoMetadataDao()
    }

    suspend fun getVideoDuration(path: Path): Long? = withContext(Dispatchers.IO) {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        if (metadata != null && metadata.lastModified == attributes.lastModifiedTime().toMillis()) {
            return@withContext metadata.durationMillis
        }

        val isVeraCrypt = path is me.zhanghai.android.files.provider.veracrypt.VeraCryptPath || path.fileSystem is me.zhanghai.android.files.provider.veracrypt.VeraCryptFileSystem
        val targetDispatcher = if (isVeraCrypt) veraCryptDispatcher else metadataDispatcher

        // Extract duration using single-thread for VeraCrypt or limited concurrency dispatcher for local
        withContext(targetDispatcher) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
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

    suspend fun getThumbnailPath(path: Path): String? = withContext(Dispatchers.IO) {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        
        // Calculate the current item scale percentage - used to determine if we need to regenerate
        val currentScale = Settings.FILE_LIST_ITEM_SCALE.valueCompat
        
        // Get the path-specific item scale if it exists
        val pathSettings = PathSettings.getFileListItemScale(path)
        val pathSpecificScale = pathSettings.value

        // Use path-specific scale if available, otherwise use global scale
        val effectiveScale = pathSpecificScale ?: currentScale
        
        if (metadata?.thumbnailPath != null && 
            metadata.lastModified == attributes.lastModifiedTime().toMillis()) {
            // Check if the thumbnail file actually exists
            val thumbnailFile = File(metadata.thumbnailPath)
            if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                // If this is a custom thumbnail (manually selected), always use it
                if (thumbnailFile.name.contains("_custom")) {
                    return@withContext metadata.thumbnailPath
                }
                
                // Check if we need to regenerate based on scale changes
                // Only regenerate if scale increased significantly and it's not a custom thumbnail
                val existingScale = thumbnailFile.name.substringAfterLast("_scale_", "")
                    .substringBefore(".jpg", "").toFloatOrNull() ?: 0f
                
                // If current scale is significantly larger, regenerate for better quality
                if (effectiveScale >= existingScale + 50) {
                    Log.d(TAG, "Scale changed significantly: $existingScale -> $effectiveScale, regenerating thumbnail")
                    // Continue to regenerate
                } else {
                    return@withContext metadata.thumbnailPath
                }
            }
        }

        val isVeraCrypt = path is me.zhanghai.android.files.provider.veracrypt.VeraCryptPath || path.fileSystem is me.zhanghai.android.files.provider.veracrypt.VeraCryptFileSystem
        val targetDispatcher = if (isVeraCrypt) veraCryptDispatcher else metadataDispatcher

        // Generate thumbnail
        withContext(targetDispatcher) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    
                    // Extract dimensions and duration for proper sizing and cache update
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    
                    // Calculate thumbnail size based on scale
                    // Use a more reasonable base size (512px) for list thumbnails
                    val baseWidth = 512f
                    val scaleMultiplier = max(1.0f, effectiveScale / 100.0f)
                    val targetWidth = (baseWidth * scaleMultiplier).toInt().coerceAtMost(max(width, height).takeIf { it > 0 } ?: 1024)
                    val targetHeight = if (width > 0) (targetWidth.toFloat() / width * height).toInt() else targetWidth
                    
                    Log.d(TAG, "Extracting thumbnail for $pathString at target size $targetWidth x $targetHeight (isVeraCrypt=$isVeraCrypt)")
                    
                    // Check if video has embedded picture via metadata hint before trying to extract it
                    val hasImage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE) == "yes"
                    } else {
                        // On older versions we can't be sure without trying or checking other keys
                        true
                    }

                    // First try to get embedded picture (highest quality source for albums/posters)
                    // This is much faster than decoding a video frame
                    val embeddedPicture = if (hasImage) {
                        try {
                            retriever.embeddedPicture?.let { bytes ->
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    
                    // If no embedded picture, get a video frame
                    val frame = if (embeddedPicture == null) {
                        // Request a frame at 0 microseconds for VeraCrypt/slow paths to avoid expensive seeking,
                        // or at 1/3 of the video duration for fast local files
                        val frameTimeMicros = if (isVeraCrypt) {
                            0L
                        } else {
                            TimeUnit.MICROSECONDS.convert(
                                durationMillis / 3, TimeUnit.MILLISECONDS
                            )
                        }
                        
                        Log.d(TAG, "Extracting keyframe at $frameTimeMicros micros for $pathString")
                        
                        // CRITICAL: Use OPTION_CLOSEST_SYNC for speed (decodes nearest keyframe)
                        // OPTION_CLOSEST decodes all frames between keyframes, taking seconds/decades on encrypted files
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && width > 0 && height > 0) {
                            retriever.getScaledFrameAtTime(
                                frameTimeMicros,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                targetWidth,
                                targetHeight
                            )
                        } else {
                            retriever.getFrameAtTime(
                                frameTimeMicros,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                        }
                    } else null
                    
                    // Use either the embedded image or the video frame
                    val finalFrame = embeddedPicture ?: frame
                    
                    if (finalFrame != null) {
                        // Ensure the frame fills the container without black areas
                        val finalBitmap = if (finalFrame.width < finalFrame.height * width / height ||
                                             finalFrame.height < finalFrame.width * height / width) {
                            // If the aspect ratio doesn't match, create a new bitmap that fills the container
                            val canvas = android.graphics.Canvas()
                            val paint = android.graphics.Paint().apply {
                                isFilterBitmap = true
                                isAntiAlias = true
                            }
                            
                            // Calculate scaling factors to fill the target dimensions
                            val scaleWidth = targetWidth / finalFrame.width.toFloat()
                            val scaleHeight = targetHeight / finalFrame.height.toFloat()
                            val scaleFactor = max(scaleWidth, scaleHeight)
                            
                            // Create a bitmap with the target dimensions
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888
                            )
                            
                            canvas.setBitmap(bitmap)
                            
                            // Calculate centered position
                            val left = (targetWidth - finalFrame.width * scaleFactor) / 2
                            val top = (targetHeight - finalFrame.height * scaleFactor) / 2
                            
                            // Setup matrix for scaling
                            val matrix = android.graphics.Matrix()
                            matrix.setScale(scaleFactor, scaleFactor)
                            matrix.postTranslate(left, top)
                            
                            // Draw the scaled bitmap centered in the container
                            canvas.drawBitmap(finalFrame, matrix, paint)
                            bitmap
                        } else {
                            finalFrame
                        }
                        
                        // Generate unique filename based on video path and include scale factor to track regeneration needs
                        val hash = MessageDigest.getInstance("SHA-256")
                            .digest(pathString.toByteArray())
                            .toHex()
                        val thumbnailFile = File(context.filesDir, "video_thumbnails/${hash}_scale_${effectiveScale}.jpg")
                        thumbnailFile.parentFile?.mkdirs()
                        
                        // Clean up any old thumbnails for this video to save space
                        // But never delete custom thumbnails
                        try {
                            val basePrefix = "$hash"
                            thumbnailFile.parentFile?.listFiles()?.forEach { file ->
                                if (file.name.startsWith(basePrefix) && 
                                    file.absolutePath != thumbnailFile.absolutePath &&
                                    !file.name.contains("_custom")) {
                                    Log.d(TAG, "Cleaning up old thumbnail: ${file.absolutePath}")
                                    file.delete()
                                }
                            }
                        } catch (e: Exception) {
                            // Non-critical error, just log it
                            Log.w(TAG, "Error cleaning up old thumbnails", e)
                        }
                        
                        // Save thumbnail with 100% quality to ensure it looks good at all scales
                        // This matches what the VideoThumbnailManagementDialogFragment does for custom thumbnails
                        FileOutputStream(thumbnailFile).use { out ->
                            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        
                        // If we created a new bitmap, recycle it to free memory
                        if (finalBitmap != finalFrame) {
                            finalBitmap.recycle()
                        }
                        
                        Log.d(TAG, "Saved thumbnail to ${thumbnailFile.absolutePath}")
                        
                        // Extract dimensions while we have the retriever open
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
    suspend fun getVideoDimensions(path: Path): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val pathString = path.pathString
        val attributes = path.readAttributes<BasicFileAttributes>(BasicFileAttributes::class.java)
        
        // Check if we have cached metadata that's still valid
        val metadata = videoMetadataDao.getByPath(pathString)
        if (metadata != null && metadata.lastModified == attributes.lastModifiedTime().toMillis() && 
            metadata.width != null && metadata.height != null) {
            return@withContext Pair(metadata.width, metadata.height)
        }

        // Extract dimensions if not cached
        withContext(metadataDispatcher) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
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

    /**
     * Updates the path for a video when it's renamed or moved
     * This preserves the thumbnail association with the file
     *
     * @param oldPath The original path before the move/rename
     * @param newPath The new path after the move/rename
     */
    suspend fun updatePathForFile(oldPath: Path, newPath: Path) {
        val oldPathString = oldPath.toString()
        val newPathString = newPath.toString()
        
        // Get the metadata for the old path
        val metadata = videoMetadataDao.getByPath(oldPathString) ?: return
        
        // Create new metadata entry with updated path
        val newMetadata = metadata.copy(path = newPathString)
        
        // Insert the new entry and delete the old one
        videoMetadataDao.insertOrReplace(newMetadata)
        videoMetadataDao.deleteByPath(oldPathString)
        
        Log.d(TAG, "Updated video metadata path from $oldPathString to $newPathString")
    }
} 