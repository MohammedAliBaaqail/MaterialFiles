/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.file.FileStore
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.attribute.FileStoreAttributeView
import java.io.IOException

internal class VeraCryptFileStore(
    private val fileSystem: VeraCryptFileSystem
) : FileStore() {
    override fun name(): String = fileSystem.containerFile.toString()

    override fun type(): String = "veracrypt"

    override fun isReadOnly(): Boolean = false

    @Throws(IOException::class)
    override fun getTotalSpace(): Long =
        if (fileSystem.isOpened) {
            try {
                fileSystem.withLock { innerFs ->
                    innerFs.getRootPath().getDirectory().getTotalSpace()
                }
            } catch (_: Exception) {
                0L
            }
        } else 0L

    @Throws(IOException::class)
    override fun getUsableSpace(): Long =
        if (fileSystem.isOpened) {
            try {
                fileSystem.withLock { innerFs ->
                    innerFs.getRootPath().getDirectory().getFreeSpace()
                }
            } catch (_: Exception) {
                0L
            }
        } else 0L

    @Throws(IOException::class)
    override fun getUnallocatedSpace(): Long = getUsableSpace()

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        VeraCryptFileAttributeView.SUPPORTED_NAMES.contains(type.simpleName.lowercase())

    override fun supportsFileAttributeView(name: String): Boolean =
        VeraCryptFileAttributeView.SUPPORTED_NAMES.contains(name.lowercase())

    override fun <V : FileStoreAttributeView> getFileStoreAttributeView(type: Class<V>): V? = null

    override fun getAttribute(attribute: String): Any {
        throw UnsupportedOperationException()
    }
}
