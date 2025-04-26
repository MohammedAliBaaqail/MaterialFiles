/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileRatingManager
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import java.util.*
import kotlinx.parcelize.Parcelize

class FileRatingDialogFragment : AppCompatDialogFragment() {

    private val args by args<Args>()

    private lateinit var ratingValueText: TextView
    private lateinit var ratingSlider: Slider

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val files = args.files
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.file_rating_dialog, null)
        
        val titleText = view.findViewById<TextView>(R.id.titleText)
        if (files.size == 1) {
            titleText.text = getString(R.string.file_rating_title) + ": " + files[0].name
        }

        ratingValueText = view.findViewById(R.id.ratingValueText)
        ratingSlider = view.findViewById(R.id.ratingSlider)
        
        // If we have a single file, set the current rating
        if (files.size == 1) {
            val rating = FileRatingManager.getRating(files[0].path)
            ratingSlider.value = rating.toFloat()
            ratingValueText.text = rating.toString()
        }
        
        ratingSlider.addOnChangeListener { _, value, _ ->
            ratingValueText.text = value.toInt().toString()
        }

        val dialog = MaterialAlertDialogBuilder(context, theme)
            .setView(view)
            .create()
        
        view.findViewById<View>(R.id.clearButton).setOnClickListener {
            setRating(0)
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.cancelButton).setOnClickListener {
            dialog.cancel()
        }
        
        view.findViewById<View>(R.id.okButton).setOnClickListener {
            setRating(ratingSlider.value.toInt())
            dialog.dismiss()
        }
        
        return dialog
    }
    
    private fun setRating(rating: Int) {
        val files = args.files
        val paths = files.map { it.path }
        FileRatingManager.setRatingForFiles(paths, rating)
        if (rating > 0) {
            requireContext().showToast(R.string.rating_set_success)
        }
    }

    companion object {
        fun show(files: List<FileItem>, fragment: Fragment) {
            FileRatingDialogFragment().putArgs(Args(files)).show(fragment.childFragmentManager, null)
        }
    }

    @Parcelize
    class Args(val files: List<FileItem>) : ParcelableArgs
} 