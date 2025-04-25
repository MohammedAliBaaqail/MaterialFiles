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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.colorpicker.ColorPickerDialog
import me.zhanghai.android.files.util.show
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Collections

class FileTagManagementDialogFragment : DialogFragment() {
    private lateinit var files: List<FileItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TagAdapter
    private var parentFragment: Fragment? = null
    private lateinit var itemTouchHelper: ItemTouchHelper

    interface Listener {
        fun onTagsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = arguments?.getParcelableArrayList(EXTRA_FILES) ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_management_dialog, null)
        
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        val selectedTags = files.flatMap { FileTagManager.getTagsForFile(it.path) }.toSet()
        adapter = TagAdapter(
            FileTagManager.getAllTags().toMutableList(),
            selectedTags
        ) { tag, isChecked ->
            files.forEach { file ->
                if (isChecked) {
                    FileTagManager.addTagToFile(tag.id, file.path)
                } else {
                    FileTagManager.removeTagFromFile(tag.id, file.path)
                }
            }
            notifyTagsChanged()
        }
        recyclerView.adapter = adapter
        
        // Setup drag and drop functionality
        setupDragAndDrop()

        return MaterialAlertDialogBuilder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.file_tag_management_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Save the current tag order when closing
                if (files.size == 1) {
                    val currentTags = adapter.getAllTags().filter { tag -> adapter.isTagSelected(tag) }
                    val path = files.first().path
                    FileTagManager.updateTagOrderForFile(currentTags.map { it.id }, path)
                }
            }
            .setNeutralButton(R.string.file_tag_add_new) { _, _ ->
                showAddTagDialog()
            }
            .create()
    }
    
    private fun setupDragAndDrop() {
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                // Only allow reordering of selected tags
                val fromTag = adapter.getTagAt(fromPosition)
                val toTag = adapter.getTagAt(toPosition)
                
                if (adapter.isTagSelected(fromTag) && adapter.isTagSelected(toTag)) {
                    adapter.moveTag(fromPosition, toPosition)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
            
            override fun isLongPressDragEnabled(): Boolean {
                // Only enable if we're working with a single file
                return files.size == 1
            }
            
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                
                // After a drag operation, update the order in the manager
                if (files.size == 1) {
                    val currentTags = adapter.getAllTags().filter { tag -> adapter.isTagSelected(tag) }
                    val path = files.first().path
                    FileTagManager.updateTagOrderForFile(currentTags.map { it.id }, path)
                }
            }
        }
        
        itemTouchHelper = ItemTouchHelper(touchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun notifyTagsChanged() {
        Log.d("FileTagManager", "Notifying tags changed to parent fragment: $parentFragment")
        (parentFragment as? Listener)?.onTagsChanged()
    }

    private fun showAddTagDialog() {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_add_dialog, null)
        val nameEdit = view.findViewById<EditText>(R.id.nameEdit)
        var selectedColor = FileTag.generateRandomColor()
        val colorButton = view.findViewById<ImageButton>(R.id.colorButton)
        colorButton.setBackgroundColor(selectedColor)
        
        val dialog = MaterialAlertDialogBuilder(context, R.style.AppTheme_Dialog)
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
                    notifyTagsChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        colorButton.setOnClickListener {
            (context as? FragmentActivity)?.supportFragmentManager?.fragments?.firstOrNull()?.let { fragment ->
                ColorPickerDialog.show(fragment) { color ->
                    selectedColor = color
                    colorButton.setBackgroundColor(color)
                }
            }
        }

        dialog.show()
    }

    companion object {
        private const val EXTRA_FILES = "files"

        fun show(files: List<FileItem>, fragment: Fragment) {
            FileTagManagementDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(EXTRA_FILES, ArrayList(files))
                }
                parentFragment = fragment
            }.show(fragment)
        }
    }

    private inner class TagAdapter(
        private val allTags: MutableList<FileTag>,
        private val selectedTags: Set<FileTag>,
        private val onTagCheckedChange: (FileTag, Boolean) -> Unit
    ) : RecyclerView.Adapter<TagAdapter.ViewHolder>() {
        
        private val selectedTagSet = selectedTags.toMutableSet()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.file_tag_item, parent, false)
            return ViewHolder(view, this)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tag = allTags[position]
            holder.bind(tag, tag in selectedTagSet)
        }

        override fun getItemCount(): Int = allTags.size
        
        fun getAllTags(): List<FileTag> = allTags.toList()
        
        fun getTagAt(position: Int): FileTag = allTags[position]
        
        fun isTagSelected(tag: FileTag): Boolean = tag in selectedTagSet
        
        fun moveTag(fromPosition: Int, toPosition: Int) {
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    Collections.swap(allTags, i, i + 1)
                }
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    Collections.swap(allTags, i, i - 1)
                }
            }
            notifyItemMoved(fromPosition, toPosition)
        }

        fun addTag(tag: FileTag) {
            allTags.add(tag)
            selectedTagSet.add(tag)
            notifyItemInserted(allTags.size - 1)
        }

        fun updateTag(tag: FileTag) {
            val index = allTags.indexOfFirst { it.id == tag.id }
            if (index != -1) {
                allTags[index] = tag
                
                // Update in selected set too if present
                if (selectedTagSet.any { it.id == tag.id }) {
                    selectedTagSet.removeAll { it.id == tag.id }
                    selectedTagSet.add(tag)
                }
                
                notifyItemChanged(index)
            }
        }

        fun removeTag(tag: FileTag) {
            val index = allTags.indexOfFirst { it.id == tag.id }
            if (index != -1) {
                allTags.removeAt(index)
                selectedTagSet.remove(tag)
                notifyItemRemoved(index)
            }
        }

        inner class ViewHolder(itemView: View, private val adapter: TagAdapter) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
            private val tagText: TextView = itemView.findViewById(R.id.tagText)
            private val tagContainer: View = itemView.findViewById(R.id.tagContainer)
            private val dragHandle: View = itemView.findViewById(R.id.dragHandle)

            fun bind(tag: FileTag, isChecked: Boolean) {
                tagText.text = tag.name
                checkBox.isChecked = isChecked
                tagContainer.setBackgroundColor(tag.color)
                
                // Show drag handle only for selected tags when working with a single file
                if (files.size == 1 && isChecked) {
                    dragHandle.visibility = View.VISIBLE
                    dragHandle.setOnTouchListener { _, _ ->
                        itemTouchHelper.startDrag(this)
                        true
                    }
                } else {
                    dragHandle.visibility = View.GONE
                }

                checkBox.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedTagSet.add(tag)
                    } else {
                        selectedTagSet.remove(tag)
                    }
                    onTagCheckedChange(tag, checked)
                    
                    // Update drag handle visibility when checked state changes
                    if (files.size == 1) {
                        dragHandle.visibility = if (checked) View.VISIBLE else View.GONE
                    }
                }

                itemView.setOnClickListener {
                    checkBox.toggle()
                }

                itemView.setOnLongClickListener {
                    if (files.size == 1 && isChecked) {
                        itemTouchHelper.startDrag(this)
                    } else {
                        showEditTagDialog(tag)
                    }
                    true
                }

                tagContainer.setOnClickListener {
                    showEditTagDialog(tag)
                }
            }

            private fun showEditTagDialog(tag: FileTag) {
                val context = itemView.context
                val view = LayoutInflater.from(context).inflate(R.layout.file_tag_add_dialog, null)
                val nameEdit = view.findViewById<EditText>(R.id.nameEdit)
                var selectedColor = tag.color
                val colorButton = view.findViewById<ImageButton>(R.id.colorButton)
                
                nameEdit.setText(tag.name)
                colorButton.setBackgroundColor(selectedColor)

                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.file_tag_management_title)
                    .setView(view)
                    .setPositiveButton(R.string.save) { _, _ ->
                        val name = nameEdit.text.toString().trim()
                        if (name.isNotEmpty()) {
                            val updatedTag = tag.copy(name = name, color = selectedColor)
                            FileTagManager.updateTag(updatedTag)
                            adapter.updateTag(updatedTag)
                            this@FileTagManagementDialogFragment.notifyTagsChanged()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.delete) { _, _ ->
                        showDeleteConfirmationDialog(tag)
                    }
                    .create()

                colorButton.setOnClickListener {
                    (context as? FragmentActivity)?.supportFragmentManager?.fragments?.firstOrNull()?.let { fragment ->
                        ColorPickerDialog.show(fragment) { color ->
                            selectedColor = color
                            colorButton.setBackgroundColor(color)
                        }
                    }
                }

                dialog.show()
            }

            private fun showDeleteConfirmationDialog(tag: FileTag) {
                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.file_tag_delete_message)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        FileTagManager.deleteTag(tag.id)
                        adapter.removeTag(tag)
                        this@FileTagManagementDialogFragment.notifyTagsChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}