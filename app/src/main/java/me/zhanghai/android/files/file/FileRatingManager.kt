/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.util.valueCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FileRating(
    val path: String,
    val rating: Int
)

object FileRatingManager {
    private const val FILE_NAME = "file_ratings.json"
    private val lock = ReentrantReadWriteLock()
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
    
    private fun loadRatings() {
        lock.write {
            ratingMap.clear()
            val file = getFile()
            if (!file.exists()) {
                return
            }
            
            try {
                FileInputStream(file).use { inputStream ->
                    val ratingsJson = inputStream.bufferedReader().use { it.readText() }
                    val ratings = json.decodeFromString<List<FileRating>>(ratingsJson)
                    
                    for (fileRating in ratings) {
                        ratingMap[fileRating.path] = fileRating.rating
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun saveRatings() {
        lock.read {
            val file = getFile()
            try {
                val ratings = ratingMap.map { (path, rating) ->
                    FileRating(path, rating)
                }
                
                val ratingsJson = json.encodeToString(ratings)
                FileOutputStream(file).use { outputStream ->
                    outputStream.bufferedWriter().use { it.write(ratingsJson) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getFile(): File {
        val directory = application.getDir("file_data", Context.MODE_PRIVATE)
        return File(directory, FILE_NAME)
    }
} 