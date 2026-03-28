/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileaction

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import android.content.Context
import java8.nio.file.Path
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.provider.veracrypt.createVeraCryptRootPath
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.putArgs

class VeraCryptPasswordDialogActivity : AppActivity() {
    private val args by args<VeraCryptPasswordDialogFragment.Args>()

    private lateinit var fragment: VeraCryptPasswordDialogFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            fragment = VeraCryptPasswordDialogFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, VeraCryptPasswordDialogFragment::class.java.name)
            }
        } else {
            fragment = supportFragmentManager.findFragmentByTag(
                VeraCryptPasswordDialogFragment::class.java.name
            ) as VeraCryptPasswordDialogFragment
        }
    }

    override fun finish() {
        if (!fragment.isAdded || fragment.isRemoving) {
            super.finish()
            return
        }
        fragment.onFinish()
        fragment.dismiss()
        // Wait for the dialog to be dismissed before finishing the activity, so that user doesn't
        // see the underlying list abruptly disappearing.
        supportFragmentManager.executePendingTransactions()
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun start(path: Path, context: Context) {
            context.startActivity(
                VeraCryptPasswordDialogActivity::class.createIntent()
                    .putArgs(VeraCryptPasswordDialogFragment.Args(path, listener = { successful ->
                        if (successful) {
                            val rootPath = path.createVeraCryptRootPath()
                            context.startActivity(FileListActivity.createViewIntent(rootPath))
                        }
                    }))
            )
        }
    }
}
