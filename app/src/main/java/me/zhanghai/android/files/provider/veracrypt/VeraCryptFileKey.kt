/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.os.Parcelable
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.util.ParcelableParceler

@Parcelize
internal data class VeraCryptFileKey(
    private val containerFile: @WriteWith<ParcelableParceler> Path,
    private val entryPath: String
) : Parcelable
