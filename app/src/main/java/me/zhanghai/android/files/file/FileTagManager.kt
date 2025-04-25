/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object FileTagManager {
    private const val PREFS_NAME = "file_tags"
    private const val KEY_TAGS = "tags"
    private const val KEY_FILE_TAGS = "file_tags"
    
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private var tags: MutableList<FileTag> = loadTags()
    private var fileTagsMap: MutableMap<String, MutableSet<String>> = loadFileTags()
    
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
        
        saveTags()
        saveFileTags()
    }
    
    fun getTagsForFile(path: Path): List<FileTag> {
        val pathKey = path.toString()
        val tagIds = fileTagsMap[pathKey] ?: emptySet()
        return tags.filter { it.id in tagIds }
    }
    
    fun addTagToFile(tagId: String, path: Path) {
        val pathKey = path.toString()
        val tagIds = fileTagsMap.getOrPut(pathKey) { mutableSetOf() }
        if (tagIds.add(tagId)) {
            saveFileTags()
        }
    }
    
    fun removeTagFromFile(tagId: String, path: Path) {
        val pathKey = path.toString()
        val tagIds = fileTagsMap[pathKey] ?: return
        if (tagIds.remove(tagId)) {
            if (tagIds.isEmpty()) {
                fileTagsMap.remove(pathKey)
            }
            saveFileTags()
        }
    }
    
    fun setTagsForFile(tagIds: Set<String>, path: Path) {
        val pathKey = path.toString()
        if (tagIds.isEmpty()) {
            fileTagsMap.remove(pathKey)
        } else {
            fileTagsMap[pathKey] = tagIds.toMutableSet()
        }
        saveFileTags()
    }
    
    fun hasTag(path: Path, tagId: String): Boolean {
        val pathKey = path.toString()
        return fileTagsMap[pathKey]?.contains(tagId) == true
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
                        id = tagObj.getString("id"),
                        name = tagObj.getString("name"),
                        color = tagObj.getInt("color")
                    )
                )
            }
            
            list
        } catch (e: JSONException) {
            mutableListOf()
        }
    }
    
    private fun saveTags() {
        val jsonArray = JSONArray()
        
        for (tag in tags) {
            val tagObj = JSONObject().apply {
                put("id", tag.id)
                put("name", tag.name)
                put("color", tag.color)
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
} 