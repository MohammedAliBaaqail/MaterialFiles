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

        // Handle grid layout case - if height is exactly specified, calculate width to maintain aspect ratio
        // This is critical for proper scaling behavior in grid view
        if (heightMode == View.MeasureSpec.EXACTLY) {
            val calculatedWidth = (height * ratio).roundToInt()
            
            // If width is also specified exactly, we need to decide how to maintain aspect ratio
            if (widthMode == View.MeasureSpec.EXACTLY) {
                // If the width is significantly different from what we'd calculate based on height and ratio,
                // use the aspect ratio to calculate our dimensions while respecting at least one constraint
                if (Math.abs(calculatedWidth - width) > width * 0.1) {
                    // If calculated width would be too large, constrain to available width and adjust height
                    if (calculatedWidth > width) {
                        val adjustedHeight = (width / ratio).roundToInt()
                        super.onMeasure(
                            widthMeasureSpec,
                            View.MeasureSpec.makeMeasureSpec(adjustedHeight, View.MeasureSpec.EXACTLY)
                        )
                        return
                    } else {
                        // Width would be smaller than available - maintain aspect ratio using height as base
                        super.onMeasure(
                            View.MeasureSpec.makeMeasureSpec(calculatedWidth, View.MeasureSpec.EXACTLY),
                            heightMeasureSpec
                        )
                        return
                    }
                }
                // If the difference is small, just use the exact dimensions
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            } else {
                // Width is flexible, use height to determine width based on aspect ratio
                val finalWidth = if (widthMode == View.MeasureSpec.AT_MOST) {
                    min(calculatedWidth, width)
                } else {
                    calculatedWidth
                }
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(finalWidth, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
                return
            }
        }
        
        // Width is exactly specified but height is flexible
        if (widthMode == View.MeasureSpec.EXACTLY) {
            val calculatedHeight = (width / ratio).roundToInt()
            val finalHeight = if (heightMode == View.MeasureSpec.AT_MOST) {
                min(calculatedHeight, height)
        } else {
                calculatedHeight
            }
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(finalHeight, View.MeasureSpec.EXACTLY)
            )
            return
        }

        // Both dimensions are flexible
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
