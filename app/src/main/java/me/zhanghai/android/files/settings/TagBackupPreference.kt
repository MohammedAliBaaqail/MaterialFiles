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
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
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

    private val logTag = "TagBackupPreference"
    
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
            Log.d(logTag, "Export activity result received: resultCode=${result.resultCode}, data=${result.data}")
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(logTag, "Export URI selected: $uri")
                    exportTags(uri)
                }
            } else {
                Log.d(logTag, "Export canceled or failed")
            }
        }
        
        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d(logTag, "Import activity result received: resultCode=${result.resultCode}, data=${result.data}")
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(logTag, "Import URI selected: $uri")
                    // Persist read permission for this URI if possible
                    try {
                        val takeFlags = result.data?.flags ?: 0
                        val hasPersistable = (takeFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
                        val hasRead = (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                        if (hasPersistable && hasRead) {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            Log.d(logTag, "Persisted URI read permission for import")
                        } else {
                            Log.d(logTag, "Persistable/read flags not both present; skipping takePersistableUriPermission")
                        }
                    } catch (e: Exception) {
                        Log.w(logTag, "Failed to persist URI permission for import", e)
                    }
                    importTags(uri)
                }
            } else {
                Log.d(logTag, "Import canceled or failed")
            }
        }
        Log.d(logTag, "Launchers registered")
    }

    override fun onClick() {
        Log.d(logTag, "Tag backup preference clicked")
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
        if (exportLauncher == null) {
            Log.w(logTag, "Export launcher is not registered; cannot start export flow")
            Toast.makeText(
                context,
                R.string.tags_export_error,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, defaultExportFileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        exportLauncher?.launch(intent)
    }
    
    private fun launchImport() {
        if (importLauncher == null) {
            Log.w(logTag, "Import launcher is not registered; cannot start import flow")
            Toast.makeText(
                context,
                R.string.tags_import_error,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        importLauncher?.launch(intent)
    }
    
    private fun exportTags(uri: Uri) {
        try {
            Log.d(logTag, "Starting export to $uri")
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val tempFile = File.createTempFile("tags_export", ".json", context.cacheDir)
                
                // Export to temporary file first
                if (FileTagManager.exportTags(tempFile)) {
                    // Then copy to the selected URI
                    val bytes = tempFile.readBytes()
                    outputStream.write(bytes)
                    Log.d(logTag, "Export completed successfully, bytes written=${bytes.size}")
                    
                    Toast.makeText(
                        context,
                        R.string.tags_export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w(logTag, "Export failed in FileTagManager.exportTags")
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
            Log.e(logTag, "Export threw exception", e)
            Toast.makeText(
                context,
                R.string.tags_export_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun importTags(uri: Uri) {
        // Run import off the main thread to avoid blocking the UI
        Thread {
            var success = false
            try {
                Log.d(logTag, "Starting import from $uri")
                Log.d(logTag, "Opening input stream for $uri")
                val inputStreamPrimary = context.contentResolver.openInputStream(uri)
                if (inputStreamPrimary == null) {
                    Log.w(logTag, "openInputStream returned null for $uri")
                } else inputStreamPrimary.use {
                    Log.d(logTag, "Opened input stream for $uri")
                    val tempFile = File.createTempFile("tags_import", ".json", context.cacheDir)

                    // Copy to temporary file first
                    tempFile.outputStream().use { outputStream ->
                        Log.d(logTag, "Copying data to temp file: ${tempFile.absolutePath}")
                        it.copyTo(outputStream)
                    }
                    Log.d(logTag, "Finished copying to temp file")

                    // Then import from the temporary file
                    Log.d(logTag, "Calling FileTagManager.importTags(...)")
                    success = FileTagManager.importTags(tempFile)
                    Log.d(logTag, "Import finished with success=$success")

                    // Clean up temp file
                    tempFile.delete()
                    Log.d(logTag, "Deleted temp file")
                }

                // Fallback if primary path failed to open
                if (!success) {
                    Log.d(logTag, "Primary import path did not complete successfully; trying file descriptor fallback")
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val tempFile = File.createTempFile("tags_import_fd", ".json", context.cacheDir)
                        java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
                            tempFile.outputStream().use { fos ->
                                fis.copyTo(fos)
                            }
                        }
                        success = FileTagManager.importTags(tempFile)
                        Log.d(logTag, "FD fallback import finished with success=$success")
                        tempFile.delete()
                    } ?: run {
                        Log.w(logTag, "openFileDescriptor returned null for $uri")
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Import threw exception", e)
                success = false
            }

            // Post result back to main thread
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    if (success) R.string.tags_import_success else R.string.tags_import_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }
} 