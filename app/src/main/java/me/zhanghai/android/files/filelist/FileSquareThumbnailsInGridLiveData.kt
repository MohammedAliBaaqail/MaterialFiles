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

class FileSquareThumbnailsInGridLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<Boolean>() {
    private lateinit var pathSquareThumbnailsInGridLiveData: SettingLiveData<Boolean?>
    private var defaultSourceAdded = false

    init {
        // Initialize with the default value
        value = Settings.FILE_LIST_SQUARE_THUMBNAILS_IN_GRID.valueCompat
        
        addSource(pathLiveData) { path ->
            if (this::pathSquareThumbnailsInGridLiveData.isInitialized) {
                removeSource(pathSquareThumbnailsInGridLiveData)
            }
            pathSquareThumbnailsInGridLiveData = PathSettings.getFileListSquareThumbnailsInGrid(path)
            addSource(pathSquareThumbnailsInGridLiveData) { loadValue() }
            
            if (!defaultSourceAdded) {
                addSource(Settings.FILE_LIST_SQUARE_THUMBNAILS_IN_GRID) { loadValue() }
                defaultSourceAdded = true
            }
            
            loadValue()
        }
    }

    private fun loadValue() {
        if (!this::pathSquareThumbnailsInGridLiveData.isInitialized) {
            // Not yet initialized.
            return
        }
        val value = pathSquareThumbnailsInGridLiveData.value 
            ?: Settings.FILE_LIST_SQUARE_THUMBNAILS_IN_GRID.valueCompat
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: Boolean) {
        if (pathSquareThumbnailsInGridLiveData.value != null) {
            pathSquareThumbnailsInGridLiveData.putValue(value)
        } else {
            Settings.FILE_LIST_SQUARE_THUMBNAILS_IN_GRID.putValue(value)
        }
    }
} 