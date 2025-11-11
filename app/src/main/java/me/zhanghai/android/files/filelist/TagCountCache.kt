package me.zhanghai.android.files.filelist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileTagManager

object TagCountCache {
	private val executor = Executors.newFixedThreadPool(1)
	private val dirPathToCounts = ConcurrentHashMap<String, Map<String, Int>>()

	fun get(directoryPath: Path): Map<String, Int>? =
		dirPathToCounts[directoryPath.toString()]

	fun computeAsync(
		directoryPath: Path,
		files: List<FileItem>,
		onDone: (Map<String, Int>) -> Unit
	) {
		// If cached, return immediately on caller thread
		val cached = get(directoryPath)
		if (cached != null) {
			onDone(cached)
			return
		}
		executor.execute {
			val counts = HashMap<String, Int>()
			for (file in files) {
				val tags = FileTagManager.getTagsForFile(file.path)
				for (tag in tags) {
					counts[tag.id] = (counts[tag.id] ?: 0) + 1
				}
			}
			dirPathToCounts[directoryPath.toString()] = counts
			onDone(counts)
		}
	}

	fun put(directoryPath: Path, counts: Map<String, Int>) {
		dirPathToCounts[directoryPath.toString()] = counts
	}

	fun clear() {
		dirPathToCounts.clear()
	}
}


