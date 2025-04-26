/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileRatingManager
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.hash

@Parcelize
data class FileSortOptions(
    val by: By,
    val order: Order,
    val isDirectoriesFirst: Boolean
) : Parcelable {
    enum class By {
        NAME,
        TYPE,
        SIZE,
        LAST_MODIFIED,
        RATING
    }

    enum class Order {
        ASCENDING,
        DESCENDING
    }

    fun createComparator(): Comparator<FileItem> {
        var comparator: Comparator<FileItem> = ByComparator(by)
        if (order == Order.DESCENDING) {
            comparator = comparator.reversed()
        }
        if (isDirectoriesFirst) {
            comparator = DirectoriesFirstComparator().then(comparator)
        }
        return comparator
    }

    private class DirectoriesFirstComparator : Comparator<FileItem> {
        override fun compare(file1: FileItem, file2: FileItem): Int {
            val isDirectory1 = file1.attributes.isDirectory
            val isDirectory2 = file2.attributes.isDirectory
            return if (isDirectory1 && !isDirectory2) {
                -1
            } else if (!isDirectory1 && isDirectory2) {
                1
            } else {
                0
            }
        }
    }

    private class ByComparator(private val by: By) : Comparator<FileItem> {
        override fun compare(file1: FileItem, file2: FileItem): Int =
            when (by) {
                By.NAME -> compareByName(file1, file2)
                By.TYPE -> compareByType(file1, file2)
                By.SIZE -> compareBySize(file1, file2)
                By.LAST_MODIFIED -> compareByLastModified(file1, file2)
                By.RATING -> compareByRating(file1, file2)
            }

        private fun compareByName(file1: FileItem, file2: FileItem): Int =
            file1.name.compareTo(file2.name, true)

        private fun compareByType(file1: FileItem, file2: FileItem): Int {
            val isDirectory1 = file1.attributes.isDirectory
            val isDirectory2 = file2.attributes.isDirectory
            return if (isDirectory1 && isDirectory2) {
                compareByName(file1, file2)
            } else if (isDirectory1) {
                -1
            } else if (isDirectory2) {
                1
            } else {
                val extension1 = file1.extension
                val extension2 = file2.extension
                if (extension1.isEmpty() && extension2.isEmpty()) {
                    compareByName(file1, file2)
                } else if (extension1.isEmpty()) {
                    -1
                } else if (extension2.isEmpty()) {
                    1
                } else {
                    val result = extension1.compareTo(extension2, true)
                    if (result != 0) result else compareByName(file1, file2)
                }
            }
        }

        private fun compareBySize(file1: FileItem, file2: FileItem): Int {
            val isDirectory1 = file1.attributes.isDirectory
            val isDirectory2 = file2.attributes.isDirectory
            return if (isDirectory1 && isDirectory2) {
                compareByName(file1, file2)
            } else if (isDirectory1) {
                -1
            } else if (isDirectory2) {
                1
            } else {
                val size1 = file1.attributes.size()
                val size2 = file2.attributes.size()
                val result = size1.compareTo(size2)
                if (result != 0) result else compareByName(file1, file2)
            }
        }

        private fun compareByLastModified(file1: FileItem, file2: FileItem): Int {
            val lastModifiedTime1 = file1.attributes.lastModifiedTime()
            val lastModifiedTime2 = file2.attributes.lastModifiedTime()
            val result = lastModifiedTime1.compareTo(lastModifiedTime2)
            return if (result != 0) result else compareByName(file1, file2)
        }
        
        private fun compareByRating(file1: FileItem, file2: FileItem): Int {
            val rating1 = FileRatingManager.getRating(file1.path)
            val rating2 = FileRatingManager.getRating(file2.path)
            val result = if (rating1 == 0 && rating2 == 0) {
                0
            } else if (rating1 == 0) {
                1 // Files with no rating come last
            } else if (rating2 == 0) {
                -1 // Files with no rating come last
            } else {
                // Since 1 is better than 9, we reverse the comparison
                rating1.compareTo(rating2)
            }
            return if (result != 0) result else compareByName(file1, file2)
        }
    }

    companion object {
        val DEFAULT = FileSortOptions(By.NAME, Order.ASCENDING, true)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as FileSortOptions
        return by == other.by && order == other.order && isDirectoriesFirst == other.isDirectoriesFirst
    }

    override fun hashCode(): Int = hash(by, order, isDirectoriesFirst)
}
