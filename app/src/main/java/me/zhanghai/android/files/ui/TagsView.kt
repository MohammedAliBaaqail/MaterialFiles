package me.zhanghai.android.files.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.util.ColorUtils

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
                
                val tagText = tagView.findViewById<TextView>(R.id.tagText)
                tagText.text = tag.name
                
                // Set tag background color
                val backgroundColor = tag.color
                
                // Get contrasting text color with 40% opacity (102/255)
                val textColor = ColorUtils.getContrastingTextColor(backgroundColor, 180) // ~85% opacity
                tagText.setTextColor(textColor)
                
                // Create a drawable with border that matches text color
                val borderColor = ColorUtils.getBorderColorFromText(textColor)
                
                // Apply the background with border
                val backgroundDrawable = ContextCompat.getDrawable(
                    context, R.drawable.tag_background_with_border
                )?.mutate() as GradientDrawable
                
                backgroundDrawable.setColor(backgroundColor)
                backgroundDrawable.setStroke(context.resources.getDimensionPixelSize(R.dimen.tag_border_width), borderColor)
                tagView.background = backgroundDrawable
                
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
                
                // Set tag background color
                val backgroundColor = tag.color
                
                // Get contrasting text color with ~85% opacity (217/255)
                val textColor = ColorUtils.getContrastingTextColor(backgroundColor, 180)
                tagView.setTextColor(textColor)
                
                // Create a drawable with border that matches text color
                val borderColor = ColorUtils.getBorderColorFromText(textColor)
                
                // Apply the background with border
                val backgroundDrawable = ContextCompat.getDrawable(
                    context, R.drawable.tag_background_with_border
                )?.mutate() as GradientDrawable
                
                backgroundDrawable.setColor(backgroundColor)
                backgroundDrawable.setStroke(context.resources.getDimensionPixelSize(R.dimen.tag_border_width), borderColor)
                tagView.background = backgroundDrawable
                
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