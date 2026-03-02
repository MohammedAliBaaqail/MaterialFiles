/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.content.Context
import java8.nio.file.Path
import me.zhanghai.android.files.fileaction.VeraCryptPasswordDialogActivity
import me.zhanghai.android.files.fileaction.VeraCryptPasswordDialogFragment
import me.zhanghai.android.files.provider.common.UserAction
import me.zhanghai.android.files.provider.common.UserActionRequiredException
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class VeraCryptPasswordRequiredException(
    val containerFile: Path,
    message: String? = null
) : UserActionRequiredException(containerFile.toString(), null, message) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        val veraCryptRootPath = containerFile.createVeraCryptRootPath()
        return UserAction(
            VeraCryptPasswordDialogActivity::class.createIntent().putArgs(
                VeraCryptPasswordDialogFragment.Args(
                    veraCryptRootPath, message
                ) { continuation.resume(it) }
            ), VeraCryptPasswordDialogFragment.getTitle(context),
            VeraCryptPasswordDialogFragment.getMessage(containerFile, context)
        )
    }
}
