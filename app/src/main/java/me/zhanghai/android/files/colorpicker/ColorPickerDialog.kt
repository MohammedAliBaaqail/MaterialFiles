package me.zhanghai.android.files.colorpicker

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.show
import com.larswerkman.holocolorpicker.ColorPicker
import com.larswerkman.holocolorpicker.SVBar
import com.larswerkman.holocolorpicker.SaturationBar
import com.larswerkman.holocolorpicker.ValueBar

class ColorPickerDialog : DialogFragment() {
    private var onColorSelected: ((Int) -> Unit)? = null
    private lateinit var colorPicker: ColorPicker
    private lateinit var saturationBar: SaturationBar
    private lateinit var valueBar: ValueBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.color_picker_dialog, null)

        colorPicker = view.findViewById(R.id.color_picker)
        saturationBar = view.findViewById(R.id.saturation_bar)
        valueBar = view.findViewById(R.id.value_bar)

        // Connect the bars to the picker
        colorPicker.addSaturationBar(saturationBar)
        colorPicker.addValueBar(valueBar)

        // Set initial color
        colorPicker.setOldCenterColor(Color.RED)
        colorPicker.setNewCenterColor(Color.RED)
        colorPicker.setShowOldCenterColor(false)

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.color_picker_dialog_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onColorSelected?.invoke(colorPicker.color)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    companion object {
        fun show(fragment: Fragment, onColorSelected: (Int) -> Unit) {
            ColorPickerDialog().apply {
                this.onColorSelected = onColorSelected
            }.show(fragment)
        }
    }
} 