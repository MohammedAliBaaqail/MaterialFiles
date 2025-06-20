/*
 * Copyright (c) 2025 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import java8.nio.file.Paths
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class FolderThumbnailManagementDialogFragment : AppCompatDialogFragment() {

    private val args by args<Args>()
    private lateinit var path: File
    private var customImageUri: Uri? = null
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleCustomImageSelected(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.folder_thumbnail_management_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        path = File(args.path)
        
        val thumbnailImageView = view.findViewById<ImageView>(R.id.thumbnail_image)
        val chooseImageButton = view.findViewById<MaterialButton>(R.id.choose_image_button)
        val removeThumbnailButton = view.findViewById<MaterialButton>(R.id.remove_thumbnail_button)
        val saveButton = view.findViewById<MaterialButton>(R.id.save_button)
        val progressIndicator = view.findViewById<LinearProgressIndicator>(R.id.progress_indicator)
        
        // Load current thumbnail if it exists
        loadCurrentThumbnail(thumbnailImageView, progressIndicator)
        
        chooseImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickImageLauncher.launch(intent)
        }
        
        removeThumbnailButton.setOnClickListener {
            customImageUri = null
            thumbnailImageView.setImageResource(R.drawable.ic_folder_white_24dp)
            
            // Remove the thumbnail file if it exists
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val thumbnailFile = FolderThumbnailManager.getFolderThumbnail(requireContext(), path)
                    thumbnailFile?.delete()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            R.string.folder_thumbnail_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Notify listeners that thumbnail has been removed
                        parentFragmentManager.setFragmentResult(
                            REQUEST_KEY, Bundle().apply { putString(KEY_PATH, path.absolutePath) }
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.folder_thumbnail_remove_error, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        saveButton.setOnClickListener {
            saveThumbnail(thumbnailImageView, progressIndicator)
        }
    }
    
    private fun loadCurrentThumbnail(imageView: ImageView, progressIndicator: LinearProgressIndicator) {
        progressIndicator.isVisible = true
        
        lifecycleScope.launch {
            try {
                val thumbnailFile = withContext(Dispatchers.IO) {
                    FolderThumbnailManager.getFolderThumbnail(requireContext(), path)
                }
                
                if (thumbnailFile != null && thumbnailFile.exists()) {
                    // Load existing thumbnail
                    imageView.setImageURI(Uri.fromFile(thumbnailFile))
                } else {
                    // Show folder icon if no thumbnail exists
                    imageView.setImageResource(R.drawable.ic_folder_white_24dp)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.folder_thumbnail_load_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
                imageView.setImageResource(R.drawable.ic_folder_white_24dp)
            } finally {
                progressIndicator.isVisible = false
            }
        }
    }
    
    private fun handleCustomImageSelected(uri: Uri) {
        val imageView = view?.findViewById<ImageView>(R.id.thumbnail_image) ?: return
        val progressIndicator = view?.findViewById<LinearProgressIndicator>(R.id.progress_indicator) ?: return
        
        lifecycleScope.launch {
            progressIndicator.isVisible = true
            customImageUri = uri
            
            try {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        // Just set the URI directly for preview
                        withContext(Dispatchers.Main) {
                            imageView.setImageURI(uri)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.folder_thumbnail_load_image_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                progressIndicator.isVisible = false
            }
        }
    }
    
    private fun saveThumbnail(imageView: ImageView, progressIndicator: LinearProgressIndicator) {
        val context = context ?: return
        
        lifecycleScope.launch {
            progressIndicator.isVisible = true
            
            try {
                val bitmap = when (val uri = customImageUri) {
                    null -> {
                        // No custom image selected, use current image
                        (imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }
                    else -> {
                        // Load the selected image
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)
                            }
                        }
                    }
                }
                
                if (bitmap != null) {
                    // Generate unique filename based on folder path
                    val hash = MessageDigest.getInstance("SHA-256")
                        .digest(path.absolutePath.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    
                    val thumbnailDir = File(context.filesDir, FolderThumbnailManager.THUMBNAIL_DIR)
                    thumbnailDir.mkdirs()
                    
                    val thumbnailFile = File(thumbnailDir, "${hash}.jpg")
                    
                    // Save the bitmap
                    withContext(Dispatchers.IO) {
                        FileOutputStream(thumbnailFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                    
                    Toast.makeText(
                        context,
                        R.string.folder_thumbnail_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Notify listeners that thumbnail has been updated
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY, Bundle().apply { putString(KEY_PATH, path.absolutePath) }
                    )
                    
                    dismiss()
                } else {
                    Toast.makeText(
                        context,
                        R.string.folder_thumbnail_save_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    getString(R.string.folder_thumbnail_save_error_detailed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressIndicator.isVisible = false
            }
        }
    }

    companion object {
        const val REQUEST_KEY = "me.zhanghai.android.files.filelist.FolderThumbnailManagementDialogFragment"
        const val KEY_PATH = "me.zhanghai.android.files.filelist.FolderThumbnailManagementDialogFragment.PATH"

        private const val ARG_PATH = "path"
        
        fun show(path: java8.nio.file.Path, fragment: Fragment) {
            FolderThumbnailManagementDialogFragment().putArgs(
                Args(path.toString())
            ).show(fragment.childFragmentManager, null)
        }
    }

    @kotlinx.parcelize.Parcelize
    class Args(val path: String) : me.zhanghai.android.files.util.ParcelableArgs


}
