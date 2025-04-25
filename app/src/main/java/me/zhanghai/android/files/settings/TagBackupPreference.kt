/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileTagManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TagBackupPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var exportLauncher: ActivityResultLauncher<Intent>? = null
    private var importLauncher: ActivityResultLauncher<Intent>? = null
    
    // Use a timestamp in the default filename for export
    private val defaultExportFileName: String
        get() {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = dateFormat.format(Date())
            return "material_files_tags_$timestamp.json"
        }
    
    /**
     * Must be called from the hosting fragment during initialization
     */
    fun registerForActivityResult(activity: AppCompatActivity) {
        exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportTags(uri)
                }
            }
        }
        
        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importTags(uri)
                }
            }
        }
    }

    override fun onClick() {
        val items = arrayOf(
            context.getString(R.string.tags_backup_export),
            context.getString(R.string.tags_backup_import)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.tags_backup_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchExport()
                    1 -> launchImport()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun launchExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, defaultExportFileName)
        }
        exportLauncher?.launch(intent)
    }
    
    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher?.launch(intent)
    }
    
    private fun exportTags(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val tempFile = File.createTempFile("tags_export", ".json", context.cacheDir)
                
                // Export to temporary file first
                if (FileTagManager.exportTags(tempFile)) {
                    // Then copy to the selected URI
                    val bytes = tempFile.readBytes()
                    outputStream.write(bytes)
                    
                    Toast.makeText(
                        context,
                        R.string.tags_export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.tags_export_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Clean up temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                R.string.tags_export_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun importTags(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val tempFile = File.createTempFile("tags_import", ".json", context.cacheDir)
                
                // Copy to temporary file first
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                // Then import from the temporary file
                if (FileTagManager.importTags(tempFile)) {
                    Toast.makeText(
                        context,
                        R.string.tags_import_success,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.tags_import_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Clean up temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                R.string.tags_import_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
} 