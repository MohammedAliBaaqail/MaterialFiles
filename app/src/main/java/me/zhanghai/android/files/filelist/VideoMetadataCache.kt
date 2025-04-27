/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import java.time.Duration
import java8.nio.file.Path
import me.zhanghai.android.files.fileproperties.extractMetadataNotBlank
import me.zhanghai.android.files.util.setDataSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A cache for video metadata, specifically durations, to avoid repeatedly 
 * extracting data from the same video files.
 */
object VideoMetadataCache {
    private val cache = ConcurrentHashMap<Path, Duration?>()
    private val executor = Executors.newFixedThreadPool(2)
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    
    /**
     * Extracts the duration of a video file asynchronously and caches the result.
     * 
     * @param path Path to the video file
     * @param callback Callback to receive the duration when available
     */
    fun getVideoDuration(path: Path, callback: (Duration?) -> Unit) {
        // Return cached result immediately if available
        val cachedDuration = cache[path]
        if (cachedDuration != null) {
            callback(cachedDuration)
            return
        }
        
        // Extract duration in a background thread
        executor.execute {
            try {
                val duration = MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.let { Duration.ofMillis(it) }
                }
                
                // Cache the result (null is valid to avoid repeated extraction attempts)
                cache[path] = duration
                
                // Call back on main thread
                mainThreadHandler.post {
                    callback(duration)
                }
            } catch (e: Exception) {
                // Cache null to prevent repeated attempts
                cache[path] = null
                mainThreadHandler.post {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Get the duration of a video file synchronously (blocking)
     * 
     * @param path The path to the video file
     * @return The duration in milliseconds, or null if not available
     */
    fun getVideoDurationSync(path: Path): Duration? {
        // Handle null path to prevent NullPointerException
        if (path == null) {
            return null
        }
        
        return cache.getOrPut(path) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    retriever.extractMetadataNotBlank(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull()?.let { Duration.ofMillis(it) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Clears the cache, useful when low on memory
     */
    fun clearCache() {
        cache.clear()
    }
} 