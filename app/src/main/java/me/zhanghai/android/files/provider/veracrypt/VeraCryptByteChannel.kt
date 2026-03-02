/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.OpenOption
import java8.nio.file.StandardOpenOption
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.nio.ByteBuffer

internal class VeraCryptByteChannel(
    private val path: VeraCryptPath,
    private val options: Set<OpenOption>
) : SeekableByteChannel {
    private val randomAccessIO: RandomAccessIO

    init {
        val mode = if (options.contains(StandardOpenOption.WRITE)) {
            AccessMode.ReadWrite
        } else {
            AccessMode.Read
        }
        val innerPath = path.getFileSystem().getInnerFs().getPath(path.toString())
        if (innerPath.isDirectory) {
             throw IOException("Path is a directory: ${path}")
        }
        randomAccessIO = innerPath.getFile().getRandomAccessIO(mode)
    }

    override fun read(dst: ByteBuffer): Int {
        val position = randomAccessIO.filePointer
        val array = ByteArray(dst.remaining())
        val read = randomAccessIO.read(array, 0, array.size)
        if (read > 0) {
            dst.put(array, 0, read)
        }
        return if (read == 0 && dst.hasRemaining()) -1 else read
    }

    override fun write(src: ByteBuffer): Int {
        val array = ByteArray(src.remaining())
        src.get(array)
        randomAccessIO.write(array, 0, array.size)
        return array.size
    }

    override fun position(): Long = randomAccessIO.filePointer

    override fun position(newPosition: Long): SeekableByteChannel {
        randomAccessIO.seek(newPosition)
        return this
    }

    override fun size(): Long = randomAccessIO.length()

    override fun truncate(size: Long): SeekableByteChannel {
        randomAccessIO.setLength(size)
        return this
    }

    override fun isOpen(): Boolean = true // RandomAccessIO doesn't have isOpen, handles it internally or via close

    override fun close() {
        randomAccessIO.close()
    }
}
