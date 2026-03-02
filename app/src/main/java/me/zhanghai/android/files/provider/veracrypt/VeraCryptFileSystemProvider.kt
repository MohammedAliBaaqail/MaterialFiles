/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.net.Uri
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.FileSystemCache
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.PathObservableProvider
import me.zhanghai.android.files.provider.common.Searchable
import me.zhanghai.android.files.provider.common.decodedPathByteString
import me.zhanghai.android.files.provider.common.decodedQueryByteString
import me.zhanghai.android.files.provider.common.toByteString
import java.io.IOException
import java.net.URI

object VeraCryptFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    const val SCHEME = "veracrypt"

    private val fileSystems = FileSystemCache<Path, VeraCryptFileSystem>()

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val containerFile = uri.containerFile
        return fileSystems.create(containerFile) { newFileSystem(containerFile) }
    }

    override fun newFileSystem(file: Path, env: Map<String, *>): FileSystem = newFileSystem(file)

    private fun newFileSystem(containerFile: Path): VeraCryptFileSystem =
        VeraCryptFileSystem(this, containerFile)

    internal fun getOrNewFileSystem(containerFile: Path): VeraCryptFileSystem =
        fileSystems.getOrCreate(containerFile) { newFileSystem(containerFile) }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val containerFile = uri.containerFile
        return fileSystems[containerFile]
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val containerFile = uri.containerFile
        val path = uri.decodedQueryByteString
            ?: throw IllegalArgumentException("URI must have a query")
        return getOrNewFileSystem(containerFile).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private val URI.containerFile: Path
        get() {
            val path = decodedPathByteString
                ?: throw IllegalArgumentException("URI must have a path")
            // Drop the first character which is always a slash.
            val containerUri = URI.create(path.toString().drop(1))
            return Paths.get(containerUri)
        }

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? VeraCryptPath ?: throw ProviderMismatchException(file.toString())
        return VeraCryptByteChannel(file, options)
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? VeraCryptPath ?: throw ProviderMismatchException(directory.toString())
        val fs = directory.getFileSystem()
        val innerFs = fs.getInnerFs()
        val innerPath = innerFs.getPath(directory.toString())
        val dir = innerPath.getDirectory()
        val contents = dir.list()
        val children = contents.use { it.map { p -> fs.getPath(p.pathString) } }
        return PathListDirectoryStream(children, filter)
    }

    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? VeraCryptPath ?: throw ProviderMismatchException(directory.toString())
        val fs = directory.getFileSystem()
        val innerFs = fs.getInnerFs()
        val parentPath = directory.parent ?: throw IOException("Cannot create root directory")
        val innerParentPath = innerFs.getPath(parentPath.toString())
        innerParentPath.getDirectory().createDirectory(directory.fileName.toString())
    }

    override fun delete(path: Path) {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        val innerFs = path.getFileSystem().getInnerFs()
        val innerPath = innerFs.getPath(path.toString())
        val record = if (innerPath.isDirectory) {
            innerPath.getDirectory()
        } else {
            innerPath.getFile()
        }
        record.delete()
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
        throw UnsupportedOperationException()
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption?) {
        throw UnsupportedOperationException()
    }

    override fun isSameFile(path: Path, path2: Path): Boolean = path == path2

    override fun isHidden(path: Path): Boolean = false

    override fun getFileStore(path: Path): FileStore {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        return VeraCryptFileStore(path.getFileSystem())
    }

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        if (!path.getFileSystem().getInnerFs().getPath(path.toString()).exists()) {
             throw IOException("File not found")
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        if (type.isAssignableFrom(VeraCryptFileAttributeView::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VeraCryptFileAttributeView(path, options) as V
        }
        return null
    }

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        if (!type.isAssignableFrom(VeraCryptFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path, VeraCryptFileAttributeView::class.java, *options)!!.readAttributes() as A
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        throw UnsupportedOperationException()
    }

    override fun observe(path: Path, intervalMillis: Long): me.zhanghai.android.files.provider.common.PathObservable {
         throw UnsupportedOperationException()
    }

    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
         throw UnsupportedOperationException()
    }

    internal fun removeFileSystem(fileSystem: VeraCryptFileSystem) {
        fileSystems.remove(fileSystem.containerFile, fileSystem)
    }
}
