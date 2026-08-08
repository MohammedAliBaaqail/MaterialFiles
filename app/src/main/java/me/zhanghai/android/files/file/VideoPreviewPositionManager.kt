/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import me.zhanghai.android.files.app.application
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object VideoPreviewPositionManager {
    private const val TAG = "VideoPreviewPositionManager"
    private const val PREFS_NAME = "video_preview_positions"

    private val lock = ReentrantReadWriteLock()
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val positionMap = mutableMapOf<String, Int>()

    init {
        loadPositions()
    }

    fun getPosition(pathString: String): Int {
        lock.read {
            return positionMap[pathString] ?: 0
        }
    }

    fun setPosition(pathString: String, positionMs: Int) {
        if (pathString.isEmpty() || positionMs < 0) return
        lock.write {
            positionMap[pathString] = positionMs
            prefs.edit { putInt(pathString, positionMs) }
        }
    }

    private fun loadPositions() {
        lock.write {
            positionMap.clear()
            try {
                for ((key, value) in prefs.all) {
                    if (value is Int) {
                        positionMap[key] = value
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading video positions", e)
            }
        }
    }
}
