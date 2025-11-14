/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcel
import android.util.Log
import me.zhanghai.android.files.app.application
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Persistent cache for file lists to avoid reloading on navigation.
 * Cache persists across app restarts, stored in files directory.
 */
object FileListCache {
    private const val TAG = "FileListCache"
    private const val CACHE_DIR_NAME = "file_list_cache"
    
    private data class CachedList(
        val fileList: List<FileItem>,
        val lastModified: Long
    )
    
    private val cache = ConcurrentHashMap<String, CachedList>()
    private val lock = ReentrantReadWriteLock()
    
    @Volatile
    private var isLoaded = false
    
    @Volatile
    private var isLoading = false
    
    private val cacheDir: File by lazy {
        File(application.filesDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    init {
        // Load cache asynchronously to avoid blocking
        Thread {
            synchronized(this) {
                if (isLoaded) return@Thread
                isLoading = true
                try {
                    loadCache()
                    isLoaded = true
                } finally {
                    isLoading = false
                }
            }
        }.start()
    }
    
    /**
     * Get cached file list if available and still valid.
     */
    fun get(path: Path, currentLastModified: Long): List<FileItem>? {
        ensureLoaded()
        val pathString = path.toString()
        lock.read {
            val cached = cache[pathString] ?: return null
            // Use <= instead of < to allow for small timestamp differences
            // Also add a small tolerance (1 second) for filesystem timestamp precision
            if (cached.lastModified + 1000 < currentLastModified) {
                // Cache is stale, remove it asynchronously
                lock.write {
                    cache.remove(pathString)
                    Thread {
                        deleteCacheFile(pathString)
                    }.start()
                }
                return null
            }
            Log.d(TAG, "Cache hit for $pathString (${cached.fileList.size} items)")
            return cached.fileList
        }
    }
    
    /**
     * Store file list in cache. No size limit - stores everything.
     */
    fun put(path: Path, fileList: List<FileItem>, lastModified: Long) {
        ensureLoaded()
        val pathString = path.toString()
        lock.write {
            cache[pathString] = CachedList(fileList, lastModified)
            Log.d(TAG, "Caching $pathString (${fileList.size} items, lastModified=$lastModified)")
            // Save asynchronously to avoid blocking
            Thread {
                saveCacheFile(pathString, fileList, lastModified)
            }.start()
        }
    }
    
    /**
     * Invalidate cache for a path.
     */
    fun invalidate(path: Path) {
        val pathString = path.toString()
        lock.write {
            cache.remove(pathString)
            deleteCacheFile(pathString)
        }
    }
    
    /**
     * Clear all cached data.
     */
    fun clear() {
        lock.write {
            cache.clear()
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache directory", e)
            }
        }
    }
    
    private fun ensureLoaded() {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            if (isLoading) {
                // Wait for async load to complete
                var waitCount = 0
                while (isLoading && waitCount < 100) {
                    Thread.sleep(10)
                    waitCount++
                }
                if (isLoaded) return
            }
            isLoading = true
            try {
                loadCache()
                isLoaded = true
                Log.d(TAG, "Cache loaded: ${cache.size} entries")
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun loadCache() {
        lock.write {
            try {
                cache.clear()
                val indexFile = File(cacheDir, "index.json")
                if (!indexFile.exists()) {
                    Log.d(TAG, "No cache index file found")
                    return
                }
                
                val indexText = indexFile.readText()
                if (indexText.isBlank()) {
                    Log.d(TAG, "Cache index file is empty")
                    return
                }
                
                val indexJson = org.json.JSONObject(indexText)
                var loadedCount = 0
                
                for (pathString in indexJson.keys()) {
                    try {
                        val hash = indexJson.getString(pathString)
                        val cacheFile = File(cacheDir, "$hash.cache")
                        if (!cacheFile.exists()) {
                            Log.w(TAG, "Cache file not found for $pathString (hash=$hash)")
                            continue
                        }
                        
                        val bytes = cacheFile.readBytes()
                        if (bytes.isEmpty()) {
                            Log.w(TAG, "Cache file is empty for $pathString")
                            continue
                        }
                        
                        val cachedList = deserializeList(bytes)
                        if (cachedList != null) {
                            // Read lastModified from a metadata file or use cache file's lastModified
                            // Store it in the index for better reliability
                            val lastModified = try {
                                // Try to read from metadata file first
                                val metaFile = File(cacheDir, "$hash.meta")
                                if (metaFile.exists()) {
                                    metaFile.readText().toLongOrNull() ?: cacheFile.lastModified()
                                } else {
                                    cacheFile.lastModified()
                                }
                            } catch (e: Exception) {
                                cacheFile.lastModified()
                            }
                            
                            cache[pathString] = CachedList(cachedList, lastModified)
                            loadedCount++
                            Log.d(TAG, "Loaded cache for $pathString: ${cachedList.size} items")
                        } else {
                            Log.w(TAG, "Failed to deserialize cache for $pathString")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error loading cache for $pathString", e)
                    }
                }
                Log.d(TAG, "Cache load complete: $loadedCount entries loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cache", e)
            }
        }
    }
    
    private fun saveCacheFile(pathString: String, fileList: List<FileItem>, lastModified: Long) {
        try {
            val hash = getPathHash(pathString)
            val cacheFile = File(cacheDir, "$hash.cache")
            val bytes = serializeList(fileList)
            
            if (bytes.isEmpty()) {
                Log.e(TAG, "Serialized data is empty for $pathString")
                return
            }
            
            cacheFile.writeBytes(bytes)
            // Store lastModified in a separate metadata file for reliability
            val metaFile = File(cacheDir, "$hash.meta")
            metaFile.writeText(lastModified.toString())
            
            // Update file modification time to match directory lastModified
            cacheFile.setLastModified(lastModified)
            
            // Update index
            updateIndex(pathString, hash)
            
            Log.d(TAG, "Saved cache for $pathString: ${fileList.size} items, ${bytes.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache file for $pathString", e)
            e.printStackTrace()
        }
    }
    
    private fun deleteCacheFile(pathString: String) {
        try {
            val indexFile = File(cacheDir, "index.json")
            if (indexFile.exists()) {
                val indexJson = try {
                    org.json.JSONObject(indexFile.readText())
                } catch (e: Exception) {
                    org.json.JSONObject()
                }
                if (indexJson.has(pathString)) {
                    val hash = indexJson.getString(pathString)
                    indexJson.remove(pathString)
                    indexFile.writeText(indexJson.toString())
                    
                    // Delete cache file and metadata file
                    File(cacheDir, "$hash.cache").delete()
                    File(cacheDir, "$hash.meta").delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache file for $pathString", e)
        }
    }
    
    private fun getPathHash(pathString: String): String {
        // Use hash to avoid filesystem issues with special characters
        return pathString.hashCode().toString()
    }
    
    private fun updateIndex(pathString: String, hash: String) {
        try {
            val indexFile = File(cacheDir, "index.json")
            val indexJson = if (indexFile.exists()) {
                org.json.JSONObject(indexFile.readText())
            } else {
                org.json.JSONObject()
            }
            indexJson.put(pathString, hash)
            indexFile.writeText(indexJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating index", e)
        }
    }
    
    private fun serializeList(fileList: List<FileItem>): ByteArray {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(fileList.size)
            for (item in fileList) {
                parcel.writeParcelable(item, 0)
            }
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }
    
    private fun deserializeList(bytes: ByteArray): List<FileItem>? {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val size = parcel.readInt()
            val list = mutableListOf<FileItem>()
            for (i in 0 until size) {
                @Suppress("UNCHECKED_CAST")
                val item = parcel.readParcelable<FileItem>(application.classLoader)
                if (item != null) {
                    list.add(item)
                }
            }
            return list
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing list", e)
            return null
        } finally {
            parcel.recycle()
        }
    }
}

