package me.zhanghai.android.files.util

import android.content.Context
import android.util.TypedValue
import java.util.concurrent.TimeUnit

/**
 * Converts the given dp value to pixels.
 */
val Int.dp: Int
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        android.content.res.Resources.getSystem().displayMetrics
    ).toInt()

/**
 * Converts the given dp value to pixels.
 */
val Float.dp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        android.content.res.Resources.getSystem().displayMetrics
    )

/**
 * Formats the given duration in milliseconds to a human-readable string.
 * Format: "HH:MM:SS" or "MM:SS" if duration is less than an hour.
 */
fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
