package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.util.show

class FileTagFilterDialog : DialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileTagFilterAdapter
    private lateinit var filterModeGroup: RadioGroup
    private lateinit var tagManager: FileTagManager
    
    private var selectedTags = setOf<FileTag>()
    private var isMatchAll = false
    
    interface Listener {
        fun onTagFilterChanged(selectedTags: Set<FileTag>, matchAll: Boolean)
        fun getCurrentTagFilter(): Set<FileTag>
        fun isMatchAllTags(): Boolean
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_tag_filter_dialog, null)
        
        tagManager = FileTagManager
        
        recyclerView = view.findViewById(R.id.recyclerView)
        filterModeGroup = view.findViewById(R.id.filterModeGroup)
        
        // Initialize with current state
        val listener = parentFragment as? Listener
        selectedTags = listener?.getCurrentTagFilter() ?: emptySet()
        isMatchAll = listener?.isMatchAllTags() ?: false
        
        adapter = FileTagFilterAdapter { tags ->
            selectedTags = tags
            notifyFilterChanged()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        adapter.setTags(tagManager.getAllTags())
        adapter.setSelectedTags(selectedTags)
        
        // Set initial filter mode
        filterModeGroup.check(if (isMatchAll) R.id.filterModeAll else R.id.filterModeAny)
        
        filterModeGroup.setOnCheckedChangeListener { _, checkedId ->
            isMatchAll = checkedId == R.id.filterModeAll
            notifyFilterChanged()
        }
        
        return MaterialAlertDialogBuilder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.file_tag_filter_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                selectedTags = emptySet()
                isMatchAll = false
                notifyFilterChanged()
            }
            .create()
    }
    
    private fun notifyFilterChanged() {
        (parentFragment as? Listener)?.onTagFilterChanged(selectedTags, isMatchAll)
    }
    
    companion object {
        fun show(fragment: androidx.fragment.app.Fragment) {
            FileTagFilterDialog().show(fragment)
        }
    }
} 