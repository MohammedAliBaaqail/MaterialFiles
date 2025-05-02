/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.util

import android.util.Log

/**
 * Utility to suppress common MediaMetadataRetriever errors in logs
 */
object MediaLogger {
    private const val TAG = "MediaMetadataRetriever"
    private val SUPPRESSED_ERROR_MESSAGES = setOf(
        "getEmbeddedPicture: Call to getEmbeddedPicture failed."
    )
    
    /**
     * Logs exceptions but suppresses known harmless errors to reduce log spam
     */
    fun logException(e: Exception) {
        val message = e.message ?: ""
        if (!SUPPRESSED_ERROR_MESSAGES.any { message.contains(it) }) {
            Log.e(TAG, "Error retrieving media metadata", e)
        }
    }
} 