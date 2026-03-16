/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.content.Context
import android.content.SharedPreferences
import java8.nio.file.Path
import me.zhanghai.android.files.app.application

internal object VeraCryptMountManager {
    private const val PREF_NAME = "veracrypt_mounts"
    private const val MOUNT_TIMEOUT_MS = 24L * 60 * 60 * 1000 // 24 hours

    private val sharedPreferences: SharedPreferences by lazy {
        application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun onMounted(containerPath: Path) {
        sharedPreferences.edit()
            .putLong(containerPath.toString(), System.currentTimeMillis())
            .apply()
    }

    fun getMountTime(containerPath: Path): Long =
        sharedPreferences.getLong(containerPath.toString(), 0)

    fun clearMount(containerPath: Path) {
        sharedPreferences.edit()
            .remove(containerPath.toString())
            .apply()
    }

    fun isMountExpired(containerPath: Path): Boolean {
        val mountTime = getMountTime(containerPath)
        if (mountTime == 0L) return false
        return (System.currentTimeMillis() - mountTime) > MOUNT_TIMEOUT_MS
    }
}
