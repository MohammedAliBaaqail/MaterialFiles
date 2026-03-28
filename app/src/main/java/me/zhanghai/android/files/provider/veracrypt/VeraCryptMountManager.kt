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
    private const val DEFAULT_TIMEOUT_SECONDS = 24 * 60 * 60L // 24 hours

    private val sharedPreferences: SharedPreferences by lazy {
        application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getTimeoutSeconds(containerPath: Path): Long =
        sharedPreferences.getLong("${containerPath}_timeout", DEFAULT_TIMEOUT_SECONDS)

    fun onMounted(containerPath: Path, timeoutSeconds: Long) {
        sharedPreferences.edit()
            .putLong("${containerPath}_time", System.currentTimeMillis())
            .putLong("${containerPath}_timeout", timeoutSeconds)
            .apply()
    }

    fun getRemainingTimeSeconds(containerPath: Path): Long {
        val mountTime = sharedPreferences.getLong("${containerPath}_time", 0)
        if (mountTime == 0L) return 0
        val timeout = sharedPreferences.getLong("${containerPath}_timeout", DEFAULT_TIMEOUT_SECONDS)
        val elapsed = (System.currentTimeMillis() - mountTime) / 1000
        return (timeout - elapsed).coerceAtLeast(0)
    }

    fun isMountExpired(containerPath: Path): Boolean {
        val mountTime = sharedPreferences.getLong("${containerPath}_time", 0)
        if (mountTime == 0L) return true
        val timeout = sharedPreferences.getLong("${containerPath}_timeout", DEFAULT_TIMEOUT_SECONDS)
        return (System.currentTimeMillis() - mountTime) > (timeout * 1000)
    }

    fun clearMount(containerPath: Path) {
        sharedPreferences.edit()
            .remove("${containerPath}_time")
            .remove("${containerPath}_timeout")
            .apply()
    }
}
