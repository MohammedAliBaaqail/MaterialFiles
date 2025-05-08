package me.zhanghai.android.files.filelist

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.util.ColorUtils

class FileTagFilterAdapter(
    private val onTagSelectionChanged: (Set<FileTag>) -> Unit
) : RecyclerView.Adapter<FileTagFilterAdapter.ViewHolder>() {
    private var tags: List<FileTag> = emptyList()
    private val selectedTags = mutableSetOf<FileTag>()

    fun setTags(newTags: List<FileTag>) {
        tags = newTags
        notifyDataSetChanged()
    }

    fun getSelectedTags(): Set<FileTag> = selectedTags.toSet()

    fun setSelectedTags(tags: Set<FileTag>) {
        selectedTags.clear()
        selectedTags.addAll(tags)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_filter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = tags[position]
        holder.bind(tag)
    }

    override fun getItemCount(): Int = tags.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
        private val tagText: TextView = itemView.findViewById(R.id.tagText)
        private val tagContainer: View = itemView.findViewById(R.id.tagContainer)

        fun bind(tag: FileTag) {
            val isSelected = selectedTags.contains(tag)
            checkBox.isChecked = isSelected
            tagText.text = tag.name

            // Apply background color from tag
            val backgroundColor = tag.color
            
            // Get contrasting text color with ~85% opacity (217/255)
            val textColor = ColorUtils.getContrastingTextColor(backgroundColor, 217)
            tagText.setTextColor(textColor)
            
            // Create a drawable with border that matches text color
            val borderColor = ColorUtils.getBorderColorFromText(textColor)
            
            // Apply the background with border
            val backgroundDrawable = ContextCompat.getDrawable(
                itemView.context, R.drawable.tag_background_with_border
            )?.mutate() as GradientDrawable
            
            backgroundDrawable.setColor(backgroundColor)
            backgroundDrawable.setStroke(
                itemView.context.resources.getDimensionPixelSize(R.dimen.tag_border_width), 
                borderColor
            )
            tagContainer.background = backgroundDrawable

            itemView.setOnClickListener {
                val newSelectedState = !isSelected
                if (newSelectedState) {
                    selectedTags.add(tag)
                } else {
                    selectedTags.remove(tag)
                }
                checkBox.isChecked = newSelectedState
                onTagSelectionChanged(selectedTags)
            }
        }
    }
} 