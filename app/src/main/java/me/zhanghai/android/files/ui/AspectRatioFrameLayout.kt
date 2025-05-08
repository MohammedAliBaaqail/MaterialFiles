/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.obtainStyledAttributesCompat
import me.zhanghai.android.files.compat.use
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    var ratio: Float = 1f
        set(value) {
            if (field != value) {
            field = value
            requestLayout()
            invalidate()
        }
        }

    init {
        context.obtainStyledAttributesCompat(
            attrs, R.styleable.AspectRatioFrameLayout, defStyleAttr, defStyleRes
        ).use { attributes ->
            ratio = attributes.getFloat(R.styleable.AspectRatioFrameLayout_aspectRatio, 1f)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)

        // If ratio is 0 or negative, let the content determine its natural size
        if (ratio <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // If both dimensions are fixed, just use the available space
        if (widthMode == View.MeasureSpec.EXACTLY && heightMode == View.MeasureSpec.EXACTLY) {
            // Both dimensions are fixed - just accept it and don't log warnings
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        
        // If width is exactly specified, calculate height based on ratio
        if (widthMode == View.MeasureSpec.EXACTLY) {
            val desiredHeight = (width / ratio).roundToInt()
            
            // Check if we're constrained by max height
            val finalHeight = if (heightMode == View.MeasureSpec.AT_MOST && desiredHeight > height) {
                // We're constrained by maximum height
                height
            } else {
                desiredHeight
            }
            
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(finalHeight, View.MeasureSpec.EXACTLY)
            )
            return
        }
        
        // If height is exactly specified, calculate width based on ratio
        if (heightMode == View.MeasureSpec.EXACTLY) {
            val desiredWidth = (height * ratio).roundToInt()
            
            // Check if we're constrained by max width
            val finalWidth = if (widthMode == View.MeasureSpec.AT_MOST && desiredWidth > width) {
                // We're constrained by maximum width
                width
            } else {
                desiredWidth
            }
            
            super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(finalWidth, View.MeasureSpec.EXACTLY),
                heightMeasureSpec
            )
            return
        }

        // Both dimensions are flexible, use a reasonable default size while maintaining ratio
        val defaultSize = max(width, 100)
        super.onMeasure(
            if (widthMode == View.MeasureSpec.AT_MOST) 
                View.MeasureSpec.makeMeasureSpec(min(defaultSize, width), View.MeasureSpec.EXACTLY)
            else 
                View.MeasureSpec.makeMeasureSpec(defaultSize, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((defaultSize / ratio).roundToInt(), View.MeasureSpec.EXACTLY)
        )
    }
}
