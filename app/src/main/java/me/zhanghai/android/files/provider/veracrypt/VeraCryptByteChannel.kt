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
import java.nio.channels.ClosedChannelException

internal class VeraCryptByteChannel(
    private val path: VeraCryptPath,
    private val options: Set<OpenOption>
) : SeekableByteChannel {
    private var closed = false
    private val lock = Any()
    private val randomAccessIO: RandomAccessIO

    init {
        val mode = if (options.contains(StandardOpenOption.WRITE)) {
            AccessMode.ReadWrite
        } else {
            AccessMode.Read
        }
        val innerFs = path.getFileSystem().getInnerFs()
        val innerPath = innerFs.getPath(path.internalPathString)
        if (innerPath.isDirectory) {
             throw IOException("Path is a directory: ${path}")
        }
        
        var lastException: IOException? = null
        var io: RandomAccessIO? = null
        // Increase retries to 10 times with slightly longer wait for container stability
        for (i in 0 until 10) {
            try {
                io = innerPath.getFile().getRandomAccessIO(mode)
                break
            } catch (e: IOException) {
                lastException = e
                if (e.javaClass.name.endsWith("FileInUseException")) {
                    Thread.sleep(150L + (i * 50)) // Increasing backoff
                    continue
                }
                throw e
            }
        }
        randomAccessIO = io ?: throw lastException!!
    }

    override fun read(dst: ByteBuffer): Int {
        synchronized(lock) {
            if (closed) throw ClosedChannelException()
            val remaining = dst.remaining()
            if (remaining == 0) return 0
            val array = ByteArray(remaining)
            val read = try {
                randomAccessIO.read(array, 0, array.size)
            } catch (e: IOException) {
                throw e
            }
            if (read > 0) {
                dst.put(array, 0, read)
            }
            return if (read == 0 || read == -1) -1 else read
        }
    }

    override fun write(src: ByteBuffer): Int {
        synchronized(lock) {
            if (closed) throw ClosedChannelException()
            val array = ByteArray(src.remaining())
            src.get(array)
            randomAccessIO.write(array, 0, array.size)
            return array.size
        }
    }

    override fun position(): Long = synchronized(lock) { randomAccessIO.filePointer }

    override fun position(newPosition: Long): SeekableByteChannel {
        synchronized(lock) {
            if (closed) throw ClosedChannelException()
            randomAccessIO.seek(newPosition)
            return this
        }
    }

    override fun size(): Long = synchronized(lock) { randomAccessIO.length() }

    override fun truncate(size: Long): SeekableByteChannel {
        synchronized(lock) {
            if (closed) throw ClosedChannelException()
            randomAccessIO.setLength(size)
            return this
        }
    }

    override fun isOpen(): Boolean = !closed

    @Throws(IOException::class)
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            randomAccessIO.close()
        }
    }
}
