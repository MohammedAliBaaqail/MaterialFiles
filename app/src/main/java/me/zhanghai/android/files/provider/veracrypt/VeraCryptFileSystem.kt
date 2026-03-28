/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.ClosedFileSystemException
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import com.sovworks.eds.android.Logger
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.fs.FileSystem as EdsFileSystem
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringBuilder
import me.zhanghai.android.files.provider.common.ByteStringListPathCreator
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.toByteString
import java.io.IOException
import java.nio.charset.StandardCharsets

class VeraCryptFileSystem(
    private val provider: VeraCryptFileSystemProvider,
    val containerFile: Path
) : FileSystem(), ByteStringListPathCreator, Parcelable {

    val rootDirectory = VeraCryptPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory $rootDirectory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory $rootDirectory must contain no names")
        }
    }

    private val lock = Any()
    private var isOpen = true
    private var passwords = mutableListOf<ByteArray>()
    private var edsContainer: EdsContainer? = null
    private var innerFs: EdsFileSystem? = null
    val isOpened: Boolean
        get() = synchronized(lock) { innerFs != null }

    val isMountExpired: Boolean
        get() = VeraCryptMountManager.isMountExpired(containerFile)

    @Throws(IOException::class)
    fun getInnerFs(): EdsFileSystem {
        synchronized(lock) {
            if (!isOpen) throw ClosedFileSystemException()
            
            // Expiration check
            if (isMountExpired) {
                clearCachedState()
                throw VeraCryptPasswordRequiredException(containerFile, "Mount expired")
            }

            innerFs?.let { return it }
            
            Logger.debug("Mounting VeraCrypt container: $containerFile")
            val container = EdsContainer(DelegatingEdsPath(containerFile))
            var lastException: Exception? = null
            
            if (passwords.isNotEmpty()) {
                val currentPasswords = passwords.toList()
                passwords.clear()
                for (pass in currentPasswords) {
                    try {
                        container.open(pass)
                        Logger.debug("Successfully opened container header.")
                        edsContainer = container
                        Logger.debug("Loading embedded file system...")
                        val fs = container.getEncryptedFS()
                        Logger.debug("File system loaded successfully.")
                        innerFs = fs
                        // Cache the successful password
                        passwords.add(pass)
                        // Record mount time
                        VeraCryptMountManager.onMounted(containerFile, VeraCryptMountManager.getTimeoutSeconds(containerFile))
                        return fs
                    } catch (e: Exception) {
                        Logger.debug("Failed to mount container with password: ${e.message}")
                        lastException = e
                    }
                }
            }
            
            throw if (lastException != null) {
                VeraCryptPasswordRequiredException(containerFile, lastException.message)
            } else {
                VeraCryptPasswordRequiredException(containerFile)
            }
        }
    }

    fun addPassword(password: String, openedContainer: EdsContainer? = null, timeoutSeconds: Long = 24 * 3600L) {
        synchronized(lock) {
            if (!isOpen) {
                openedContainer?.close()
                throw ClosedFileSystemException()
            }
            passwords.add(password.toByteArray(StandardCharsets.UTF_8))
            // Store the mount info
            VeraCryptMountManager.onMounted(containerFile, timeoutSeconds)
            // Clear cached FS to retry with new password if needed
            innerFs = null
            edsContainer?.close()
            if (openedContainer != null) {
                Logger.debug("Using already opened container for $containerFile")
                edsContainer = openedContainer
                try {
                    innerFs = openedContainer.getEncryptedFS()
                } catch (e: Exception) {
                    Logger.debug("Failed to get inner FS from handed-over container: ${e.message}")
                    edsContainer = null
                    openedContainer.close()
                }
            } else {
                edsContainer = null
            }
        }
    }

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) return
            isOpen = false
            clearCachedState()
            provider.removeFileSystem(this)
        }
    }

    private fun clearCachedState() {
        synchronized(lock) {
            innerFs = null
            edsContainer?.close()
            edsContainer = null
            passwords.clear()
            VeraCryptMountManager.clearMount(containerFile)
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        VeraCryptFileAttributeView.SUPPORTED_NAMES

    override fun getPath(first: String, vararg more: String): VeraCryptPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return VeraCryptPath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): VeraCryptPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return VeraCryptPath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService {
        if (!isOpen) throw ClosedFileSystemException()
        return LocalWatchService()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VeraCryptFileSystem
        return containerFile == other.containerFile
    }

    override fun hashCode(): Int = containerFile.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(containerFile as Parcelable, flags)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        const val SEPARATOR_STRING = "/"

        @JvmField
        val CREATOR = object : Parcelable.Creator<VeraCryptFileSystem> {
            override fun createFromParcel(source: Parcel): VeraCryptFileSystem {
                val containerFile = source.readParcelable<Parcelable>(Path::class.java.classLoader) as Path
                return VeraCryptFileSystemProvider.getOrNewFileSystem(containerFile)
            }

            override fun newArray(size: Int): Array<VeraCryptFileSystem?> = arrayOfNulls(size)
        }
    }
}
