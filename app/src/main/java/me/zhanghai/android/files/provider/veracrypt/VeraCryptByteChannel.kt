/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.OpenOption
import java8.nio.file.StandardOpenOption
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.FileSystem as EdsFileSystem
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

internal class VeraCryptByteChannel(
    private val path: VeraCryptPath,
    private val options: Set<OpenOption>
) : SeekableByteChannel {
    @Volatile
    private var closed = false
    private var position: Long = 0L
    private val valIsWrite = options.contains(StandardOpenOption.WRITE)

    private var buffer: ByteArray? = null
    private var bufferSize = 0

    val internalPathString: String
        get() = path.internalPathString

    init {
        path.getFileSystem().registerChannel(this)
    }

    fun closeIo() {
        synchronized(path.getFileSystem()) {
            buffer = null
            bufferSize = 0
        }
    }

    override fun read(dst: ByteBuffer): Int =
        path.getFileSystem().withLock { innerFs ->
            if (closed) throw ClosedChannelException()
            val remaining = dst.remaining()
            if (remaining <= 0) return@withLock 0

            // If reading in read-only mode, buffer files up to 8MB in memory.
            // This reads the entire file in 1 contiguous native read, eliminating native exFAT sector cache
            // interleaving between parallel image thumbnail decoder threads and closing native handles instantly.
            if (!valIsWrite) {
                if (buffer == null) {
                    val innerPath = innerFs.getPath(path.internalPathString)
                    if (innerPath.isDirectory) {
                        throw IOException("Path is a directory: $path")
                    }
                    val file = innerPath.getFile()
                    val fileSize = file.size
                    if (fileSize in 1..8_388_608L) {
                        val array = ByteArray(fileSize.toInt())
                        val io = file.getRandomAccessIO(AccessMode.Read)
                        try {
                            var totalRead = 0
                            while (totalRead < array.size) {
                                val r = io.read(array, totalRead, array.size - totalRead)
                                if (r <= 0) break
                                totalRead += r
                            }
                            buffer = array
                            bufferSize = totalRead
                        } finally {
                            try { io.close() } catch (_: Exception) {}
                        }
                    }
                }

                val buf = buffer
                if (buf != null) {
                    if (position >= bufferSize) {
                        return@withLock -1
                    }
                    val bytesToCopy = minOf(remaining.toLong(), (bufferSize - position)).toInt()
                    dst.put(buf, position.toInt(), bytesToCopy)
                    position += bytesToCopy
                    return@withLock bytesToCopy
                }
            }

            // Fallback for write mode or files larger than 8MB: direct I/O with immediate handle closure per call
            val innerPath = innerFs.getPath(path.internalPathString)
            if (innerPath.isDirectory) {
                throw IOException("Path is a directory: $path")
            }
            val io = innerPath.getFile().getRandomAccessIO(
                if (valIsWrite) AccessMode.ReadWrite else AccessMode.Read
            )
            try {
                io.seek(position)
                val array = ByteArray(remaining)
                val read = io.read(array, 0, array.size)
                if (read > 0) {
                    dst.put(array, 0, read)
                    position += read
                }
                if (read == 0 || read == -1) -1 else read
            } finally {
                try { io.close() } catch (_: Exception) {}
            }
        }

    override fun write(src: ByteBuffer): Int =
        path.getFileSystem().withLock { innerFs ->
            if (closed) throw ClosedChannelException()
            val remaining = src.remaining()
            if (remaining > 0) {
                val innerPath = innerFs.getPath(path.internalPathString)
                if (innerPath.isDirectory) {
                    throw IOException("Path is a directory: $path")
                }
                val io = innerPath.getFile().getRandomAccessIO(AccessMode.ReadWrite)
                try {
                    io.seek(position)
                    val array = ByteArray(remaining)
                    src.get(array)
                    io.write(array, 0, array.size)
                    position += remaining
                    remaining
                } finally {
                    try { io.close() } catch (_: Exception) {}
                }
            } else {
                0
            }
        }

    override fun position(): Long = synchronized(path.getFileSystem()) { position }

    override fun position(newPosition: Long): SeekableByteChannel {
        synchronized(path.getFileSystem()) {
            if (closed) throw ClosedChannelException()
            position = newPosition
            return this
        }
    }

    override fun size(): Long =
        path.getFileSystem().withLock { innerFs ->
            if (closed) throw ClosedChannelException()
            val buf = buffer
            if (buf != null) {
                buf.size.toLong()
            } else {
                val innerPath = innerFs.getPath(path.internalPathString)
                innerPath.getFile().size
            }
        }

    override fun truncate(size: Long): SeekableByteChannel {
        path.getFileSystem().withLock { innerFs ->
            if (closed) throw ClosedChannelException()
            val innerPath = innerFs.getPath(path.internalPathString)
            val io = innerPath.getFile().getRandomAccessIO(
                if (valIsWrite) AccessMode.ReadWrite else AccessMode.Read
            )
            try {
                io.setLength(size)
                if (position > size) {
                    position = size
                }
            } finally {
                try { io.close() } catch (_: Exception) {}
            }
        }
        return this
    }

    override fun isOpen(): Boolean = !closed

    @Throws(IOException::class)
    override fun close() {
        synchronized(path.getFileSystem()) {
            if (closed) return
            closed = true
            buffer = null
            bufferSize = 0
            path.getFileSystem().unregisterChannel(this)
        }
    }
}
