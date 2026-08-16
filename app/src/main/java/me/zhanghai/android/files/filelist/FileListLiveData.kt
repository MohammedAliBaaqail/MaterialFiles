/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
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
        value = Loading(value?.value)

        // Load attributes, check cache, and read directory stream entirely in background
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            try {
                val attributes = path.readAttributes(BasicFileAttributes::class.java)
                val lastModified = attributes.lastModifiedTime().toMillis()

                // Try to get cached data first in background
                val cachedList = FileListCache.get(path, lastModified)
                var hasCache = false
                if (cachedList != null) {
                    hasCache = true
                    postValue(Success(cachedList))
                }

                path.newDirectoryStream().use { directoryStream ->
                    val fileList = mutableListOf<FileItem>()
                    val batchSize = 200

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
                    FileListCache.put(path, finalList, lastModified)
                    if (!hasCache || finalList.size != cachedList?.size || finalList != cachedList) {
                        postValue(Success(finalList))
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
