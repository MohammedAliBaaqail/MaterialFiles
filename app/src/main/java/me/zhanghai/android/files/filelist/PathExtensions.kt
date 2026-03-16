/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.isSupportedArchive
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.document.resolver.DocumentResolver
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.veracrypt.isVeraCryptPath
import me.zhanghai.android.files.provider.veracrypt.veraCryptContainerFile

val Path.name: String
    get() = fileName?.toString() ?: when {
        isArchivePath -> archiveFile.fileName.toString()
        isVeraCryptPath -> veraCryptContainerFile.fileName.toString().substringBeforeLast('.')
        else -> "/"
    }

fun Path.toUserFriendlyString(): String =
    when {
        isLinuxPath -> toFile().path
        isVeraCryptPath -> toString()
        else -> toUri().toString()
    }

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
