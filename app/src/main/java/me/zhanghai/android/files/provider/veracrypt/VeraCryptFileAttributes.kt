/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.os.Parcelable
import java8.nio.file.Path
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.sovworks.eds.fs.util.FileStat
import me.zhanghai.android.files.provider.common.AbstractPosixFileAttributes
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.EncryptedFileAttributes
import me.zhanghai.android.files.provider.common.FileTimeParceler
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser

@Parcelize
internal class VeraCryptFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: PosixFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    override val owner: PosixUser?,
    override val group: PosixGroup?,
    override val mode: Set<PosixFileModeBit>?,
    override val seLinuxContext: ByteString?,
    private val isEncrypted: Boolean
) : AbstractPosixFileAttributes(), EncryptedFileAttributes {
    override fun isEncrypted(): Boolean = isEncrypted

    companion object {
        fun from(containerFile: Path, entryPath: String, stat: FileStat): VeraCryptFileAttributes {
            val lastModifiedTime = FileTime.fromMillis(stat.modTime)
            val lastAccessTime = lastModifiedTime
            val creationTime = lastModifiedTime
            val type = if (stat.isDir) PosixFileType.DIRECTORY else PosixFileType.REGULAR_FILE
            val size = stat.size
            val fileKey = VeraCryptFileKey(containerFile, entryPath)
            // VeraCrypt containers (FAT32) don't have POSIX attributes.
            val owner = null
            val group = null
            val mode = null
            val seLinuxContext = null
            val isEncrypted = false // Don't show encrypted badge for files inside container
            return VeraCryptFileAttributes(
                lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey, owner, group,
                mode, seLinuxContext, isEncrypted
            )
        }
    }
}
