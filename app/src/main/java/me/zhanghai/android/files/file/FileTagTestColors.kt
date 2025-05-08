package me.zhanghai.android.files.file

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import me.zhanghai.android.files.util.ColorUtils

/**
 * A test class to verify our contrast-based tag colors implementation.
 * This is just for testing purposes.
 */
object FileTagTestColors {
    /**
     * Tests if contrast-based text colors work correctly.
     */
    fun testContrast(context: Context) {
        // Test colors: white, black, light gray, dark gray, red, green, blue, yellow
        val testColors = listOf(
            Color.WHITE,                  // Should produce dark text
            Color.BLACK,                  // Should produce white text
            Color.parseColor("#DDDDDD"),  // Light gray - dark text
            Color.parseColor("#222222"),  // Dark gray - white text
            Color.parseColor("#FF0000"),  // Red - could be either
            Color.parseColor("#00FF00"),  // Green - dark text
            Color.parseColor("#0000FF"),  // Blue - white text
            Color.parseColor("#FFFF00")   // Yellow - dark text
        )
        
        // Check text colors for each background
        testColors.forEach { bgColor ->
            val textColor = ColorUtils.getContrastingTextColor(bgColor)
            val isLight = ColorUtils.isLightColor(bgColor)
            
            // If background is light, text should be dark (black), and vice versa
            if (isLight) {
                assert(Color.red(textColor) < 128 && Color.green(textColor) < 128 && Color.blue(textColor) < 128)
            } else {
                assert(Color.red(textColor) > 128 && Color.green(textColor) > 128 && Color.blue(textColor) > 128)
            }
        }
    }
} 