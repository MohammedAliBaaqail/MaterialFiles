package me.zhanghai.android.files.filelist

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileTag(
    val name: String,
    val color: String // Hex color code in format "#RRGGBB"
) : Parcelable {
    companion object {
        fun generateRandomColor(): String {
            val colors = arrayOf(
                "#F44336", // Red
                "#E91E63", // Pink
                "#9C27B0", // Purple
                "#673AB7", // Deep Purple
                "#3F51B5", // Indigo
                "#2196F3", // Blue
                "#03A9F4", // Light Blue
                "#00BCD4", // Cyan
                "#009688", // Teal
                "#4CAF50", // Green
                "#8BC34A", // Light Green
                "#CDDC39", // Lime
                "#FFEB3B", // Yellow
                "#FFC107", // Amber
                "#FF9800", // Orange
                "#FF5722"  // Deep Orange
            )
            return colors.random()
        }
    }
} 