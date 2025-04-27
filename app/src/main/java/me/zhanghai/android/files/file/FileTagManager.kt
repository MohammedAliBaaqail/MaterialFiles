/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object FileTagManager {
    private const val TAG = "FileTagManager"
    private const val PREFS_NAME = "file_tags"
    private const val KEY_TAGS = "tags"
    private const val KEY_FILE_TAGS = "file_tags"
    private const val KEY_TAG_ORDERS = "tag_orders"
    
    // JSON keys
    private const val JSON_KEY_ID = "id"
    private const val JSON_KEY_NAME = "name"
    private const val JSON_KEY_COLOR = "color"
    private const val JSON_KEY_TAG_IDS = "tagIds"
    private const val JSON_KEY_ORDER = "order"
    
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private var tags: MutableList<FileTag> = loadTags()
    private var fileTagsMap: MutableMap<String, MutableSet<String>> = loadFileTags()
    private var tagOrderMap: MutableMap<String, MutableMap<String, Int>> = loadTagOrders()
    
    fun getAllTags(): List<FileTag> = tags.toList()
    
    fun getTagById(id: String): FileTag? = tags.find { it.id == id }
    
    fun addTag(name: String, color: Int): FileTag {
        val id = UUID.randomUUID().toString()
        val tag = FileTag(id, name, color)
        tags.add(tag)
        saveTags()
        return tag
    }
    
    fun updateTag(tag: FileTag) {
        val index = tags.indexOfFirst { it.id == tag.id }
        if (index != -1) {
            tags[index] = tag
            saveTags()
        }
    }
    
    fun deleteTag(tagId: String) {
        tags.removeAll { it.id == tagId }
        
        // Remove this tag from all files
        fileTagsMap.forEach { (_, tagIds) ->
            tagIds.remove(tagId)
        }
        
        // Remove this tag from all order maps
        tagOrderMap.forEach { (_, orderMap) ->
            orderMap.remove(tagId)
        }
        
        saveTags()
        saveFileTags()
        saveTagOrders()
    }
    
    fun getTagsForFile(path: Path): List<FileTag> {
        val pathKey = path.toString()
        val tagIds = fileTagsMap[pathKey] ?: emptySet()
        val fileTags = tags.filter { it.id in tagIds }
        
        // Sort by custom order if available
        val orderMap = tagOrderMap[pathKey]
        return if (orderMap != null) {
            fileTags.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
        } else {
            fileTags
        }
    }
    
    fun addTagToFile(tagId: String, path: Path) {
        val pathKey = path.toString()
        val tagIds = fileTagsMap.getOrPut(pathKey) { mutableSetOf() }
        if (tagIds.add(tagId)) {
            // Add to order map with default position at the end
            val orderMap = tagOrderMap.getOrPut(pathKey) { mutableMapOf() }
            if (!orderMap.containsKey(tagId)) {
                orderMap[tagId] = orderMap.values.maxOrNull()?.plus(1) ?: 0
            }
            saveFileTags()
            saveTagOrders()
        }
    }
    
    fun removeTagFromFile(tagId: String, path: Path) {
        val pathKey = path.toString()
        val tagIds = fileTagsMap[pathKey] ?: return
        if (tagIds.remove(tagId)) {
            if (tagIds.isEmpty()) {
                fileTagsMap.remove(pathKey)
                tagOrderMap.remove(pathKey)
            } else {
                // Remove from order map
                tagOrderMap[pathKey]?.remove(tagId)
            }
            saveFileTags()
            saveTagOrders()
        }
    }
    
    fun setTagsForFile(tagIds: Set<String>, path: Path) {
        val pathKey = path.toString()
        if (tagIds.isEmpty()) {
            fileTagsMap.remove(pathKey)
            tagOrderMap.remove(pathKey)
        } else {
            fileTagsMap[pathKey] = tagIds.toMutableSet()
            
            // Ensure all tags have an order
            val orderMap = tagOrderMap.getOrPut(pathKey) { mutableMapOf() }
            tagIds.forEachIndexed { index, tagId ->
                if (!orderMap.containsKey(tagId)) {
                    orderMap[tagId] = index
                }
            }
            
            // Remove any tags from order map that are no longer associated with the file
            orderMap.keys.toList().forEach { id ->
                if (id !in tagIds) {
                    orderMap.remove(id)
                }
            }
        }
        saveFileTags()
        saveTagOrders()
    }
    
    fun hasTag(path: Path, tagId: String): Boolean {
        val pathKey = path.toString()
        return fileTagsMap[pathKey]?.contains(tagId) == true
    }
    
    /**
     * Updates the order of tags for a specific file
     */
    fun updateTagOrderForFile(tagIds: List<String>, path: Path) {
        val pathKey = path.toString()
        val orderMap = tagOrderMap.getOrPut(pathKey) { mutableMapOf() }
        
        // Update order for each tag
        tagIds.forEachIndexed { index, tagId ->
            orderMap[tagId] = index
        }
        
        saveTagOrders()
    }
    
    /**
     * Updates file tags when a file is moved or renamed
     */
    fun updatePathForFile(oldPath: Path, newPath: Path) {
        val oldPathKey = oldPath.toString()
        val newPathKey = newPath.toString()
        
        // Move tags from old path to new path
        fileTagsMap[oldPathKey]?.let { tagIds ->
            Log.d(TAG, "Moving tags from $oldPathKey to $newPathKey")
            fileTagsMap[newPathKey] = tagIds.toMutableSet()
            fileTagsMap.remove(oldPathKey)
            
            // Move order information too
            tagOrderMap[oldPathKey]?.let { orderMap ->
                tagOrderMap[newPathKey] = orderMap.toMutableMap()
                tagOrderMap.remove(oldPathKey)
            }
            
            saveFileTags()
            saveTagOrders()
        }
    }
    
    /**
     * Exports all tag data to a JSON file
     * 
     * @param file The destination file to write the exported data
     * @return true if export was successful, false otherwise
     */
    fun exportTags(file: File): Boolean {
        return try {
            val jsonObject = JSONObject()
            
            // Export tags
            val tagsJson = JSONArray()
            for (tag in tags) {
                val tagObj = JSONObject().apply {
                    put(JSON_KEY_ID, tag.id)
                    put(JSON_KEY_NAME, tag.name)
                    put(JSON_KEY_COLOR, tag.color)
                }
                tagsJson.put(tagObj)
            }
            jsonObject.put(KEY_TAGS, tagsJson)
            
            // Export file-tag associations
            val fileTagsJson = JSONObject()
            for ((path, tagIds) in fileTagsMap) {
                if (tagIds.isEmpty()) continue
                
                val tagIdsArray = JSONArray()
                tagIds.forEach { tagIdsArray.put(it) }
                
                fileTagsJson.put(path, tagIdsArray)
            }
            jsonObject.put(KEY_FILE_TAGS, fileTagsJson)
            
            // Export tag orders
            val tagOrdersJson = JSONObject()
            for ((pathKey, orderMap) in tagOrderMap) {
                if (orderMap.isEmpty()) continue
                
                val pathOrderObj = JSONObject()
                orderMap.forEach { (tagId, order) ->
                    pathOrderObj.put(tagId, order)
                }
                
                tagOrdersJson.put(pathKey, pathOrderObj)
            }
            jsonObject.put(KEY_TAG_ORDERS, tagOrdersJson)
            
            // Include ratings data from FileRatingManager
            val ratingsJson = FileRatingManager.exportRatingsToJson()
            for (key in ratingsJson.keys()) {
                jsonObject.put(key, ratingsJson.get(key))
            }
            
            // Write to file
            file.writeText(jsonObject.toString(2)) // Use indented format
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting tags", e)
            false
        }
    }
    
    /**
     * Imports all tag data from a JSON file
     * 
     * @param file The source file containing the exported data
     * @return true if import was successful, false otherwise
     */
    fun importTags(file: File): Boolean {
        return try {
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            
            // Import tags
            if (jsonObject.has(KEY_TAGS)) {
                val tagsArray = jsonObject.getJSONArray(KEY_TAGS)
                val newTags = mutableListOf<FileTag>()
                
                for (i in 0 until tagsArray.length()) {
                    val tagObj = tagsArray.getJSONObject(i)
                    newTags.add(
                        FileTag(
                            id = tagObj.getString(JSON_KEY_ID),
                            name = tagObj.getString(JSON_KEY_NAME),
                            color = tagObj.getInt(JSON_KEY_COLOR)
                        )
                    )
                }
                
                tags = newTags
                saveTags()
            }
            
            // Import file-tag associations
            if (jsonObject.has(KEY_FILE_TAGS)) {
                val fileTagsObj = jsonObject.getJSONObject(KEY_FILE_TAGS)
                val newFileTagsMap = mutableMapOf<String, MutableSet<String>>()
                
                for (key in fileTagsObj.keys()) {
                    val tagIdsArray = fileTagsObj.getJSONArray(key)
                    val tagIds = mutableSetOf<String>()
                    
                    for (i in 0 until tagIdsArray.length()) {
                        tagIds.add(tagIdsArray.getString(i))
                    }
                    
                    newFileTagsMap[key] = tagIds
                }
                
                fileTagsMap = newFileTagsMap
                saveFileTags()
            }
            
            // Import tag orders
            if (jsonObject.has(KEY_TAG_ORDERS)) {
                val tagOrdersObj = jsonObject.getJSONObject(KEY_TAG_ORDERS)
                val newTagOrderMap = mutableMapOf<String, MutableMap<String, Int>>()
                
                for (pathKey in tagOrdersObj.keys()) {
                    val pathOrderObj = tagOrdersObj.getJSONObject(pathKey)
                    val orderMap = mutableMapOf<String, Int>()
                    
                    for (tagId in pathOrderObj.keys()) {
                        orderMap[tagId] = pathOrderObj.getInt(tagId)
                    }
                    
                    newTagOrderMap[pathKey] = orderMap
                }
                
                tagOrderMap = newTagOrderMap
                saveTagOrders()
            }
            
            // Import ratings data
            FileRatingManager.importRatingsFromJson(jsonObject)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing tags", e)
            false
        }
    }
    
    private fun loadTags(): MutableList<FileTag> {
        val tagsJson = prefs.getString(KEY_TAGS, null) ?: return mutableListOf()
        
        return try {
            val jsonArray = JSONArray(tagsJson)
            val list = mutableListOf<FileTag>()
            
            for (i in 0 until jsonArray.length()) {
                val tagObj = jsonArray.getJSONObject(i)
                list.add(
                    FileTag(
                        id = tagObj.getString(JSON_KEY_ID),
                        name = tagObj.getString(JSON_KEY_NAME),
                        color = tagObj.getInt(JSON_KEY_COLOR)
                    )
                )
            }
            
            list
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading tags", e)
            mutableListOf()
        }
    }
    
    private fun saveTags() {
        val jsonArray = JSONArray()
        
        for (tag in tags) {
            val tagObj = JSONObject().apply {
                put(JSON_KEY_ID, tag.id)
                put(JSON_KEY_NAME, tag.name)
                put(JSON_KEY_COLOR, tag.color)
            }
            jsonArray.put(tagObj)
        }
        
        prefs.edit {
            putString(KEY_TAGS, jsonArray.toString())
        }
    }
    
    private fun loadFileTags(): MutableMap<String, MutableSet<String>> {
        val fileTagsJson = prefs.getString(KEY_FILE_TAGS, null) ?: return mutableMapOf()
        
        return try {
            val jsonObj = JSONObject(fileTagsJson)
            val map = mutableMapOf<String, MutableSet<String>>()
            
            for (key in jsonObj.keys()) {
                val tagIdsArray = jsonObj.getJSONArray(key)
                val tagIds = mutableSetOf<String>()
                
                for (i in 0 until tagIdsArray.length()) {
                    tagIds.add(tagIdsArray.getString(i))
                }
                
                map[key] = tagIds
            }
            
            map
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading file tags", e)
            mutableMapOf()
        }
    }
    
    private fun saveFileTags() {
        val jsonObj = JSONObject()
        
        for ((path, tagIds) in fileTagsMap) {
            if (tagIds.isEmpty()) continue
            
            val tagIdsArray = JSONArray()
            tagIds.forEach { tagIdsArray.put(it) }
            
            jsonObj.put(path, tagIdsArray)
        }
        
        prefs.edit {
            putString(KEY_FILE_TAGS, jsonObj.toString())
        }
    }
    
    private fun loadTagOrders(): MutableMap<String, MutableMap<String, Int>> {
        val tagOrdersJson = prefs.getString(KEY_TAG_ORDERS, null) ?: return mutableMapOf()
        
        return try {
            val jsonObj = JSONObject(tagOrdersJson)
            val map = mutableMapOf<String, MutableMap<String, Int>>()
            
            for (pathKey in jsonObj.keys()) {
                val pathOrderObj = jsonObj.getJSONObject(pathKey)
                val orderMap = mutableMapOf<String, Int>()
                
                for (tagId in pathOrderObj.keys()) {
                    orderMap[tagId] = pathOrderObj.getInt(tagId)
                }
                
                map[pathKey] = orderMap
            }
            
            map
        } catch (e: JSONException) {
            Log.e(TAG, "Error loading tag orders", e)
            mutableMapOf()
        }
    }
    
    private fun saveTagOrders() {
        val jsonObj = JSONObject()
        
        for ((pathKey, orderMap) in tagOrderMap) {
            if (orderMap.isEmpty()) continue
            
            val pathOrderObj = JSONObject()
            orderMap.forEach { (tagId, order) ->
                pathOrderObj.put(tagId, order)
            }
            
            jsonObj.put(pathKey, pathOrderObj)
        }
        
        prefs.edit {
            putString(KEY_TAG_ORDERS, jsonObj.toString())
        }
    }
} 