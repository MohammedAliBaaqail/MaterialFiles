/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.root.RootablePath
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class VeraCryptPath : ByteStringListPath<VeraCryptPath>, RootablePath {
    private val fileSystem: VeraCryptFileSystem

    constructor(fileSystem: VeraCryptFileSystem, path: ByteString) : super(
        VeraCryptFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: VeraCryptFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(VeraCryptFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        !path.isEmpty() && path[0] == VeraCryptFileSystem.SEPARATOR

    override fun createPath(path: ByteString): VeraCryptPath = VeraCryptPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): VeraCryptPath =
        VeraCryptPath(fileSystem, absolute, segments)

    override val uriPath: ByteString
        get() = ("/" + fileSystem.containerFile.toUri().toString()).toByteString()

    override val uriQuery: ByteString?
        get() = super.uriPath

    override val defaultDirectory: VeraCryptPath
        get() = fileSystem.rootDirectory

    override fun getFileSystem(): VeraCryptFileSystem = fileSystem

    override fun getRoot(): VeraCryptPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): VeraCryptPath {
        // TODO: Resolve symlinks if VeraCrypt/FAT supports them (usually they don't)
        return toAbsolutePath()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        throw UnsupportedOperationException()
    }

    override fun isRootRequired(isAttributeAccess: Boolean): Boolean {
        val containerFile = fileSystem.containerFile
        return if (containerFile is RootablePath) {
            containerFile.isRootRequired(isAttributeAccess)
        } else {
            false
        }
    }

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<VeraCryptPath> {
            override fun createFromParcel(source: Parcel): VeraCryptPath = VeraCryptPath(source)

            override fun newArray(size: Int): Array<VeraCryptPath?> = arrayOfNulls(size)
        }
    }
}
