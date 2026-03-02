/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import java8.nio.file.Path
import java.io.IOException

fun MediaMetadataRetriever.setDataSource(path: Path) {
    if (path.fileSystem.provider().scheme == "file") {
        setDataSource(path.toString())
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        setDataSource(PathMediaDataSource(path))
    } else {
        // Fallback for older versions: copy to a temporary file.
        // This is slow but better than nothing.
        // However, most devices should be on M+ now.
        throw IOException("MediaMetadataRetriever setDataSource(Path) not supported on API < 23 for custom providers")
    }
}

@android.annotation.TargetApi(Build.VERSION_CODES.M)
private class PathMediaDataSource(private val path: Path) : MediaDataSource() {
    private var channel = path.newByteChannel()

    @Throws(IOException::class)
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        channel.position(position)
        val readBuffer = java.nio.ByteBuffer.wrap(buffer, offset, size)
        return channel.read(readBuffer)
    }

    @Throws(IOException::class)
    override fun getSize(): Long = channel.size()

    @Throws(IOException::class)
    override fun close() {
        channel.close()
    }
}
