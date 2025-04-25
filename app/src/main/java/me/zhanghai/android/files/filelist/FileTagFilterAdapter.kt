package me.zhanghai.android.files.filelist

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag

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

        fun bind(tag: FileTag) {
            checkBox.text = tag.name
            checkBox.isChecked = selectedTags.contains(tag)
            checkBox.buttonTintList = ColorStateList.valueOf(tag.color)
            
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTags.add(tag)
                } else {
                    selectedTags.remove(tag)
                }
                onTagSelectionChanged(selectedTags)
            }
        }
    }
} 