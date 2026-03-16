/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.PosixFileAttributeView
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import java.io.IOException
import java.util.Date

internal class VeraCryptFileAttributeView(
    private val path: VeraCryptPath,
    private val options: Array<out LinkOption>
) : PosixFileAttributeView {
    override fun name(): String = "veracrypt"

    @Throws(IOException::class)
    override fun readAttributes(): PosixFileAttributes {
        val innerFs = path.getFileSystem().getInnerFs()
        val innerPath = innerFs.getPath(path.internalPathString)
        if (!innerPath.exists()) {
            throw NoSuchFileException(path.toString())
        }
        val stat = if (innerPath.isDirectory) {
            val dir = innerPath.getDirectory()
            com.sovworks.eds.fs.util.FileStat().apply {
                fileName = innerPath.pathDesc
                isDir = true
                size = 0
                modTime = try { dir.lastModified.time } catch (e: Exception) { 0L }
            }
        } else {
            val file = innerPath.getFile()
            com.sovworks.eds.fs.util.FileStat().apply {
                fileName = innerPath.pathDesc
                isDir = false
                size = try { file.size } catch (e: Exception) { 0L }
                modTime = try { file.lastModified.time } catch (e: Exception) { 0L }
            }
        }
        return VeraCryptFileAttributes.from(path.getFileSystem().containerFile, path.internalPathString, stat)
    }

    override fun setTimes(
        lastModifiedTime: java8.nio.file.attribute.FileTime?,
        lastAccessTime: java8.nio.file.attribute.FileTime?,
        createTime: java8.nio.file.attribute.FileTime?
    ) {
        if (lastModifiedTime == null) return
        val innerFs = path.getFileSystem().getInnerFs()
        val innerPath = innerFs.getPath(path.internalPathString)
        val record = if (innerPath.isDirectory) innerPath.getDirectory() else innerPath.getFile()
        record.lastModified = Date(lastModifiedTime.toMillis())
    }

    override fun setOwner(owner: PosixUser) {
        throw UnsupportedOperationException()
    }

    override fun setGroup(group: PosixGroup) {
        throw UnsupportedOperationException()
    }

    override fun setMode(mode: Set<PosixFileModeBit>) {
        throw UnsupportedOperationException()
    }

    override fun setSeLinuxContext(context: ByteString) {
        throw UnsupportedOperationException()
    }

    override fun restoreSeLinuxContext() {
        throw UnsupportedOperationException()
    }

    companion object {
        val SUPPORTED_NAMES = setOf("basic", "posix", "veracrypt")
    }
}
