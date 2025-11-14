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
import me.zhanghai.android.files.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
				result.data?.data?.let { uri ->
					exportAllData(uri)
				}
			}
		}
		importLauncher = activity.registerForActivityResult(
			ActivityResultContracts.StartActivityForResult()
		) { result ->
			if (result.resultCode == AppCompatActivity.RESULT_OK) {
				result.data?.data?.let { uri ->
					importAllData(uri)
				}
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
		// Generate filename with current date and time
		val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
		val timestamp = dateFormat.format(Date())
		val filename = "material_files_full_backup_$timestamp.zip"
		
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

	private fun exportAllData(uri: Uri) {
		try {
			context.contentResolver.openOutputStream(uri)?.use { out ->
				ZipOutputStream(out).use { zos ->
					// Include shared_prefs, databases, filesDir, cacheDir
					zipDir(getDir("shared_prefs"), "shared_prefs", zos)
					zipDir(getDir("databases"), "databases", zos)
					zipDir(context.filesDir, "files", zos)
					zipDir(context.cacheDir, "cache", zos)
				}
			}
			Toast.makeText(context, R.string.full_backup_export_success, Toast.LENGTH_SHORT).show()
		} catch (e: Exception) {
			Log.e("FullDataBackup", "Export failed", e)
			Toast.makeText(context, R.string.full_backup_export_error, Toast.LENGTH_SHORT).show()
		}
	}

	private fun importAllData(uri: Uri) {
		Thread {
			var success = false
			try {
				context.contentResolver.openInputStream(uri)?.use { input ->
					ZipInputStream(input).use { zis ->
						var entry: ZipEntry? = zis.nextEntry
						while (entry != null) {
							if (!entry.isDirectory) {
								val target = when {
									entry.name.startsWith("shared_prefs/") ->
										File(getDir("shared_prefs"), entry.name.removePrefix("shared_prefs/"))
									entry.name.startsWith("databases/") ->
										File(getDir("databases"), entry.name.removePrefix("databases/"))
									entry.name.startsWith("files/") ->
										File(context.filesDir, entry.name.removePrefix("files/"))
									entry.name.startsWith("cache/") ->
										File(context.cacheDir, entry.name.removePrefix("cache/"))
									else -> null
								}
								if (target != null) {
									target.parentFile?.mkdirs()
									FileOutputStream(target).use { fos ->
										zis.copyTo(fos)
									}
								}
							}
							zis.closeEntry()
							entry = zis.nextEntry
						}
					}
				}
				success = true
			} catch (e: Exception) {
				Log.e("FullDataBackup", "Import failed", e)
			}
			(android.os.Handler(android.os.Looper.getMainLooper())).post {
				Toast.makeText(
					context,
					if (success) R.string.full_backup_import_success else R.string.full_backup_import_error,
					Toast.LENGTH_SHORT
				).show()
			}
		}.start()
	}

	private fun getDir(name: String): File =
		File(context.applicationInfo.dataDir, name)

	private fun zipDir(dir: File, base: String, zos: ZipOutputStream) {
		if (!dir.exists()) return
		val stack = ArrayDeque<File>()
		stack.add(dir)
		while (stack.isNotEmpty()) {
			val f = stack.removeFirst()
			val rel = dir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/')
			if (f.isDirectory) {
				f.listFiles()?.forEach { stack.add(it) }
			} else {
				val entryName = if (rel.isEmpty()) "$base/${f.name}" else "$base/$rel"
				zos.putNextEntry(ZipEntry(entryName))
				FileInputStream(f).use { it.copyTo(zos) }
				zos.closeEntry()
			}
		}
	}
}


