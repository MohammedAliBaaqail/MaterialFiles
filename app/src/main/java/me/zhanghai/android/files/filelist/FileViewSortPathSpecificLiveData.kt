/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
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

class FileViewSortPathSpecificLiveData(pathLiveData: LiveData<Path>) : MediatorLiveData<Boolean>() {
    private lateinit var pathViewTypeLiveData: SettingLiveData<FileViewType?>
    private lateinit var pathSortOptionsLiveData: SettingLiveData<FileSortOptions?>
    private lateinit var pathSquareThumbnailsInGridLiveData: SettingLiveData<Boolean?>
    private lateinit var pathPortraitModeInGridLiveData: SettingLiveData<Boolean?>
    private lateinit var pathItemScaleLiveData: SettingLiveData<Int?>

    private fun loadValue() {
        val value = pathViewTypeLiveData.value != null || 
                   pathSortOptionsLiveData.value != null ||
                   pathSquareThumbnailsInGridLiveData.value != null ||
                   pathPortraitModeInGridLiveData.value != null ||
                   pathItemScaleLiveData.value != null
        if (this.value != value) {
            this.value = value
        }
    }

    fun putValue(value: Boolean) {
        if (value) {
                pathViewTypeLiveData.putValue(Settings.FILE_LIST_VIEW_TYPE.valueCompat)
                pathSortOptionsLiveData.putValue(Settings.FILE_LIST_SORT_OPTIONS.valueCompat)
            pathSquareThumbnailsInGridLiveData.putValue(Settings.FILE_LIST_SQUARE_THUMBNAILS_IN_GRID.valueCompat)
            pathPortraitModeInGridLiveData.putValue(Settings.FILE_LIST_USE_PORTRAIT_MODE_IN_GRID.valueCompat)
            pathItemScaleLiveData.putValue(Settings.FILE_LIST_ITEM_SCALE.valueCompat)
        } else {
                pathViewTypeLiveData.putValue(null)
                pathSortOptionsLiveData.putValue(null)
            pathSquareThumbnailsInGridLiveData.putValue(null)
            pathPortraitModeInGridLiveData.putValue(null)
            pathItemScaleLiveData.putValue(null)
        }
    }

    init {
        addSource(pathLiveData) { path: Path ->
            if (this::pathViewTypeLiveData.isInitialized) {
                removeSource(pathViewTypeLiveData)
            }
            if (this::pathSortOptionsLiveData.isInitialized) {
                removeSource(pathSortOptionsLiveData)
            }
            if (this::pathSquareThumbnailsInGridLiveData.isInitialized) {
                removeSource(pathSquareThumbnailsInGridLiveData)
            }
            if (this::pathPortraitModeInGridLiveData.isInitialized) {
                removeSource(pathPortraitModeInGridLiveData)
            }
            if (this::pathItemScaleLiveData.isInitialized) {
                removeSource(pathItemScaleLiveData)
            }
            pathViewTypeLiveData = PathSettings.getFileListViewType(path)
            pathSortOptionsLiveData = PathSettings.getFileListSortOptions(path)
            pathSquareThumbnailsInGridLiveData = PathSettings.getFileListSquareThumbnailsInGrid(path)
            pathPortraitModeInGridLiveData = PathSettings.getFileListUsePortraitModeInGrid(path)
            pathItemScaleLiveData = PathSettings.getFileListItemScale(path)
            
            // If this is the first time visiting this path,
            // enable path-specific settings by default
            if (pathViewTypeLiveData.value == null && 
                pathSortOptionsLiveData.value == null &&
                pathSquareThumbnailsInGridLiveData.value == null &&
                pathPortraitModeInGridLiveData.value == null &&
                pathItemScaleLiveData.value == null) {
                // Enable path-specific sorting by default
                putValue(true)
            } else {
                // Path has existing settings, let loadValue determine state
                loadValue()
            }
            
            // Add sources to observe changes
            addSource(pathViewTypeLiveData) { loadValue() }
            addSource(pathSortOptionsLiveData) { loadValue() }
            addSource(pathSquareThumbnailsInGridLiveData) { loadValue() }
            addSource(pathPortraitModeInGridLiveData) { loadValue() }
            addSource(pathItemScaleLiveData) { loadValue() }
        }
    }
}
