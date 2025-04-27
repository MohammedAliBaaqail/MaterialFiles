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
                Log.d("AspectRatioFrameLayout", "Setting ratio to $value")
            requestLayout()
            invalidate()
        }
        }

    init {
        context.obtainStyledAttributesCompat(
            attrs, R.styleable.AspectRatioFrameLayout, defStyleAttr, defStyleRes
        ).use { attributes ->
            ratio = attributes.getFloat(R.styleable.AspectRatioFrameLayout_aspectRatio, 1f)
            Log.d("AspectRatioFrameLayout", "Init with ratio $ratio")
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)

        // For video thumbnails, we want to use the height to calculate width 
        // to honor the 16:9 aspect ratio
        if (widthMode == View.MeasureSpec.AT_MOST || widthMode == View.MeasureSpec.UNSPECIFIED) {
            if (heightMode == View.MeasureSpec.EXACTLY) {
                // Use height to determine width
                val calculatedWidth = (height * ratio).roundToInt()
                val finalWidth = if (widthMode == View.MeasureSpec.AT_MOST) {
                    min(calculatedWidth, width)
            } else {
                    calculatedWidth
                }
                Log.d("AspectRatioFrameLayout", "onMeasure: height=$height, calculated width=$finalWidth using ratio=$ratio")
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(finalWidth, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
                return
            }
        }

        // Normal flow for other cases
        if (heightMode == View.MeasureSpec.AT_MOST || heightMode == View.MeasureSpec.UNSPECIFIED) {
            if (widthMode == View.MeasureSpec.EXACTLY) {
                val calculatedHeight = (width / ratio).roundToInt()
                val finalHeight = if (heightMode == View.MeasureSpec.AT_MOST) {
                    min(calculatedHeight, height)
        } else {
                    calculatedHeight
                }
                Log.d("AspectRatioFrameLayout", "onMeasure: width=$width, calculated height=$finalHeight using ratio=$ratio")
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(finalHeight, View.MeasureSpec.EXACTLY)
                )
                return
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
