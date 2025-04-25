package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.util.show

class FileTagFilterDialog : DialogFragment() {
    private lateinit var listener: Listener
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TagAdapter
    private var selectedTags = mutableSetOf<FileTag>()
    private var isMatchAll = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_filter_dialog, null)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = TagAdapter(FileTagManager.getAllTags(), selectedTags) { tag, isChecked ->
            if (isChecked) {
                selectedTags.add(tag)
            } else {
                selectedTags.remove(tag)
            }
        }
        recyclerView.adapter = adapter
        
        // Set up filter mode radio buttons
        val filterModeGroup = view.findViewById<RadioGroup>(R.id.filterModeGroup)
        val filterModeAny = view.findViewById<RadioButton>(R.id.filterModeAny)
        val filterModeAll = view.findViewById<RadioButton>(R.id.filterModeAll)
        
        if (isMatchAll) {
            filterModeAll.isChecked = true
        } else {
            filterModeAny.isChecked = true
        }
        
        filterModeGroup.setOnCheckedChangeListener { _, checkedId ->
            isMatchAll = checkedId == R.id.filterModeAll
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.file_tag_filter_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                listener.onTagFilterChanged(selectedTags, isMatchAll)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private inner class TagAdapter(
        private val allTags: List<FileTag>,
        private val selectedTags: Set<FileTag>,
        private val onTagCheckedChange: (FileTag, Boolean) -> Unit
    ) : RecyclerView.Adapter<TagAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tag_filter, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = allTags[position]
            holder.bind(tag, tag in selectedTags)
        }

        override fun getItemCount(): Int = allTags.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
            private val tagText: TextView = itemView.findViewById(R.id.tagText)
            private val tagContainer: View = itemView.findViewById(R.id.tagContainer)

            fun bind(tag: FileTag, isChecked: Boolean) {
                tagText.text = tag.name
                checkBox.isChecked = isChecked
                tagContainer.setBackgroundColor(tag.color)

                checkBox.setOnCheckedChangeListener { _, checked ->
                    onTagCheckedChange(tag, checked)
                }

                itemView.setOnClickListener {
                    checkBox.toggle()
                }
            }
        }
    }

    interface Listener {
        fun onTagFilterChanged(selectedTags: Set<FileTag>, matchAll: Boolean)
    }

    companion object {
        fun show(fragment: Fragment, currentTags: Set<FileTag>, isMatchAll: Boolean) {
            FileTagFilterDialog().apply {
                listener = fragment as Listener
                selectedTags.addAll(currentTags)
                this.isMatchAll = isMatchAll
            }.show(fragment)
        }
    }
} 