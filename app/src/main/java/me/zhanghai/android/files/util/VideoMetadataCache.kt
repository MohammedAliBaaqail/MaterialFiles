/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import java8.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A cache for video metadata, specifically durations, to avoid repeatedly 
 * extracting data from the same video files.
 */
object VideoMetadataCache {
    private val cache = ConcurrentHashMap<Path, Long?>()
    private val executor = Executors.newFixedThreadPool(2)
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    
    /**
     * Extracts the duration of a video file asynchronously and caches the result.
     * 
     * @param path Path to the video file
     * @param callback Callback to receive the duration when available
     */
    fun getVideoDuration(path: Path?, callback: (Long?) -> Unit) {
        // Validate path to prevent NullPointerException
        if (path == null) {
            callback(null)
            return
        }
        
        try {
            // Return cached result immediately if available
            val cachedDuration = cache[path]
            if (cachedDuration != null) {
                callback(cachedDuration)
                return
            }
        } catch (e: Exception) {
            // If there's an issue with the path as a key, just continue to fetch
        }
        
        // Extract duration in a background thread
        executor.execute {
            try {
                val duration = MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path.toFile().absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                }
                
                // Safely cache the result
                try {
                    cache[path] = duration
                } catch (e: Exception) {
                    // Ignore cache errors
                }
                
                // Call back on main thread
                mainThreadHandler.post {
                    callback(duration)
                }
            } catch (e: Exception) {
                // Try to cache null to prevent repeated attempts
                try {
                    cache[path] = null
                } catch (e: Exception) {
                    // Ignore cache errors
                }
                
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
    fun getVideoDurationSync(path: Path?): Long? {
        // Handle null path to prevent NullPointerException
        if (path == null) {
            return null
        }
        
        try {
            // Try to get from cache first
            val cachedDuration = cache[path]
            if (cachedDuration != null) {
                return cachedDuration
            }
        } catch (e: Exception) {
            // If there's a problem with the path key, continue with extraction
        }
        
        var duration: Long? = null
        try {
            duration = MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path.toFile().absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
            
            // Safely cache the result
            try {
                if (path != null) {
                    cache[path] = duration
                }
            } catch (e: Exception) {
                // Ignore cache failures
            }
        } catch (e: Exception) {
            // Try to cache null value on failure
            try {
                if (path != null) {
                    cache[path] = null
                }
            } catch (e: Exception) {
                // Ignore cache failures
            }
        }
        
        return duration
    }
    
    /**
     * Clears the cache, useful when low on memory
     */
    fun clearCache() {
        cache.clear()
    }
} 