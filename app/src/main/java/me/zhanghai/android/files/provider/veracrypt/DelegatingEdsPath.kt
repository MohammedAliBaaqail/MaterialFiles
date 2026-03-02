/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.channels.SeekableByteChannel
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.StandardOpenOption
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.FileSystem as EdsFileSystem
import com.sovworks.eds.fs.Path as EdsPath
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.nio.ByteBuffer

internal class DelegatingEdsPath(val nioPath: Path) : EdsPath {
    override fun getFileSystem(): EdsFileSystem? = null // Not used for the container file itself in this context

    override fun getPathString(): String = nioPath.toString()

    override fun exists(): Boolean = Files.exists(nioPath)

    override fun isFile(): Boolean = Files.isRegularFile(nioPath)

    override fun isDirectory(): Boolean = Files.isDirectory(nioPath)

    override fun getPathDesc(): String = nioPath.fileName.toString()

    override fun isRootDirectory(): Boolean = nioPath.parent == null

    override fun combine(part: String): EdsPath = DelegatingEdsPath(nioPath.resolve(part))

    override fun getDirectory(): Directory {
        throw UnsupportedOperationException("Not implemented for DelegatingEdsPath")
    }

    override fun getFile(): com.sovworks.eds.fs.File = object : com.sovworks.eds.fs.File {
        override fun getPath(): EdsPath = this@DelegatingEdsPath
        override fun getName(): String = nioPath.fileName.toString()
        override fun rename(newName: String) { throw UnsupportedOperationException() }
        override fun getLastModified(): java.util.Date = java.util.Date(Files.getLastModifiedTime(nioPath).toMillis())
        override fun setLastModified(dt: java.util.Date) { throw UnsupportedOperationException() }
        override fun delete() { throw UnsupportedOperationException() }
        override fun moveTo(newParent: Directory) { throw UnsupportedOperationException() }
        override fun getInputStream(): java.io.InputStream = Files.newInputStream(nioPath)
        override fun getOutputStream(): java.io.OutputStream = Files.newOutputStream(nioPath)
        override fun getFileDescriptor(accessMode: AccessMode): android.os.ParcelFileDescriptor { throw UnsupportedOperationException() }
        override fun copyToOutputStream(output: java.io.OutputStream, offset: Long, count: Long, progressInfo: com.sovworks.eds.fs.File.ProgressInfo?) { throw UnsupportedOperationException() }
        override fun copyFromInputStream(input: java.io.InputStream, offset: Long, count: Long, progressInfo: com.sovworks.eds.fs.File.ProgressInfo?) { throw UnsupportedOperationException() }
        override fun getSize(): Long = Files.size(nioPath)

        override fun getRandomAccessIO(mode: AccessMode): RandomAccessIO {
            val options = if (mode == AccessMode.Read) {
                setOf(StandardOpenOption.READ)
            } else {
                setOf(StandardOpenOption.READ, StandardOpenOption.WRITE)
            }
            val channel = Files.newByteChannel(nioPath, options)
            return ChannelRandomAccessIO(channel)
        }
    }

    override fun getParentPath(): EdsPath? = nioPath.parent?.let { DelegatingEdsPath(it) }

    override fun compareTo(other: EdsPath): Int = getPathString().compareTo(other.getPathString())
}

private class ChannelRandomAccessIO(val channel: SeekableByteChannel) : RandomAccessIO {
    override fun read(): Int {
        val buf = ByteBuffer.allocate(1)
        return if (channel.read(buf) == -1) -1 else buf.get(0).toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        val byteBuffer = ByteBuffer.wrap(buffer, offset, count)
        val read = channel.read(byteBuffer)
        return if (read == -1) 0 else read // Note: Some random access implementations expect 0 at EOF
    }

    override fun write(b: Int) {
        val buf = ByteBuffer.allocate(1)
        buf.put(b.toByte())
        buf.flip()
        channel.write(buf)
    }

    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        val byteBuffer = ByteBuffer.wrap(buffer, offset, count)
        channel.write(byteBuffer)
    }

    override fun seek(position: Long) {
        channel.position(position)
    }

    override fun getFilePointer(): Long = channel.position()

    override fun length(): Long = channel.size()

    override fun setLength(length: Long) {
        throw UnsupportedOperationException("setLength not supported for ChannelRandomAccessIO")
    }

    override fun flush() {
        // SeekableByteChannel doesn't have flush, but FileChannel does via force()
    }

    override fun close() {
        channel.close()
    }
}
