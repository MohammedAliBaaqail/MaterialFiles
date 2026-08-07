/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.webdav.client

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.CreationDate
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import java.time.Instant

val Response.creationTime: Instant?
    get() = (this.get(CreationDate::class.java) as? CreationDate)?.creationDate?.let {
        try {
            Instant.parse(it)
        } catch (e: Exception) {
            HttpUtils.parseDate(it)?.toInstant()
        }
    }

val Response.isDirectory: Boolean
    get() = (this.get(ResourceType::class.java) as? ResourceType)?.types?.contains(ResourceType.COLLECTION) == true

val Response.isSymbolicLink: Boolean
    get() = newLocation != null

val Response.lastModifiedTime: Instant?
    get() = (this.get(GetLastModified::class.java) as? GetLastModified)?.lastModified?.let { Instant.ofEpochMilli(it) }

val Response.size: Long
    get() = (this.get(GetContentLength::class.java) as? GetContentLength)?.contentLength ?: 0
