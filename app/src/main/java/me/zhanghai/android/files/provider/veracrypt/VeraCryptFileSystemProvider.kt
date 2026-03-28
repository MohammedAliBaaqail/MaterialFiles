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
import java8.nio.file.StandardOpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.FileSystemCache
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.PathObservableProvider
import me.zhanghai.android.files.provider.common.Searchable
import me.zhanghai.android.files.provider.common.decodedPathByteString
import me.zhanghai.android.files.provider.common.decodedQueryByteString
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.WatchServicePathObservable
import java.io.IOException
import java.net.URI

object VeraCryptFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    const val SCHEME = "veracrypt"

    private val fileSystems = FileSystemCache<Path, VeraCryptFileSystem>()
    
    private val activeFileSystemsLiveData = MutableLiveData<List<VeraCryptFileSystem>>(emptyList())
    internal val activeFileSystems: LiveData<List<VeraCryptFileSystem>> = activeFileSystemsLiveData

    @Volatile
    private var isServiceRunning = false

    override fun getScheme(): String = SCHEME

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val containerFile = uri.containerFile
        val fs = fileSystems.create(containerFile) { newFileSystem(containerFile) }
        updateActiveFileSystems()
        return fs
    }

    override fun newFileSystem(file: Path, env: Map<String, *>): FileSystem {
        val fs = newFileSystem(file)
        updateActiveFileSystems()
        return fs
    }

    private fun newFileSystem(containerFile: Path): VeraCryptFileSystem =
        VeraCryptFileSystem(this, containerFile)

    internal fun getOrNewFileSystem(containerFile: Path): VeraCryptFileSystem {
        val fs = fileSystems.getOrCreate(containerFile) { newFileSystem(containerFile) }
        updateActiveFileSystems()
        return fs
    }

    internal fun getActiveFileSystem(containerFile: Path): VeraCryptFileSystem? {
        val fs = fileSystems.getOrNull(containerFile)
        return if (fs != null && fs.isOpened && !fs.isMountExpired) fs else null
    }

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
        val channel = VeraCryptByteChannel(file, options)
        return if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)) {
            me.zhanghai.android.files.provider.common.NotifyEntryModifiedSeekableByteChannel(channel, file)
        } else {
            channel
        }
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? VeraCryptPath ?: throw ProviderMismatchException(directory.toString())
        val fs = directory.getFileSystem()
        val innerFs = fs.getInnerFs()
        val innerPath = innerFs.getPath(directory.internalPathString)
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
        val innerParentPath = innerFs.getPath(parentPath.internalPathString)
        innerParentPath.getDirectory().createDirectory(directory.fileName.toString())
        LocalWatchService.onEntryCreated(directory)
    }

    override fun delete(path: Path) {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        val innerFs = path.getFileSystem().getInnerFs()
        val innerPath = innerFs.getPath(path.internalPathString)
        val record = if (innerPath.isDirectory) {
            innerPath.getDirectory()
        } else {
            innerPath.getFile()
        }
        record.delete()
        LocalWatchService.onEntryDeleted(path)
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption?) {
        source as? VeraCryptPath ?: throw ProviderMismatchException(source.toString())
        target as? VeraCryptPath ?: throw ProviderMismatchException(target.toString())
        if (source.fileSystem != target.fileSystem) {
            throw IOException("Copying between different VeraCrypt file systems is not supported natively")
        }
        val fs = source.fileSystem
        val innerFs = fs.getInnerFs()
        val innerSourcePath = innerFs.getPath(source.internalPathString)
        val parentTarget = target.parent ?: throw IOException("Cannot copy to root without a name")
        val innerParentTarget = innerFs.getPath(parentTarget.internalPathString)
        
        if (innerSourcePath.isDirectory) {
            // For directories, we'd need a recursive copy. 
            // Standard nio copy(source, target) for directories usually only creates the target directory.
            innerParentTarget.getDirectory().createDirectory(target.fileName.toString())
        } else {
            val sourceFile = innerSourcePath.getFile()
            val targetDir = innerParentTarget.getDirectory()
            val targetFile = targetDir.createFile(target.fileName.toString())
            sourceFile.getInputStream().use { input ->
                targetFile.getOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        LocalWatchService.onEntryCreated(target)
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption?) {
        source as? VeraCryptPath ?: throw ProviderMismatchException(source.toString())
        target as? VeraCryptPath ?: throw ProviderMismatchException(target.toString())
        if (source.fileSystem != target.fileSystem) {
            throw IOException("Moving between different VeraCrypt file systems is not supported natively")
        }
        
        val fs = source.fileSystem
        val innerFs = fs.getInnerFs()
        val innerSourcePath = innerFs.getPath(source.internalPathString)
        val sourceRecord = if (innerSourcePath.isDirectory) innerSourcePath.getDirectory() else innerSourcePath.getFile()
        
        val sourceParent = source.parent
        val targetParent = target.parent
        
        // If parent is different, move it first
        if (sourceParent != targetParent) {
            val innerTargetParent = innerFs.getPath(targetParent?.internalPathString ?: "/")
            sourceRecord.moveTo(innerTargetParent.getDirectory())
        }
        
        // If name is different, rename it
        if (source.fileName != target.fileName) {
            sourceRecord.rename(target.fileName.toString())
        }
        LocalWatchService.onEntryDeleted(source)
        LocalWatchService.onEntryCreated(target)
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

    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        path as? VeraCryptPath ?: throw ProviderMismatchException(path.toString())
        return WatchServicePathObservable(path, intervalMillis)
    }

    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
         throw UnsupportedOperationException()
    }

    internal fun unmountAll() {
        val allFs = fileSystems.getAll()
        for (fs in allFs) {
            try {
                fs.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            fileSystems.remove(fs.containerFile, fs)
        }
        updateActiveFileSystems()
    }

    internal fun removeFileSystem(fileSystem: VeraCryptFileSystem) {
        fileSystems.remove(fileSystem.containerFile, fileSystem)
        updateActiveFileSystems()
    }

    private fun updateActiveFileSystems() {
        val allActive = fileSystems.getAll().filter { !it.isMountExpired }
        activeFileSystemsLiveData.postValue(allActive)
        
        val context = me.zhanghai.android.files.app.application
        val shouldBeRunning = allActive.isNotEmpty()
        if (shouldBeRunning != isServiceRunning) {
            isServiceRunning = shouldBeRunning
            if (shouldBeRunning) {
                VeraCryptService.start(context)
            } else {
                VeraCryptService.stop(context)
            }
        }
    }
}
