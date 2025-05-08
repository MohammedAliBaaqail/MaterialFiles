/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import androidx.core.content.res.ResourcesCompat
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.filelist.FileViewType

object PathSettings {
    private const val NAME_SUFFIX = "path"

    @Suppress("UNCHECKED_CAST")
    fun getFileListViewType(path: Path): SettingLiveData<FileViewType?> =
        EnumSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_view_type, path.toString(),
            ResourcesCompat.ID_NULL, FileViewType::class.java
        ) as SettingLiveData<FileViewType?>

    fun getFileListSortOptions(path: Path): SettingLiveData<FileSortOptions?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_sort_options, path.toString(), null
        )

    fun getFileListSquareThumbnailsInGrid(path: Path): SettingLiveData<Boolean?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_square_thumbnails_in_grid, path.toString(), null
        )

    fun getFileListUsePortraitModeInGrid(path: Path): SettingLiveData<Boolean?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_use_portrait_mode_in_grid, path.toString(), null
        )

    fun getFileListItemScale(path: Path): SettingLiveData<Int?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_item_scale, path.toString(), null
        )
}
