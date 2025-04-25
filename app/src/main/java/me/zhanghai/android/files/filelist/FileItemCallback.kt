package me.zhanghai.android.files.filelist

import androidx.recyclerview.widget.DiffUtil
import me.zhanghai.android.files.file.FileItem

class FileItemCallback : DiffUtil.ItemCallback<FileItem>() {
    override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
        oldItem.path == newItem.path

    override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
        oldItem == newItem
} 