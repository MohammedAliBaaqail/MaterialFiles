package me.zhanghai.android.files.util

import android.graphics.Color
import kotlin.math.max

/**
 * Utility methods for working with colors in the app.
 */
object ColorUtils {
    /**
     * Determines if a color is light or dark.
     * Based on the formula from W3C accessibility guidelines.
     * @param color The color to check
     * @return true if the color is light, false if it is dark
     */
    fun isLightColor(color: Int): Boolean {
        // Extract color components
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        
        // Calculate luminance using the formula from W3C
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        
        // Threshold for light/dark determination (0.5 is middle, but we go slightly higher for better results)
        return luminance > 0.6
    }
    
    /**
     * Creates a contrasting text color based on the background color.
     * For light backgrounds, returns semi-transparent black.
     * For dark backgrounds, returns semi-transparent white.
     * @param backgroundColor The background color
     * @param alpha The alpha value to apply (0-255)
     * @return A contrasting text color with the specified alpha
     */
    fun getContrastingTextColor(backgroundColor: Int, alpha: Int = 255): Int {
        return if (isLightColor(backgroundColor)) {
            Color.argb(alpha, 0, 0, 0)
        } else {
            Color.argb(alpha, 255, 255, 255)
        }
    }
    
    /**
     * Creates a border color based on the text color.
     * @param textColor The text color
     * @param alpha The alpha value for the border (0-255)
     * @return A border color matching the text color with the specified alpha
     */
    fun getBorderColorFromText(textColor: Int, alpha: Int = 102): Int {
        return Color.argb(
            alpha,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )
    }
} 