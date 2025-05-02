/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.settings.PathSettings
import me.zhanghai.android.files.settings.SettingLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

class FileItemScaleLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<Int>() {
    private lateinit var pathItemScaleLiveData: SettingLiveData<Int?>
    private var defaultSourceAdded = false

    init {
        // Initialize with the default value
        value = Settings.FILE_LIST_ITEM_SCALE.valueCompat
        
        addSource(pathLiveData) { path ->
            if (this::pathItemScaleLiveData.isInitialized) {
                removeSource(pathItemScaleLiveData)
            }
            pathItemScaleLiveData = PathSettings.getFileListItemScale(path)
            addSource(pathItemScaleLiveData) { loadValue() }
            
            if (!defaultSourceAdded) {
                addSource(Settings.FILE_LIST_ITEM_SCALE) { loadValue() }
                defaultSourceAdded = true
            }
            
            loadValue()
        }
    }

    private fun loadValue() {
        if (!this::pathItemScaleLiveData.isInitialized) {
            // Not yet initialized.
            return
        }
        val value = pathItemScaleLiveData.value 
            ?: Settings.FILE_LIST_ITEM_SCALE.valueCompat
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: Int) {
        if (pathItemScaleLiveData.value != null) {
            pathItemScaleLiveData.putValue(value)
        } else {
            Settings.FILE_LIST_ITEM_SCALE.putValue(value)
        }
    }
} 