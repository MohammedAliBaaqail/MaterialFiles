/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class FileListLiveData(private val path: Path) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null

    private val observer: PathObserver

    @Volatile
    private var isChangedWhileInactive = false

    init {
        loadValue()
        observer = PathObserver(path) { onChangeObserved() }
    }

    fun loadValue() {
        future?.cancel(true)
        
        // Try to get cached data first
        val cachedList = try {
            val attributes = path.readAttributes(BasicFileAttributes::class.java)
            val lastModified = attributes.lastModifiedTime().toMillis()
            android.util.Log.d("FileListLiveData", "Checking cache for ${path} (lastModified=$lastModified)")
            val cached = FileListCache.get(path, lastModified)
            if (cached != null) {
                android.util.Log.d("FileListLiveData", "Cache HIT for ${path}: ${cached.size} items")
            } else {
                android.util.Log.d("FileListLiveData", "Cache MISS for ${path}")
            }
            cached
        } catch (e: Exception) {
            if (e !is me.zhanghai.android.files.provider.common.UserActionRequiredException) {
                android.util.Log.e("FileListLiveData", "Error checking cache for ${path}", e)
            }
            null
        }
        
        // Show cached data immediately if available - this prevents sequential loading
        if (cachedList != null) {
            value = Success(cachedList)
        } else {
            value = Loading(value?.value)
        }
        
        val hasCache = cachedList != null
        
        // Load fresh data in background
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            try {
                val attributes = path.readAttributes(BasicFileAttributes::class.java)
                val lastModified = attributes.lastModifiedTime().toMillis()
                
                path.newDirectoryStream().use { directoryStream ->
                    val fileList = mutableListOf<FileItem>()
                    val batchSize = 200
                    var lastEmitted = 0
                    
                    val pathBatch = java.util.ArrayList<Path>(batchSize)
                    
                    for (childPath in directoryStream) {
                        pathBatch.add(childPath)
                        
                        if (pathBatch.size >= batchSize) {
                            val batchItems = StreamSupport.stream(pathBatch.spliterator(), true)
                                .map {
                                    try {
                                        it.loadFileItem()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                }
                                .filter { it != null }
                                .collect(Collectors.toList())
                            
                            for (item in batchItems) {
                                if (item != null) fileList.add(item)
                            }
                            pathBatch.clear()
                            
                            if (!hasCache) {
                                postValue(Success(fileList.toList()))
                                lastEmitted = fileList.size
                            }
                        }
                    }
                    
                    // Process remaining items
                    if (pathBatch.isNotEmpty()) {
                        val batchItems = StreamSupport.stream(pathBatch.spliterator(), true)
                            .map {
                                try {
                                    it.loadFileItem()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }
                            .filter { it != null }
                            .collect(Collectors.toList())
                        
                        for (item in batchItems) {
                            if (item != null) fileList.add(item)
                        }
                    }
                    
                    // Final full result
                    val finalList = fileList.toList()
                    // Update cache
                    FileListCache.put(path, finalList, lastModified)
                    // Only emit final result if we didn't have cache or if data actually changed
                    if (!hasCache) {
                        postValue(Success(finalList))
                    } else {
                        // If we had cache, only update if the list actually changed
                        // Compare sizes first for quick check
                        if (finalList.size != cachedList!!.size) {
                            postValue(Success(finalList))
                        } else {
                            // Lists might be the same, but update cache timestamp anyway
                            // Don't emit to avoid unnecessary UI updates
                        }
                    }
                }
            } catch (e: Exception) {
                postValue(Failure(valueCompat.value, e))
            }
        }
    }

    private fun onChangeObserved() {
        // Invalidate cache when directory changes
        FileListCache.invalidate(path)
        if (hasActiveObservers()) {
            loadValue()
        } else {
            isChangedWhileInactive = true
        }
    }

    override fun onActive() {
        if (isChangedWhileInactive) {
            loadValue()
            isChangedWhileInactive = false
        }
    }

    override fun close() {
        observer.close()
        future?.cancel(true)
    }
}
