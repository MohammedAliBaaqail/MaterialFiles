package me.zhanghai.android.files.colorpicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.show

class ColorPickerDialog : DialogFragment() {
    private var onColorSelected: ((Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val colors = intArrayOf(
            0xFFF44336.toInt(), // Red
            0xFFE91E63.toInt(), // Pink
            0xFF9C27B0.toInt(), // Purple
            0xFF673AB7.toInt(), // Deep Purple
            0xFF3F51B5.toInt(), // Indigo
            0xFF2196F3.toInt(), // Blue
            0xFF03A9F4.toInt(), // Light Blue
            0xFF00BCD4.toInt(), // Cyan
            0xFF009688.toInt(), // Teal
            0xFF4CAF50.toInt(), // Green
            0xFF8BC34A.toInt(), // Light Green
            0xFFCDDC39.toInt(), // Lime
            0xFFFFEB3B.toInt(), // Yellow
            0xFFFFC107.toInt(), // Amber
            0xFFFF9800.toInt(), // Orange
            0xFFFF5722.toInt()  // Deep Orange
        )

        val gridView = GridView(context).apply {
            numColumns = 4
            adapter = ColorPaletteAdapter(context, colors)
            setOnItemClickListener { _, _, position, _ ->
                onColorSelected?.invoke(colors[position])
                dismiss()
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.color_picker_dialog_title)
            .setView(gridView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private class ColorPaletteAdapter(
        private val context: Context,
        private val colors: IntArray
    ) : BaseAdapter() {
        override fun getCount(): Int = colors.size
        override fun getItem(position: Int): Any = colors[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val colorView = convertView ?: View(context).apply {
                layoutParams = ViewGroup.LayoutParams(60, 60)
            }
            colorView.setBackgroundColor(colors[position])
            return colorView
        }
    }

    companion object {
        fun show(fragment: Fragment, onColorSelected: (Int) -> Unit) {
            ColorPickerDialog().apply {
                this.onColorSelected = onColorSelected
            }.show(fragment)
        }
    }
} 