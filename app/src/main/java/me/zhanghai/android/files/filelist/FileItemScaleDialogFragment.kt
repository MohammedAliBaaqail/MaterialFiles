/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java8.nio.file.Path

class FileItemScaleDialogFragment : AppCompatDialogFragment() {

    private var currentScale = 100
    private var isPathSpecific = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.file_list_item_scale_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scaleSeekBar = view.findViewById<SeekBar>(R.id.scaleSlider)
        val scaleText = view.findViewById<TextView>(R.id.scaleValueText)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val okButton = view.findViewById<Button>(R.id.okButton)

        // Get current path and scale
        val currentPath = (parentFragment as? FileListFragment)?.currentPath
        
        // Check if path-specific setting is enabled
        isPathSpecific = if (currentPath != null) {
            (parentFragment as? FileListFragment)?.isViewSortPathSpecific() ?: false
        } else {
            false
        }

        // Get the current scale
        currentScale = if (isPathSpecific && currentPath != null) {
            val pathSettings = PathSettings.getFileListItemScale(currentPath)
            pathSettings.value ?: Settings.FILE_LIST_ITEM_SCALE.valueCompat
        } else {
            Settings.FILE_LIST_ITEM_SCALE.valueCompat
        }

        // Set initial values
        scaleSeekBar.progress = (currentScale - 50) / 5 // Convert 50-250 range to 0-40 for SeekBar
        updateScaleText(scaleText, currentScale)

        // Set up seek bar change listener
        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                currentScale = 50 + progress * 5 // Convert back to 50-250 range
                updateScaleText(scaleText, currentScale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Set up buttons
        cancelButton.setOnClickListener { dismiss() }
        okButton.setOnClickListener {
            if (isPathSpecific && currentPath != null) {
                PathSettings.getFileListItemScale(currentPath).putValue(currentScale)
            } else {
                Settings.FILE_LIST_ITEM_SCALE.putValue(currentScale)
            }
            val listener = parentFragment as? Listener ?: activity as? Listener
            listener?.onItemScaleChanged(currentScale)
            dismiss()
        }
    }

    private fun updateScaleText(textView: TextView, scale: Int) {
        textView.text = getString(R.string.file_list_action_item_scale_value, scale)
    }

    interface Listener {
        fun onItemScaleChanged(scale: Int)
    }

    companion object {
        fun show(fragment: Fragment) {
            FileItemScaleDialogFragment().show(fragment.childFragmentManager, null)
        }
    }
} 