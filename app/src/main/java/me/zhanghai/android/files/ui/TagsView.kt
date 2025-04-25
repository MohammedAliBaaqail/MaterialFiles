package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag

/**
 * A view that displays file tags in a horizontal scrollable container.
 * Shows tags in the order provided, which corresponds to the custom order 
 * defined in FileTagManager for each file.
 */
class TagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout
    private var onTagClickListener: ((FileTag) -> Unit)? = null
    private var isFilterView: Boolean = false

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        addView(container)
    }

    fun setOnTagClickListener(listener: (FileTag) -> Unit) {
        onTagClickListener = listener
    }
    
    fun setAsFilterView(isFilterView: Boolean) {
        this.isFilterView = isFilterView
    }

    /**
     * Sets the tags to display in the specified order.
     * The order of tags in the list will be preserved in the UI.
     */
    fun setTags(tags: List<FileTag>) {
        container.removeAllViews()
        
        tags.forEach { tag ->
            if (isFilterView) {
                // Create filter tag view
                val tagView = LayoutInflater.from(context)
                    .inflate(R.layout.filter_tag_item, container, false)
                
                tagView.findViewById<TextView>(R.id.tagText).text = tag.name
                tagView.setBackgroundColor(tag.color)
                
                // Set clickable on entire view
                tagView.isClickable = true
                tagView.isFocusable = true
                tagView.setOnClickListener {
                    onTagClickListener?.invoke(tag)
                }
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 4
                }
                container.addView(tagView, params)
            } else {
                // Regular tag view
                val tagView = LayoutInflater.from(context)
                    .inflate(R.layout.tag_item, container, false) as TextView
                
                tagView.text = tag.name
                tagView.setBackgroundColor(tag.color)
                
                // Make tag clickable
                tagView.isClickable = true
                tagView.isFocusable = true
                tagView.setOnClickListener {
                    onTagClickListener?.invoke(tag)
                }
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 4
                }
                container.addView(tagView, params)
            }
        }

        visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    }
} 