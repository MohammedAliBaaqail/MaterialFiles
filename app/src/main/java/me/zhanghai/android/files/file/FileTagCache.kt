package me.zhanghai.android.files.file

import java.util.concurrent.ConcurrentHashMap
import java8.nio.file.Path

/**
 * In-memory cache for tags per file path.
 * Keep it lightweight; it is cleared or selectively invalidated by FileTagManager on changes.
 */
object FileTagCache {
	private val pathToTags = ConcurrentHashMap<String, List<FileTag>>()

	fun get(path: Path): List<FileTag>? = pathToTags[path.toString()]

	fun put(path: Path, tags: List<FileTag>) {
		pathToTags[path.toString()] = tags
	}

	fun invalidate(path: Path) {
		pathToTags.remove(path.toString())
	}

	fun invalidate(paths: Iterable<Path>) {
		for (p in paths) pathToTags.remove(p.toString())
	}

	fun clear() {
		pathToTags.clear()
	}
}


