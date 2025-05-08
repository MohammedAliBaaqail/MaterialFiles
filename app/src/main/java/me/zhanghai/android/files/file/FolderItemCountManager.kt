/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages folder item counts in a persistent store.
 * This allows the app to remember and display item counts for folders without recounting.
 */
object FolderItemCountManager {
    private const val TAG = "FolderItemCountManager"
    private const val PREFS_NAME = "folder_item_counts"
    private const val KEY_ITEM_COUNTS = "item_counts"
    
    // JSON keys
    private const val JSON_KEY_PATH = "path"
    private const val JSON_KEY_COUNT = "count"
    private const val JSON_KEY_LAST_MODIFIED = "lastModified"
    
    private val lock = ReentrantReadWriteLock()
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Cache that stores folder path to ItemCount data
    private val itemCountMap = mutableMapOf<String, ItemCount>()
    
    private val _countChangedLiveData = MutableLiveData<Unit>()
    val countChangedLiveData: LiveData<Unit>
        get() = _countChangedLiveData
    
    // Class to hold item count data with last modified timestamp for staleness checking
    data class ItemCount(
        val count: Int,
        val lastModified: Long
    )
    
    init {
        loadItemCounts()
    }
    
    /**
     * Get the cached item count for a folder if available
     * 
     * @param path The folder path
     * @param currentLastModified The folder's current last modified timestamp
     * @return The cached item count or null if not available or stale
     */
    fun getItemCount(path: Path, currentLastModified: Long): Int? {
        lock.read {
            val itemCount = itemCountMap[path.toString()] ?: return null
            
            // If the folder has been modified since we last counted, consider the count stale
            if (itemCount.lastModified < currentLastModified) {
                return null
            }
            
            return itemCount.count
        }
    }
    
    /**
     * Updates the item count for a folder
     * 
     * @param path The folder path
     * @param count The number of items in the folder
     * @param lastModified The folder's last modified timestamp
     */
    fun setItemCount(path: Path, count: Int, lastModified: Long) {
        lock.write {
            val pathString = path.toString()
            itemCountMap[pathString] = ItemCount(count, lastModified)
            saveItemCounts()
            _countChangedLiveData.value = Unit
        }
    }
    
    /**
     * Remove a folder's item count from the cache
     * 
     * @param path The folder path
     */
    fun removeItemCount(path: Path) {
        lock.write {
            val pathString = path.toString()
            if (itemCountMap.remove(pathString) != null) {
                saveItemCounts()
                _countChangedLiveData.value = Unit
            }
        }
    }
    
    /**
     * Updates the stored path when a folder is moved or renamed
     * 
     * @param oldPath The original path
     * @param newPath The new path
     */
    fun updatePathForFolder(oldPath: Path, newPath: Path) {
        val oldPathKey = oldPath.toString()
        val newPathKey = newPath.toString()
        
        lock.write {
            itemCountMap[oldPathKey]?.let { itemCount ->
                Log.d(TAG, "Moving item count from $oldPathKey to $newPathKey")
                itemCountMap[newPathKey] = itemCount
                itemCountMap.remove(oldPathKey)
                saveItemCounts()
            }
        }
    }
    
    /**
     * Get all item counts for export
     */
    fun getAllItemCounts(): Map<String, ItemCount> {
        lock.read {
            return itemCountMap.toMap()
        }
    }
    
    /**
     * Exports all item count data to a JSON object that can be included in backups
     */
    fun exportItemCountsToJson(): JSONObject {
        val jsonObject = JSONObject()
        
        lock.read {
            val countsArray = JSONArray()
            
            for ((path, itemCount) in itemCountMap) {
                val countObj = JSONObject().apply {
                    put(JSON_KEY_PATH, path)
                    put(JSON_KEY_COUNT, itemCount.count)
                    put(JSON_KEY_LAST_MODIFIED, itemCount.lastModified)
                }
                countsArray.put(countObj)
            }
            
            jsonObject.put(KEY_ITEM_COUNTS, countsArray)
        }
        
        return jsonObject
    }
    
    /**
     * Exports folder item counts to a file
     * 
     * @param file The destination file to write the exported data
     * @return true if export was successful, false otherwise
     */
    fun exportItemCounts(file: File): Boolean {
        return try {
            val jsonObject = exportItemCountsToJson()
            file.writeText(jsonObject.toString(2)) // Use indented format
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting item counts", e)
            false
        }
    }
    
    /**
     * Imports item count data from a JSON object
     */
    fun importItemCountsFromJson(jsonObject: JSONObject): Boolean {
        return try {
            lock.write {
                if (jsonObject.has(KEY_ITEM_COUNTS)) {
                    val countsArray = jsonObject.getJSONArray(KEY_ITEM_COUNTS)
                    val newItemCountMap = mutableMapOf<String, ItemCount>()
                    
                    for (i in 0 until countsArray.length()) {
                        val countObj = countsArray.getJSONObject(i)
                        val path = countObj.getString(JSON_KEY_PATH)
                        val count = countObj.getInt(JSON_KEY_COUNT)
                        val lastModified = countObj.getLong(JSON_KEY_LAST_MODIFIED)
                        newItemCountMap[path] = ItemCount(count, lastModified)
                    }
                    
                    itemCountMap.clear()
                    itemCountMap.putAll(newItemCountMap)
                    saveItemCounts()
                    _countChangedLiveData.value = Unit
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing item counts from JSON", e)
            false
        }
    }
    
    /**
     * Imports folder item counts from a file
     * 
     * @param file The file containing the exported data
     * @return true if import was successful, false otherwise
     */
    fun importItemCounts(file: File): Boolean {
        return try {
            val jsonObject = JSONObject(file.readText())
            importItemCountsFromJson(jsonObject)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing item counts", e)
            false
        }
    }
    
    private fun loadItemCounts() {
        lock.write {
            itemCountMap.clear()
            try {
                val countsJson = prefs.getString(KEY_ITEM_COUNTS, null) ?: return
                val jsonArray = JSONArray(countsJson)
                
                for (i in 0 until jsonArray.length()) {
                    val countObj = jsonArray.getJSONObject(i)
                    val path = countObj.getString(JSON_KEY_PATH)
                    val count = countObj.getInt(JSON_KEY_COUNT)
                    val lastModified = countObj.getLong(JSON_KEY_LAST_MODIFIED)
                    itemCountMap[path] = ItemCount(count, lastModified)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading item counts", e)
            }
        }
    }
    
    private fun saveItemCounts() {
        lock.read {
            try {
                val jsonArray = JSONArray()
                
                for ((path, itemCount) in itemCountMap) {
                    val countObj = JSONObject().apply {
                        put(JSON_KEY_PATH, path)
                        put(JSON_KEY_COUNT, itemCount.count)
                        put(JSON_KEY_LAST_MODIFIED, itemCount.lastModified)
                    }
                    jsonArray.put(countObj)
                }
                
                prefs.edit().putString(KEY_ITEM_COUNTS, jsonArray.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving item counts", e)
            }
        }
    }
} 