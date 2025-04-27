/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.valueCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FileRating(
    val path: String,
    val rating: Int
)

object FileRatingManager {
    private const val TAG = "FileRatingManager"
    private const val PREFS_NAME = "file_ratings"
    private const val KEY_RATINGS = "ratings"
    private const val JSON_KEY_PATH = "path"
    private const val JSON_KEY_RATING = "rating"
    
    private val lock = ReentrantReadWriteLock()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _ratingChangedLiveData = MutableLiveData<Unit>()
    val ratingChangedLiveData: LiveData<Unit>
        get() = _ratingChangedLiveData
    
    // Cache of file path to rating
    private val ratingMap = mutableMapOf<String, Int>()
    
    init {
        loadRatings()
    }
    
    fun getRating(path: Path): Int {
        lock.read {
            return ratingMap[path.toString()] ?: 0
        }
    }
    
    fun setRating(path: Path, rating: Int) {
        require(rating in 0..9) { "Rating must be between 0 and 9" }
        
        lock.write {
            if (rating == 0) {
                ratingMap.remove(path.toString())
            } else {
                ratingMap[path.toString()] = rating
            }
            saveRatings()
            _ratingChangedLiveData.value = Unit
        }
    }
    
    fun setRatingForFiles(paths: List<Path>, rating: Int) {
        require(rating in 0..9) { "Rating must be between 0 and 9" }
        
        lock.write {
            for (path in paths) {
                if (rating == 0) {
                    ratingMap.remove(path.toString())
                } else {
                    ratingMap[path.toString()] = rating
                }
            }
            saveRatings()
            _ratingChangedLiveData.value = Unit
        }
    }
    
    fun getAllRatings(): Map<String, Int> {
        lock.read {
            return ratingMap.toMap()
        }
    }
    
    /**
     * Updates file ratings when a file is moved or renamed
     */
    fun updatePathForFile(oldPath: Path, newPath: Path) {
        val oldPathKey = oldPath.toString()
        val newPathKey = newPath.toString()
        
        lock.write {
            // Move rating from old path to new path
            ratingMap[oldPathKey]?.let { rating ->
                Log.d(TAG, "Moving rating from $oldPathKey to $newPathKey")
                ratingMap[newPathKey] = rating
                ratingMap.remove(oldPathKey)
                saveRatings()
            }
        }
    }
    
    /**
     * Exports all rating data to a JSON object that can be included in the tag backup
     */
    fun exportRatingsToJson(): JSONObject {
        val jsonObject = JSONObject()
        
        lock.read {
            val ratingsArray = JSONArray()
            
            for ((path, rating) in ratingMap) {
                val ratingObj = JSONObject().apply {
                    put(JSON_KEY_PATH, path)
                    put(JSON_KEY_RATING, rating)
                }
                ratingsArray.put(ratingObj)
            }
            
            jsonObject.put(KEY_RATINGS, ratingsArray)
        }
        
        return jsonObject
    }
    
    /**
     * Imports rating data from a JSON object from the tag backup
     */
    fun importRatingsFromJson(jsonObject: JSONObject): Boolean {
        return try {
            lock.write {
                if (jsonObject.has(KEY_RATINGS)) {
                    val ratingsArray = jsonObject.getJSONArray(KEY_RATINGS)
                    val newRatingMap = mutableMapOf<String, Int>()
                    
                    for (i in 0 until ratingsArray.length()) {
                        val ratingObj = ratingsArray.getJSONObject(i)
                        val path = ratingObj.getString(JSON_KEY_PATH)
                        val rating = ratingObj.getInt(JSON_KEY_RATING)
                        newRatingMap[path] = rating
                    }
                    
                    ratingMap.clear()
                    ratingMap.putAll(newRatingMap)
                    saveRatings()
                    _ratingChangedLiveData.value = Unit
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing ratings from JSON", e)
            false
        }
    }
    
    private fun loadRatings() {
        lock.write {
            ratingMap.clear()
            try {
                val ratingsJson = prefs.getString(KEY_RATINGS, null) ?: return
                val jsonArray = JSONArray(ratingsJson)
                
                for (i in 0 until jsonArray.length()) {
                    val ratingObj = jsonArray.getJSONObject(i)
                    val path = ratingObj.getString(JSON_KEY_PATH)
                    val rating = ratingObj.getInt(JSON_KEY_RATING)
                    ratingMap[path] = rating
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ratings", e)
            }
        }
    }
    
    private fun saveRatings() {
        lock.read {
            try {
                val jsonArray = JSONArray()
                
                for ((path, rating) in ratingMap) {
                    val ratingObj = JSONObject().apply {
                        put(JSON_KEY_PATH, path)
                        put(JSON_KEY_RATING, rating)
                    }
                    jsonArray.put(ratingObj)
                }
                
                prefs.edit().putString(KEY_RATINGS, jsonArray.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving ratings", e)
            }
        }
    }
} 