package me.zhanghai.android.files.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.AppDatabase
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.file.FileRatingManager
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
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

class FullDataBackupPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var exportLauncher: ActivityResultLauncher<Intent>? = null
    private var importLauncher: ActivityResultLauncher<Intent>? = null

    fun registerForActivityResult(activity: AppCompatActivity) {
        exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri -> exportBackup(uri) }
            }
        }
        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri -> importBackup(uri) }
            }
        }
    }

    override fun onClick() {
        val items = arrayOf(
            context.getString(R.string.full_backup_export),
            context.getString(R.string.full_backup_import)
        )
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.settings_full_backup_title)
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val filename = "material_files_backup_${dateFormat.format(Date())}.zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        exportLauncher?.launch(intent)
    }

    private fun launchImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        importLauncher?.launch(intent)
    }

    private fun exportBackup(uri: Uri) {
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setMessage("Exporting backup...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val tempZipFile = File(context.cacheDir, "temp_backup_export.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            try {
                // 1. Write Zip to local temporary file first (Faster & Safer)
                FileOutputStream(tempZipFile).use { fos ->
                    java.io.BufferedOutputStream(fos).use { bos ->
                        ZipOutputStream(bos).use { zos ->
                            // A. Export Tags & Ratings (from Manager)
                            val tagsFile = File(context.cacheDir, "tags_export_temp.json")
                            if (FileTagManager.exportTags(tagsFile)) {
                                addToZip(tagsFile, "metadata/tags.json", zos)
                                tagsFile.delete()
                            }

                            // B. Export Video Thumbnails & Metadata (from DB)
                            val db = AppDatabase.getDatabase(context)
                            val thumbnailsJson = JSONArray()
                            // Use raw query
                            val cursor = db.openHelper.readableDatabase.query("SELECT * FROM video_thumbnails")
                            cursor.use { c ->
                                if (c.moveToFirst()) {
                                    do {
                                        val obj = JSONObject()
                                        (0 until c.columnCount).forEach { i ->
                                            when (c.getType(i)) {
                                                android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(c.getColumnName(i), c.getLong(i))
                                                android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(c.getColumnName(i), c.getDouble(i))
                                                android.database.Cursor.FIELD_TYPE_STRING -> obj.put(c.getColumnName(i), c.getString(i))
                                                android.database.Cursor.FIELD_TYPE_NULL -> obj.put(c.getColumnName(i), JSONObject.NULL)
                                            }
                                        }
                                        thumbnailsJson.put(obj)
                                    } while (c.moveToNext())
                                }
                            }
                            addToZipStr(thumbnailsJson.toString(), "metadata/video_thumbnails.json", zos)
                            
                            // C. Export Video Metadata
                            val metaJson = JSONArray()
                            val cursorMeta = db.openHelper.readableDatabase.query("SELECT * FROM video_metadata")
                            cursorMeta.use { c ->
                                if (c.moveToFirst()) {
                                    do {
                                        val obj = JSONObject()
                                        (0 until c.columnCount).forEach { i ->
                                            when (c.getType(i)) {
                                                android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(c.getColumnName(i), c.getLong(i))
                                                android.database.Cursor.FIELD_TYPE_STRING -> obj.put(c.getColumnName(i), c.getString(i))
                                                android.database.Cursor.FIELD_TYPE_NULL -> obj.put(c.getColumnName(i), JSONObject.NULL)
                                            }
                                        }
                                        metaJson.put(obj)
                                    } while (c.moveToNext())
                                }
                            }
                            addToZipStr(metaJson.toString(), "metadata/video_metadata.json", zos)

                            // D. Export Images
                            val vidThumbDir = File(context.filesDir, "video_thumbnails")
                            if (vidThumbDir.exists()) {
                                zipDirectory(vidThumbDir, "images/video_thumbnails", zos)
                            }
                            val folderThumbDir = File(context.filesDir, "folder_thumbnails")
                            if (folderThumbDir.exists()) {
                                 zipDirectory(folderThumbDir, "images/folder_thumbnails", zos)
                            }
                        }
                    }
                }

                // 2. Copy the safe temporary file to the user's destination URI
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempZipFile).use { input ->
                        input.copyTo(out)
                    }
                }
                
                postResult(true, "Backup exported successfully", progressDialog)
            } catch (e: Exception) {
                Log.e("Backup", "Export failed", e)
                postResult(false, "Export failed: ${e.message}", progressDialog)
            } finally {
                tempZipFile.delete()
            }
        }.start()
    }

    private fun importBackup(uri: Uri) {
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setMessage(R.string.full_backup_importing)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val tempDir = File(context.cacheDir, "restore_temp")
            tempDir.deleteRecursively()
            tempDir.mkdirs()
            
            var extractionError = false

            try {
                // 1. Unzip everything (Best Effort)
                // Use BufferedInputStream to prevent premature EOF on some streams
                context.contentResolver.openInputStream(uri)?.let { rawInput ->
                    java.io.BufferedInputStream(rawInput).use { input ->
                        ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val outFile = File(tempDir, entry.name)
                                    // Prevent Zip Path Traversal
                                    if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                                        Log.w("Backup", "Skipping malicious zip entry: ${entry.name}")
                                        entry = zis.nextEntry
                                        continue
                                    }
                                    
                                    outFile.parentFile?.mkdirs()
                                    try {
                                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                    } catch (e: Exception) {
                                        Log.e("Backup", "Failed to extract entry: ${entry.name}", e)
                                        outFile.delete() // Remove partial file
                                        extractionError = true
                                        // If we hit EOF or stream error, we probably can't read further
                                        break
                                    }
                                }
                                try {
                                    entry = zis.nextEntry
                                } catch (e: Exception) {
                                    Log.e("Backup", "Failed to move to next entry", e)
                                    extractionError = true
                                    break
                                }
                            }
                        }
                    }
                }

                // 2. Detect Backup Type & Restore
                var restored = false
                if (File(tempDir, "metadata/tags.json").exists()) {
                    restoreLogicalBackup(tempDir)
                    restored = true
                } else if (File(tempDir, "shared_prefs").exists() || File(tempDir, "databases").exists()) {
                    restoreLegacyBackup(tempDir)
                    restored = true
                }
                
                if (restored) {
                    val msg = if (extractionError) "Backup imported partially (source file corrupt). Restarting..." else "Backup imported successfully. Restarting..."
                    postResult(true, msg, progressDialog, restart = true)
                } else {
                    throw Exception("No valid backup data found in file")
                }

            } catch (e: Exception) {
                Log.e("Backup", "Import failed", e)
                postResult(false, "Import failed: ${e.message}", progressDialog)
            } finally {
                tempDir.deleteRecursively()
            }
        }.start()
    }

    private fun restoreLogicalBackup(root: File) {
        // Tags
        val tagsFile = File(root, "metadata/tags.json")
        if (tagsFile.exists()) {
            FileTagManager.importTags(tagsFile)
        }

        // Database
        val db = AppDatabase.getDatabase(context).openHelper.writableDatabase
        
        // Video Metadata
        val metaFile = File(root, "metadata/video_metadata.json")
        if (metaFile.exists()) {
            val json = JSONArray(metaFile.readText())
            db.beginTransaction()
            try {
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    val cv = jsonToContentValues(obj)
                    db.insert("video_metadata", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        // Video Thumbnails
        val thumbMetaFile = File(root, "metadata/video_thumbnails.json")
        if (thumbMetaFile.exists()) {
             val json = JSONArray(thumbMetaFile.readText())
             restoreVideoThumbnailsToDb(db, json)
        }
        
        // Images
        val vidThumbSrc = File(root, "images/video_thumbnails")
        if (vidThumbSrc.exists()) {
             vidThumbSrc.copyRecursively(File(context.filesDir, "video_thumbnails"), overwrite = true)
        }
        val folderThumbSrc = File(root, "images/folder_thumbnails")
        if (folderThumbSrc.exists()) {
             folderThumbSrc.copyRecursively(File(context.filesDir, "folder_thumbnails"), overwrite = true)
        }
    }

    // LEGACY RESTORE LOGIC
    private fun restoreLegacyBackup(root: File) {
        // 1. Extract Tags/Ratings from XML
        val tagsXml = File(root, "shared_prefs/file_tags.xml")
        val ratingsXml = File(root, "shared_prefs/file_ratings.xml")
        
        if (tagsXml.exists() || ratingsXml.exists()) {
            val combinedJson = JSONObject()
            
            if (tagsXml.exists()) {
                val tagsMap = parseXmlPrefs(tagsXml)
                tagsMap["tags"]?.let { combinedJson.put("tags", JSONArray(it)) }
                tagsMap["file_tags"]?.let { combinedJson.put("file_tags", JSONObject(it)) }
                tagsMap["tag_orders"]?.let { combinedJson.put("tag_orders", JSONObject(it)) }
            }
            if (ratingsXml.exists()) {
                val ratingsMap = parseXmlPrefs(ratingsXml)
                ratingsMap["ratings"]?.let { combinedJson.put("ratings", JSONArray(it)) }
            }
            
            // Feed to FileTagManager (it handles partial imports usually, or we dump to a file it likes)
            val tempJsonFile = File(context.cacheDir, "legacy_tags_import.json")
            tempJsonFile.writeText(combinedJson.toString())
            FileTagManager.importTags(tempJsonFile)
            tempJsonFile.delete()
        }

        // 2. Extract DB Data
        val dbFile = File(root, "databases/material_files_database")
        if (dbFile.exists()) {
             val legacyDb = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
             val currentDb = AppDatabase.getDatabase(context).openHelper.writableDatabase
             
             try {
                 currentDb.beginTransaction()
                 
                 // Restore Video Metadata
                 try {
                     val cursor = legacyDb.query("video_metadata", null, null, null, null, null, null)
                     cursor.use { c ->
                         while (c.moveToNext()) {
                            val cv = android.content.ContentValues()
                            android.database.DatabaseUtils.cursorRowToContentValues(c, cv)
                            currentDb.insert("video_metadata", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                         }
                     }
                 } catch (e: Exception) { Log.e("Backup", "Legacy metadata restore error", e) }

                 // Restore Thumbnails
                 try {
                     val cursor = legacyDb.query("video_thumbnails", null, null, null, null, null, null)
                     val thumbsJson = JSONArray()
                     cursor.use { c ->
                         while (c.moveToNext()) {
                             val obj = JSONObject()
                             val cv = android.content.ContentValues()
                             android.database.DatabaseUtils.cursorRowToContentValues(c, cv)
                             cv.valueSet().forEach { 
                                 obj.put(it.key, it.value) 
                             }
                             thumbsJson.put(obj)
                         }
                     }
                     restoreVideoThumbnailsToDb(currentDb, thumbsJson)
                 } catch (e: Exception) { Log.e("Backup", "Legacy thumbnails restore error", e) }

                 currentDb.setTransactionSuccessful()
             } finally {
                 currentDb.endTransaction()
                 legacyDb.close()
             }
        }

        // 3. Files
        val fileSrc = File(root, "files")
        if (fileSrc.exists()) {
            fileSrc.copyRecursively(context.filesDir, overwrite = true)
        }
    }
    
    // UTILS

    private fun restoreVideoThumbnailsToDb(db: androidx.sqlite.db.SupportSQLiteDatabase, json: JSONArray) {
        val currentThumbDir = File(context.filesDir, "video_thumbnails")
        
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val path = obj.optString("thumbnail_path")
            
            // Sanitize Path
            if (path.isNotEmpty()) {
                val filename = File(path).name
                val newFile = File(currentThumbDir, filename)
                if (newFile.exists()) {
                    obj.put("thumbnail_path", newFile.absolutePath)
                }
            }
            
            val cv = jsonToContentValues(obj)
            db.insert("video_thumbnails", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
        }
    }

    private fun jsonToContentValues(json: JSONObject): android.content.ContentValues {
        val cv = android.content.ContentValues()
        for (key in json.keys()) {
            when (val v = json.get(key)) {
                is Int -> cv.put(key, v)
                is Long -> cv.put(key, v)
                is Double -> cv.put(key, v)
                is String -> cv.put(key, v)
                is Boolean -> cv.put(key, v)
            }
        }
        return cv
    }

    private fun parseXmlPrefs(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        file.inputStream().use { input ->
             val parser = Xml.newPullParser()
             parser.setInput(input, null)
             var eventType = parser.eventType
             while (eventType != XmlPullParser.END_DOCUMENT) {
                 if (eventType == XmlPullParser.START_TAG && parser.name == "string") {
                     val name = parser.getAttributeValue(null, "name")
                     val value = parser.nextText()
                     if (name != null) map[name] = value
                 }
                 eventType = parser.next()
             }
        }
        return map
    }

    private fun zipDirectory(dir: File, base: String, zos: ZipOutputStream) {
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                val rel = f.relativeTo(dir).path.replace("\\", "/")
                val entry = ZipEntry("$base/$rel")
                zos.putNextEntry(entry)
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun addToZipStr(content: String, path: String, zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }

    private fun addToZip(file: File, path: String, zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun postResult(success: Boolean, message: String, dialog: androidx.appcompat.app.AlertDialog, restart: Boolean = false) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { if (dialog.isShowing) dialog.dismiss() } catch(e: Exception){}
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (success && restart) {
                 android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    val comp = intent?.component
                    val mainIntent = Intent.makeRestartActivityTask(comp)
                    context.startActivity(mainIntent)
                    Runtime.getRuntime().exit(0)
                 }, 1500)
            }
        }
    }
}


