/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException

fun Path.veraCryptAddPassword(password: String, container: com.sovworks.eds.container.EdsContainer? = null, timeoutSeconds: Long = 24 * 3600L) {
    (this as? VeraCryptPath)?.fileSystem?.addPassword(password, container, timeoutSeconds)
        ?: (this.createVeraCryptRootPath() as VeraCryptPath).fileSystem.addPassword(password, container, timeoutSeconds)
}

val Path.isVeraCryptPath: Boolean
    get() = this is VeraCryptPath

val Path.isVeraCryptContainer: Boolean
    get() {
        val name = fileName?.toString()?.lowercase() ?: return false
        return name.endsWith(".hc") || name.endsWith(".tc") || name.endsWith(".vc")
    }

val Path.veraCryptContainerFile: Path
    get() = (this as? VeraCryptPath)?.fileSystem?.containerFile ?: this

fun Path.createVeraCryptRootPath(): Path =
    if (this is VeraCryptPath) {
        fileSystem.rootDirectory
    } else {
        VeraCryptFileSystemProvider.getOrNewFileSystem(this).rootDirectory
    }
