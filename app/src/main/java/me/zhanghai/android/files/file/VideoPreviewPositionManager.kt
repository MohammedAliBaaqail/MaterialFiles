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
    private const val KEY_LAST_SPEED = "__last_preview_speed__"
    private const val KEY_MIN_SPEED = "__min_preview_speed__"
    private const val KEY_MAX_SPEED = "__max_preview_speed__"
    private const val KEY_FORCE_FULL_SCREEN_MEDIA = "__force_full_screen_media__"

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

    /** Round a speed float to the nearest 0.1 to avoid RangeSlider stepSize validation errors. */
    private fun roundSpeed(speed: Float): Float = Math.round(speed * 10) / 10.0f

    fun getMinSpeed(): Float {
        lock.read {
            val raw = prefs.getFloat(KEY_MIN_SPEED, 0.1f).coerceIn(0.1f, 6.0f)
            return roundSpeed(raw)
        }
    }

    fun setMinSpeed(speed: Float) {
        val coerced = roundSpeed(speed.coerceIn(0.1f, 6.0f))
        lock.write {
            prefs.edit { putFloat(KEY_MIN_SPEED, coerced) }
        }
    }

    fun getMaxSpeed(): Float {
        lock.read {
            val raw = prefs.getFloat(KEY_MAX_SPEED, 6.0f).coerceIn(0.1f, 6.0f)
            return roundSpeed(raw)
        }
    }

    fun setMaxSpeed(speed: Float) {
        val coerced = roundSpeed(speed.coerceIn(0.1f, 6.0f))
        lock.write {
            prefs.edit { putFloat(KEY_MAX_SPEED, coerced) }
        }
    }

    fun getLastSpeed(): Float {
        lock.read {
            val raw = prefs.getFloat(KEY_LAST_SPEED, 2.0f).coerceIn(0.1f, 6.0f)
            return roundSpeed(raw)
        }
    }

    fun setLastSpeed(speed: Float) {
        val rounded = roundSpeed(speed)
        if (rounded < 0.1f || rounded > 6.0f) return
        lock.write {
            prefs.edit { putFloat(KEY_LAST_SPEED, rounded) }
        }
    }

    fun getForceFullScreenMedia(): Boolean {
        lock.read {
            return prefs.getBoolean(KEY_FORCE_FULL_SCREEN_MEDIA, false)
        }
    }

    fun setForceFullScreenMedia(enabled: Boolean) {
        lock.write {
            prefs.edit { putBoolean(KEY_FORCE_FULL_SCREEN_MEDIA, enabled) }
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
