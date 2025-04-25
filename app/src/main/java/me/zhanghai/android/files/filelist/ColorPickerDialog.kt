package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

class ColorPickerDialog : DialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ColorAdapter
    private var onColorSelected: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.color_picker_dialog, null)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 4)
        adapter = ColorAdapter { color ->
            onColorSelected?.invoke(color)
            dismiss()
        }
        recyclerView.adapter = adapter

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.file_tag_color)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private class ColorAdapter(private val onColorSelected: (String) -> Unit) : 
        RecyclerView.Adapter<ColorAdapter.ViewHolder>() {

        private val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.color_picker_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(colors[position])
        }

        override fun getItemCount() = colors.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorView: ImageView = itemView.findViewById(R.id.colorView)

            fun bind(color: String) {
                colorView.setBackgroundColor(android.graphics.Color.parseColor(color))
                itemView.setOnClickListener { onColorSelected(color) }
            }
        }
    }

    companion object {
        fun show(fragment: androidx.fragment.app.Fragment, onColorSelected: (String) -> Unit) {
            val dialog = ColorPickerDialog().apply {
                this.onColorSelected = onColorSelected
            }
            dialog.show(fragment.childFragmentManager, null)
        }
    }
} 