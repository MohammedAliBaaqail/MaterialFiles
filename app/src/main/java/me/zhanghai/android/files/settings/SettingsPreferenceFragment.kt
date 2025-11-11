/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference
    private lateinit var tagBackupPreference: TagBackupPreference
    private lateinit var folderItemCountBackupPreference: FolderItemCountBackupPreference
    private lateinit var videoMetadataBackupPreference: VideoMetadataBackupPreference
    private lateinit var fullDataBackupPreference: FullDataBackupPreference

    private val logTag = "SettingsPrefFragment"

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
        
        // Initialize the tag backup preference
        tagBackupPreference = preferenceScreen.findPreference("file_tags_backup")!!
        Log.d(logTag, "Found TagBackupPreference by key 'file_tags_backup': $tagBackupPreference")
        tagBackupPreference.registerForActivityResult(requireActivity() as AppCompatActivity)
        Log.d(logTag, "Registered TagBackupPreference for activity results (onCreatePreferencesFix)")
        
        // Initialize the folder item count backup preference
        folderItemCountBackupPreference = preferenceScreen.findPreference("folder_item_counts_backup")!!
        Log.d(logTag, "Found FolderItemCountBackupPreference by key 'folder_item_counts_backup': $folderItemCountBackupPreference")
        folderItemCountBackupPreference.registerForActivityResult(requireActivity() as AppCompatActivity)
        Log.d(logTag, "Registered FolderItemCountBackupPreference for activity results (onCreatePreferencesFix)")
        
        // Initialize the video metadata backup preference
        videoMetadataBackupPreference = preferenceScreen.findPreference("video_thumbnails_backup")!!
        Log.d(logTag, "Found VideoMetadataBackupPreference by key 'video_thumbnails_backup': $videoMetadataBackupPreference")
        videoMetadataBackupPreference.registerForActivityResult(requireActivity() as AppCompatActivity)
        Log.d(logTag, "Registered VideoMetadataBackupPreference for activity results (onCreatePreferencesFix)")

        // Initialize full data backup preference
        fullDataBackupPreference = preferenceScreen.findPreference("full_data_backup")!!
        Log.d(logTag, "Found FullDataBackupPreference by key 'full_data_backup': $fullDataBackupPreference")
        fullDataBackupPreference.registerForActivityResult(requireActivity() as AppCompatActivity)
        Log.d(logTag, "Registered FullDataBackupPreference for activity results (onCreatePreferencesFix)")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as SettingsActivity
        // Register the tag backup preference for activity results
        tagBackupPreference.registerForActivityResult(activity)
        Log.d(logTag, "Registered TagBackupPreference for activity results (onActivityCreated)")
        // Register the folder item count backup preference for activity results
        folderItemCountBackupPreference.registerForActivityResult(activity)
        Log.d(logTag, "Registered FolderItemCountBackupPreference for activity results (onActivityCreated)")
        // Register the full data backup preference for activity results
        fullDataBackupPreference.registerForActivityResult(activity)
        Log.d(logTag, "Registered FullDataBackupPreference for activity results (onActivityCreated)")

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }
}
