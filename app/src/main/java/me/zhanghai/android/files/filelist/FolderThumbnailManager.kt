package me.zhanghai.android.files.filelist

import android.content.Context
import java.io.File
import java.security.MessageDigest

object FolderThumbnailManager {
    const val THUMBNAIL_DIR = "folder_thumbnails"

    fun getFolderThumbnail(context: Context, folderPath: File): File? {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(folderPath.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val thumbnailDir = File(context.filesDir, THUMBNAIL_DIR)
        val thumbnailFile = File(thumbnailDir, "$hash.jpg")
        return if (thumbnailFile.exists()) thumbnailFile else null
    }
}
