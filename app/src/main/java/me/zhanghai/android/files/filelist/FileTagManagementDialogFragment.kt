package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.colorpicker.ColorPickerDialog
import me.zhanghai.android.files.util.show

class FileTagManagementDialogFragment : DialogFragment() {
    private lateinit var files: List<FileItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TagAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = arguments?.getParcelableArrayList(EXTRA_FILES) ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_management_dialog, null)
        
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = TagAdapter(
            FileTagManager.getAllTags().toMutableList(),
            files.flatMap { FileTagManager.getTagsForFile(it.path) }.toSet()
        ) { tag, isChecked ->
            files.forEach { file ->
                if (isChecked) {
                    FileTagManager.addTagToFile(tag.id, file.path)
                } else {
                    FileTagManager.removeTagFromFile(tag.id, file.path)
                }
            }
        }
        recyclerView.adapter = adapter

        return MaterialAlertDialogBuilder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.file_tag_management_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.file_tag_add_new) { _, _ ->
                showAddTagDialog()
            }
            .create()
    }

    private fun showAddTagDialog() {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_add_dialog, null)
        val nameEdit = view.findViewById<EditText>(R.id.nameEdit)
        var selectedColor = FileTag.generateRandomColor()
        val colorButton = view.findViewById<ImageButton>(R.id.colorButton)
        colorButton.setBackgroundColor(selectedColor)
        colorButton.setOnClickListener {
            ColorPickerDialog.show(this) { color ->
                selectedColor = color
                colorButton.setBackgroundColor(color)
            }
        }

        MaterialAlertDialogBuilder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.file_tag_add_title)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isNotEmpty()) {
                    val tag = FileTagManager.addTag(name, selectedColor)
                    adapter.addTag(tag)
                    files.forEach { file ->
                        FileTagManager.addTagToFile(tag.id, file.path)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_FILES = "files"

        fun show(files: List<FileItem>, fragment: androidx.fragment.app.Fragment) {
            FileTagManagementDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(EXTRA_FILES, ArrayList(files))
                }
            }.show(fragment)
        }
    }

    private class TagAdapter(
        private val allTags: MutableList<FileTag>,
        private val selectedTags: Set<FileTag>,
        private val onTagCheckedChange: (FileTag, Boolean) -> Unit
    ) : RecyclerView.Adapter<TagAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.file_tag_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = allTags[position]
            holder.bind(tag, tag in selectedTags)
        }

        override fun getItemCount(): Int = allTags.size

        fun addTag(tag: FileTag) {
            allTags.add(tag)
            notifyItemInserted(allTags.size - 1)
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
            private val tagText: TextView = itemView.findViewById(R.id.tagText)
            private val colorButton: ImageButton = itemView.findViewById(R.id.colorButton)

            fun bind(tag: FileTag, isChecked: Boolean) {
                tagText.text = tag.name
                checkBox.isChecked = isChecked
                colorButton.setBackgroundColor(tag.color)

                checkBox.setOnCheckedChangeListener { _, checked ->
                    onTagCheckedChange(tag, checked)
                }
                itemView.setOnClickListener {
                    checkBox.toggle()
                }
            }
        }
    }
}