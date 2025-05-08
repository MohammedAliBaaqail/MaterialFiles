/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.AppDatabase
import me.zhanghai.android.files.database.VideoMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VideoMetadataBackupPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var exportLauncher: ActivityResultLauncher<Intent>? = null
    private var importLauncher: ActivityResultLauncher<Intent>? = null
    private val coroutineScope = MainScope()
    
    // Use a timestamp in the default filename for export
    private val defaultExportFileName: String
        get() {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = dateFormat.format(Date())
            return "material_files_thumbnails_$timestamp.zip"
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
                    exportVideoMetadata(uri)
                }
            }
        }
        
        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importVideoMetadata(uri)
                }
            }
        }
    }

    override fun onClick() {
        val items = arrayOf(
            context.getString(R.string.thumbnail_backup_export),
            context.getString(R.string.thumbnail_backup_import)
        )
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.thumbnail_backup_title)
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
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, defaultExportFileName)
        }
        exportLauncher?.launch(intent)
    }
    
    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            // Add MIME type filter for ZIP files
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip", 
                "application/x-zip",
                "application/x-zip-compressed",
                "application/octet-stream"
            ))
        }
        importLauncher?.launch(intent)
    }
    
    private fun exportVideoMetadata(uri: Uri) {
        coroutineScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val videoMetadataDao = db.videoMetadataDao()
                val allMetadata = videoMetadataDao.getAll()
                
                // Create a JSON array to hold metadata without thumbnail data
                val jsonArray = JSONArray()
                
                withContext(Dispatchers.IO) {
                    // Open the output zip stream
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            var thumbnailCount = 0
                            
                            // Add each thumbnail to the zip file
                            for (metadata in allMetadata) {
                                // Only export if we have a valid thumbnail path
                                val thumbnailFile = metadata.thumbnailPath?.let { File(it) }
                                if (metadata.thumbnailPath != null && thumbnailFile?.exists() == true) {
                                    // Generate a unique name for this thumbnail in the zip
                                    val thumbnailFilename = "${thumbnailCount}.jpg"
                                    thumbnailCount++
                                    
                                    // Add thumbnail to zip
                                    zipOut.putNextEntry(ZipEntry("thumbnails/$thumbnailFilename"))
                                    FileInputStream(thumbnailFile).use { input ->
                                        input.copyTo(zipOut)
                                    }
                                    zipOut.closeEntry()
                                    
                                    // Add metadata entry without the actual image data
                                    val metadataJson = JSONObject().apply {
                                        put("path", metadata.path)
                                        put("lastModified", metadata.lastModified)
                                        put("durationMillis", metadata.durationMillis)
                                        put("width", metadata.width)
                                        put("height", metadata.height)
                                        put("thumbnailFilename", thumbnailFilename)
                                    }
                                    jsonArray.put(metadataJson)
                                }
                            }
                            
                            // Create the root JSON object
                            val rootJson = JSONObject().apply {
                                put("videoMetadata", jsonArray)
                            }
                            
                            // Write metadata.json to zip
                            zipOut.putNextEntry(ZipEntry("metadata.json"))
                            zipOut.write(rootJson.toString(2).toByteArray())
                            zipOut.closeEntry()
                        }
                    }
                }
                
                Toast.makeText(
                    context,
                    R.string.thumbnail_export_success,
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting video metadata", e)
                Toast.makeText(
                    context,
                    R.string.thumbnail_export_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun importVideoMetadata(uri: Uri) {
        coroutineScope.launch {
            try {
                // Prepare the thumbnail directory
                val thumbnailDir = File(context.filesDir, "video_thumbnails")
                thumbnailDir.mkdirs()
                
                // Create temp directory for import processing
                val tempDir = File(context.cacheDir, "thumbnail_import_temp")
                tempDir.mkdirs()
                
                // Extract metadata.json and thumbnails from zip
                var metadataJson: JSONObject? = null
                val extractedThumbnails = mutableMapOf<String, File>()
                
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zipIn ->
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                val entryName = entry.name
                                
                                if (entryName == "metadata.json") {
                                    // Read metadata.json
                                    val metadataBytes = zipIn.readBytes()
                                    val metadataString = String(metadataBytes)
                                    metadataJson = JSONObject(metadataString)
                                } else if (entryName.startsWith("thumbnails/") && !entry.isDirectory) {
                                    // Extract thumbnail file
                                    val thumbnailFilename = File(entryName).name
                                    val tempFile = File(tempDir, thumbnailFilename)
                                    FileOutputStream(tempFile).use { output ->
                                        zipIn.copyTo(output)
                                    }
                                    extractedThumbnails[thumbnailFilename] = tempFile
                                }
                                
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    } ?: throw IllegalStateException("Could not open input stream")
                }
                
                // Process metadata and thumbnails
                if (metadataJson == null) {
                    throw IllegalStateException("No metadata.json found in zip file")
                }
                
                val db = AppDatabase.getDatabase(context)
                val videoMetadataDao = db.videoMetadataDao()
                
                val metadataArray = metadataJson!!.getJSONArray("videoMetadata")
                for (i in 0 until metadataArray.length()) {
                    val metadataEntry = metadataArray.getJSONObject(i)
                    
                    // Extract fields
                    val path = metadataEntry.getString("path")
                    val lastModified = metadataEntry.getLong("lastModified")
                    val durationMillis = if (metadataEntry.has("durationMillis")) 
                        metadataEntry.getLong("durationMillis") else null
                    val width = if (metadataEntry.has("width")) 
                        metadataEntry.getInt("width") else null
                    val height = if (metadataEntry.has("height")) 
                        metadataEntry.getInt("height") else null
                    val thumbnailFilename = metadataEntry.getString("thumbnailFilename")
                    
                    // Copy thumbnail file to app directory
                    val tempFile = extractedThumbnails[thumbnailFilename]
                    if (tempFile != null && tempFile.exists()) {
                        // Generate a unique filename for persistent storage
                        val fileName = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(path.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        val thumbnailFile = File(thumbnailDir, "$fileName.jpg")
                        
                        withContext(Dispatchers.IO) {
                            tempFile.copyTo(thumbnailFile, overwrite = true)
                        }
                        
                        // Create and save metadata entry
                        val metadata = VideoMetadata(
                            path = path,
                            lastModified = lastModified,
                            durationMillis = durationMillis,
                            thumbnailPath = thumbnailFile.absolutePath,
                            width = width,
                            height = height
                        )
                        
                        videoMetadataDao.insertOrReplace(metadata)
                    }
                }
                
                // Clean up temp directory
                withContext(Dispatchers.IO) {
                    tempDir.deleteRecursively()
                }
                
                Toast.makeText(
                    context,
                    R.string.thumbnail_import_success,
                    Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error importing video metadata: ${e.message}", e)
                Toast.makeText(
                    context,
                    R.string.thumbnail_import_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    companion object {
        private const val TAG = "VideoMetadataBackup"
    }
} 