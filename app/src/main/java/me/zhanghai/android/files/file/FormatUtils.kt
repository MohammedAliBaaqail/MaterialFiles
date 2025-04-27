/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.getQuantityString
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Format a duration in milliseconds to a string as "mm:ss" or "h:mm:ss"
 */
fun Long.format(): String {
    if (this <= 0) return "0:00"
    
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
} 