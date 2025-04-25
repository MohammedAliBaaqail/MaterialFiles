package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag

class TagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
        }
        addView(container)
    }

    fun setTags(tags: List<FileTag>) {
        container.removeAllViews()
        
        tags.forEach { tag ->
            val tagView = LayoutInflater.from(context)
                .inflate(R.layout.tag_item, container, false) as TextView
            
            tagView.text = tag.name
            tagView.setBackgroundColor(tag.color)
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
            container.addView(tagView, params)
        }

        visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    }
} 