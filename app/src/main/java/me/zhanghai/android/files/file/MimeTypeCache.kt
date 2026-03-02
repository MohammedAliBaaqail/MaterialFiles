/*
 * Simple persistent MIME type cache keyed by file path and validated by lastModified + size.
 */
package me.zhanghai.android.files.file

import android.util.Log
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.common.AndroidFileTypeDetector
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java8.nio.file.attribute.BasicFileAttributes

object MimeTypeCache {
	private const val TAG = "MimeTypeCache"
	private const val CACHE_FILE_NAME = "mime_cache.json"
	private const val MAX_ENTRIES = 10000

	private data class Entry(
		val mime: String,
		val lastModified: Long,
		val size: Long
	)

	private val cacheFile: File by lazy {
		File(application.cacheDir, CACHE_FILE_NAME)
	}

	private val map: ConcurrentHashMap<String, Entry> = ConcurrentHashMap()

	@Volatile
	private var isLoaded: Boolean = false

	private fun ensureLoaded() {
		if (isLoaded) return
		synchronized(this) {
			if (isLoaded) return
			if (!cacheFile.exists()) {
				isLoaded = true
				return
			}
			try {
				val text = cacheFile.readText()
				val json = JSONObject(text)
				for (key in json.keys()) {
					val obj = json.getJSONObject(key)
					val entry = Entry(
						mime = obj.getString("mime"),
						lastModified = obj.getLong("lastModified"),
						size = obj.getLong("size")
					)
					map[key] = entry
				}
			} catch (e: Exception) {
				Log.w(TAG, "Failed to load cache", e)
				// Start fresh
				map.clear()
			}
			isLoaded = true
		}
	}

	@Synchronized
	private fun persist() {
		try {
			val json = JSONObject()
			// Soft limit: if too large, drop extras arbitrarily
			if (map.size > MAX_ENTRIES) {
				// Remove roughly 10% oldest-ish by iteration (ConcurrentHashMap has no order; this is a soft trim)
				val toRemove = map.keys.take(map.size - MAX_ENTRIES + MAX_ENTRIES / 10)
				toRemove.forEach { map.remove(it) }
			}
			for ((k, v) in map) {
				val obj = JSONObject()
				obj.put("mime", v.mime)
				obj.put("lastModified", v.lastModified)
				obj.put("size", v.size)
				json.put(k, obj)
			}
			cacheFile.writeText(json.toString())
		} catch (e: Exception) {
			Log.w(TAG, "Failed to persist cache", e)
		}
	}

    private val persistRunnable = me.zhanghai.android.files.util.DebouncedRunnable(
        android.os.Handler(android.os.Looper.getMainLooper()), 2000
    ) {
        // Offload to background
        android.os.AsyncTask.execute { persist() }
    }

	fun getOrDetect(path: java8.nio.file.Path, attributes: BasicFileAttributes): MimeType {
		ensureLoaded()
		val key = path.toString()
		val lm = try { attributes.lastModifiedTime().toMillis() } catch (_: Exception) { -1L }
		val size = try { attributes.size() } catch (_: Exception) { -1L }
		val cached = map[key]
		if (cached != null && cached.lastModified == lm && cached.size == size) {
			return cached.mime.asMimeType()
		}
		// Detect and store
		val detected = AndroidFileTypeDetector.getMimeType(path, attributes).asMimeType()
		map[key] = Entry(detected.value, lm, size)
		// Persist asynchronously to avoid I/O on calling thread
		persistRunnable()
		return detected
	}
}


