/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import coil.dispose
import coil.load
import java8.nio.file.Path
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.isSingleLineCompat
import me.zhanghai.android.files.databinding.FileItemGridBinding
import me.zhanghai.android.files.databinding.FileItemListBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AnimatedListAdapter
import me.zhanghai.android.files.ui.CheckableForegroundLinearLayout
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.ui.TagsView
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible
import java.util.Locale
import android.util.Log
import me.zhanghai.android.files.file.FileRatingManager
import me.zhanghai.android.files.ui.AspectRatioFrameLayout
import android.media.MediaMetadataRetriever
import me.zhanghai.android.files.provider.common.VideoMetadataRepository
import kotlinx.coroutines.*
import me.zhanghai.android.files.app.application
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import me.zhanghai.android.files.util.toHex
import me.zhanghai.android.files.util.pathString
import java.time.Duration

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(FileItemCallback()),
    PopupTextProvider {
    private var isSearching = false

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                replace(list, false)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            if (!isSearching) {
                val sortedList = list.sortedWith(value.createComparator())
                replace(sortedList, false)
                rebuildFilePositionMap()
            }
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<Path, Int>()

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private var _isSquareThumbnailsInGrid: Boolean = false
    var isSquareThumbnailsInGrid: Boolean
        get() = _isSquareThumbnailsInGrid
        set(value) {
            if (_isSquareThumbnailsInGrid == value) {
                return
            }
            _isSquareThumbnailsInGrid = value
            // Force a complete redraw of all items
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SQUARE_THUMBNAILS_CHANGED)
        }
        
    private var _itemScale: Int = 100
    var itemScale: Int
        get() = _itemScale
        set(value) {
            if (_itemScale == value) {
                return
            }
            _itemScale = value
            // Force a complete redraw of all items
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SCALE_CHANGED)
        }

    // Initialize repository lazily
    private val metadataRepository: VideoMetadataRepository by lazy {
        VideoMetadataRepository(application)
    }
    // Coroutine scope for background tasks tied to the adapter's lifecycle
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Get thumbnail directory path
    private val thumbnailDir: File by lazy {
        File(application.filesDir, "video_thumbnails").apply { mkdirs() }
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        if (!isFileSelectable(file)) {
            return
        }
        val selected = file in selectedFiles
        val pickOptions = pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, !selected)
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        this.isSearching = isSearching
        val sortedList = if (!isSearching) list.sortedWith(sortOptions.createComparator()) else list
        replace(sortedList, false)
        rebuildFilePositionMap()
    }

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            val file = getItem(index)
            filePositionMap[file.path] = index
        }
    }

    override fun getItemViewType(position: Int): Int = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewType = FileViewType.entries[viewType]
        val inflater = parent.context.layoutInflater
        val holder = when (viewType) {
            FileViewType.LIST -> ViewHolder(FileItemListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(FileItemGridBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_grid_foreground_material3)
                }
                background = if (viewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
            thumbnailOutlineView?.apply {
                val context = context
                if (context.isMaterial3Theme) {
                    background = context.getDrawableCompat(
                        R.drawable.file_item_grid_thumbnail_outline_material3
                    )
                }
            }
            popupMenu = PopupMenu(menuButton.context, menuButton)
                .apply { inflate(R.menu.file_item) }
            menuButton.setOnClickListener { popupMenu.show() }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val file = getItem(position)
        val isDirectory = file.attributes.isDirectory
        val isEnabled = isFileSelectable(file) || isDirectory
        holder.itemLayout.isEnabled = isEnabled
        holder.menuButton.isEnabled = isEnabled
        val menu = holder.popupMenu.menu
        val path = file.path
        val pathString = path.pathString
        val fileAttributes = file.attributes
        val hasPickOptions = pickOptions != null
        val isReadOnly = path.fileSystem.isReadOnly
        menu.findItem(R.id.action_cut).isVisible = !hasPickOptions && !isReadOnly
        menu.findItem(R.id.action_copy).isVisible = !hasPickOptions
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        holder.nameText.apply {
            if (isSingleLineCompat) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            }
        }
        
        // Apply item scale if needed
        if (payloads.contains(PAYLOAD_SCALE_CHANGED)) {
            applyItemScale(holder)
            return
        }
        
        // If this is just a tag update, refresh the tags view and return
        if (payloads.contains(PAYLOAD_TAGS_CHANGED)) {
            // Update the tags view if present
            updateTagsView(holder, file)
            return
        }
        
        // If this is just for square thumbnails, just update that
        if (payloads.contains(PAYLOAD_SQUARE_THUMBNAILS_CHANGED)) {
            holder.thumbnailLayout?.let { thumbnailLayout ->
                val newRatio = if (isSquareThumbnailsInGrid) 1.0f else getAspectRatioForFile(file)
                thumbnailLayout.ratio = newRatio
            }
            return
        }
        
        if (payloads.isNotEmpty()) {
            return
        }
        
        holder.itemLayout.apply {
            setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            setOnLongClickListener {
                if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                true
            }
        }

        // Apply thumbnail aspect ratio based on square thumbnails setting for both list and grid views
        holder.thumbnailLayout?.let { thumbnailLayout ->
            // Apply square thumbnails in both grid and list view
            val newRatio = if (isSquareThumbnailsInGrid) 1.0f else getAspectRatioForFile(file)
            thumbnailLayout.ratio = newRatio
        }

        // CRITICAL FIX: Fix thumbnail click handling - reset and force to work with a direct approach
        holder.thumbnailClickArea?.let { clickArea ->
            // Remove all previous listeners
            clickArea.setOnClickListener(null)
            clickArea.setOnLongClickListener(null)
            
            // Configure the view for better click detection
            clickArea.isClickable = true
            clickArea.isFocusable = true
            
            // Set up the most direct click handler possible
            clickArea.setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
        }

        // Remove any potentially conflicting click listeners
        holder.iconLayout?.setOnClickListener(null)

        val iconRes = file.mimeType.iconRes
        holder.iconImage?.apply {
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.isVisible = isDirectory
        holder.thumbnailOutlineView?.isVisible = !isDirectory
        val supportsThumbnail = file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        val attributes = file.attributes
        holder.thumbnailIconImage?.apply {
            dispose()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes)
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            setImageDrawable(null)
            val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
            isVisible = shouldLoadThumbnail
            if (shouldLoadThumbnail) {
                // Launch coroutine for persistent thumbnail handling
                adapterScope.launch {
                    var thumbnailLoaded = false
                    // 1. Check database for persisted thumbnail path
                    val persistedPath = metadataRepository.getPersistedThumbnailPath(path)
                    if (persistedPath != null) {
                        val persistedFile = File(persistedPath)
                        if (persistedFile.exists()) {
                            // Load from persisted file
                            load(persistedFile) {
                    listener { _, _ ->
                                    // Hide icon if thumbnail loads
                                    val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                                    iconImage?.isVisible = false
                                }
                            }
                            thumbnailLoaded = true
                        } else {
                            // Thumbnail file missing, clear DB entry
                            Log.w("FileListAdapter", "Persisted thumbnail file missing: $persistedPath")
                            metadataRepository.updateThumbnailPath(path, null)
                        }
                    }
                    
                    // 2. If not loaded from persisted file, generate and save
                    if (!thumbnailLoaded) {
                        load(path to attributes) { // Use Coil to generate thumbnail
                            allowHardware(false) // Need software bitmap to save
                            listener(
                                onSuccess = { _, result ->
                                    // Hide icon
                        val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                        iconImage?.isVisible = false
                                    
                                    // Save the generated bitmap persistently
                                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    if (bitmap != null) {
                                        adapterScope.launch(Dispatchers.IO) { // Save in background
                                            saveThumbnailToFile(path, bitmap)
                                        }
                                    }
                                },
                                onError = { _, _ ->
                                    // Optional: Handle thumbnail generation error
                                }
                            )
                        }
                    }
                }
            }
        }
        holder.appIconBadgeImage?.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        holder.badgeImage?.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.error_badge_icon_18dp
                } else {
                    R.drawable.symbolic_link_badge_icon_18dp
                }
            } else if (file.attributesNoFollowLinks.isEncrypted()) {
                R.drawable.encrypted_badge_icon_18dp
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        val showCreationDate = Settings.FILE_LIST_SHOW_CREATION_DATE.valueCompat
        val dateTime = if (showCreationDate) {
            attributes.creationTime().toInstant()
        } else {
            attributes.lastModifiedTime().toInstant()
        }
        val formattedDate = dateTime.formatShort(holder.descriptionText?.context ?: holder.nameText.context)
        val size = attributes.fileSize.formatHumanReadable(
            holder.descriptionText?.context ?: holder.nameText.context
        )
        val descriptionSeparator = holder.descriptionText?.context?.getString(
            R.string.file_item_description_separator
        ) ?: holder.nameText.context.getString(R.string.file_item_description_separator)
        
        // Set the initial description
        val descriptionParts = mutableListOf(formattedDate, size)
        holder.descriptionText?.text = descriptionParts.joinToString(descriptionSeparator)
        
        // If it's a video file, try to load and display the duration
        if (file.mimeType.isVideo && file.path.isMediaMetadataRetrieverCompatible) {
            val viewPosition = holder.bindingAdapterPosition
            if (viewPosition != RecyclerView.NO_POSITION) {
                // Launch coroutine to get duration from repository
                adapterScope.launch {
                    val durationMillis = try {
                        metadataRepository.getVideoDuration(file.path)
                    } catch (e: Exception) {
                        Log.e("FileListAdapter", "Error getting duration for ${file.path}", e)
                        null
                    }

                    // Update UI only if the view hasn't been recycled and duration is valid
                    if (holder.bindingAdapterPosition == viewPosition && durationMillis != null) {
                        val context = holder.descriptionText?.context ?: holder.nameText.context
                        val formattedDuration = Duration.ofMillis(durationMillis).format()
                        
                        // Use the same date that was already formatted (modified or creation based on settings)
                        // Rebuild description parts including the duration and date
                        val updatedParts = listOfNotNull(
                            formattedDate, // Use the same formatted date from above
                            size, 
                            formattedDuration.takeIf { it.isNotEmpty() } // Only add if not empty
                        )
                        holder.descriptionText?.text = updatedParts.joinToString(descriptionSeparator)
                    }
                }
            }
        }
        
        holder.nameText.text = file.name

        // Check if we need to hide file info in grid view
        val isGridView = viewType == FileViewType.GRID
        val hideInfoInGrid = isGridView && Settings.FILE_LIST_HIDE_INFO_IN_GRID.valueCompat
        
        // Handle the info container visibility for grid view
        holder.infoContainer?.isVisible = !hideInfoInGrid
        
        // Ensure rating is always visible with consistent spacing
        val rating = FileRatingManager.getRating(file.path)
        holder.ratingText?.apply {
            text = if (rating > 0) rating.toString() else ""
            isVisible = true // Always visible for consistent layout
        }
        
        // Always update tags view regardless of view type
        updateTagsView(holder, file)
        
        // Set up popup menu click listener
        holder.popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_open_with -> {
                    listener.openFileWith(file)
                    true
                }
                R.id.action_cut -> {
                    listener.cutFile(file)
                    true
                }
                R.id.action_copy -> {
                    listener.copyFile(file)
                    true
                }
                R.id.action_delete -> {
                    listener.confirmDeleteFile(file)
                    true
                }
                R.id.action_rename -> {
                    listener.showRenameFileDialog(file)
                    true
                }
                R.id.action_extract -> {
                    listener.extractFile(file)
                    true
                }
                R.id.action_archive -> {
                    listener.showCreateArchiveDialog(file)
                    true
                }
                R.id.action_share -> {
                    listener.shareFile(file)
                    true
                }
                R.id.action_copy_path -> {
                    listener.copyPath(file)
                    true
                }
                R.id.action_add_bookmark -> {
                    listener.addBookmark(file)
                    true
                }
                R.id.action_create_shortcut -> {
                    listener.createShortcut(file)
                    true
                }
                R.id.action_properties -> {
                    listener.showPropertiesDialog(file)
                    true
                }
                else -> false
            }
        }

        // Apply the current scale
        applyItemScale(holder)
    }

    private fun updateTagsView(holder: ViewHolder, file: FileItem) {
        val tags = FileTagManager.getTagsForFile(file.path)
        holder.tagsView?.apply {
            if (tags.isNotEmpty()) {
                visibility = View.VISIBLE
                setTags(tags)
                setOnTagClickListener { tag ->
                    listener.onTagClick(tag)
                }
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun applyItemScale(holder: ViewHolder) {
        val scale = itemScale / 100f
        
        // Apply the scale - don't use scaleX/scaleY as that could cause overlap issues
        // Instead, adjust layout parameters
        
        // Scale the icon layout
        holder.iconLayout?.let { layout ->
            val params = layout.layoutParams
            // Store original size if not yet stored
            if (layout.getTag(R.id.tag_original_width) == null) {
                layout.setTag(R.id.tag_original_width, params.width)
                layout.setTag(R.id.tag_original_height, params.height)
            }
            val originalWidth = layout.getTag(R.id.tag_original_width) as Int
            val originalHeight = layout.getTag(R.id.tag_original_height) as Int
            
            // Apply scale if not WRAP_CONTENT or MATCH_PARENT
            if (originalWidth > 0) {
                params.width = (originalWidth * scale).toInt()
            }
            if (originalHeight > 0) {
                params.height = (originalHeight * scale).toInt()
            }
            layout.layoutParams = params
        }
        
        // Scale the thumbnail layout - IMPORTANT for thumbnail sizing
        holder.thumbnailLayout?.let { layout ->
            val params = layout.layoutParams
            // Store original height if not yet stored
            if (layout.getTag(R.id.tag_original_height) == null) {
                layout.setTag(R.id.tag_original_height, params.height)
            }
            val originalHeight = layout.getTag(R.id.tag_original_height) as Int
            
            // Apply scale if not WRAP_CONTENT or MATCH_PARENT
            if (originalHeight > 0) {
                params.height = (originalHeight * scale).toInt()
                layout.layoutParams = params
                
                // Critical: when scaling in grid view, maintain aspect ratio
                if (viewType == FileViewType.GRID) {
                    // Apply the current ratio setting again to force a re-measurement
                    // that will maintain the aspect ratio with the new height
                    val ratio = layout.ratio
                    layout.ratio = ratio
                }
            }
        }
        
        // Scale the thumbnail click area as well for consistency
        holder.thumbnailClickArea?.let { layout ->
            val params = layout.layoutParams
            // For thumbnail click area, we only scale height as width is often match_parent
            if (layout.getTag(R.id.tag_original_height) == null) {
                layout.setTag(R.id.tag_original_height, params.height)
            }
            val originalHeight = layout.getTag(R.id.tag_original_height) as Int
            
            // Apply scale if not WRAP_CONTENT or MATCH_PARENT
            if (originalHeight > 0) {
                params.height = (originalHeight * scale).toInt()
                layout.layoutParams = params
            }
        }
        
        // Scale text sizes
        holder.nameText.let { textView ->
            if (textView.getTag(R.id.tag_original_text_size) == null) {
                textView.setTag(R.id.tag_original_text_size, textView.textSize)
            }
            val originalSize = textView.getTag(R.id.tag_original_text_size) as Float
            textView.textSize = (originalSize * scale) / textView.resources.displayMetrics.density
        }
        
        holder.descriptionText?.let { textView ->
            if (textView.getTag(R.id.tag_original_text_size) == null) {
                textView.setTag(R.id.tag_original_text_size, textView.textSize)
            }
            val originalSize = textView.getTag(R.id.tag_original_text_size) as Float
            textView.textSize = (originalSize * scale) / textView.resources.displayMetrics.density
        }
        
        // Scale the entire item layout height for list view
        if (viewType == FileViewType.LIST) {
            val params = holder.itemLayout.layoutParams
            if (holder.itemLayout.getTag(R.id.tag_original_height) == null) {
                holder.itemLayout.setTag(R.id.tag_original_height, params.height)
            }
            val originalHeight = holder.itemLayout.getTag(R.id.tag_original_height) as Int
            
            // Only apply if not WRAP_CONTENT or MATCH_PARENT
            if (originalHeight > 0) {
                params.height = (originalHeight * scale).toInt()
                holder.itemLayout.layoutParams = params
            }
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val file = getItem(position)
        return when (sortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(Locale.getDefault())
            FileSortOptions.By.TYPE -> file.extension.uppercase(Locale.getDefault())
            FileSortOptions.By.SIZE -> file.attributes.fileSize.formatHumanReadable(view.context)
            FileSortOptions.By.LAST_MODIFIED -> {
                val showCreationDate = Settings.FILE_LIST_SHOW_CREATION_DATE.valueCompat
                val dateTime = if (showCreationDate) {
                    file.attributes.creationTime().toInstant()
                } else {
                    file.attributes.lastModifiedTime().toInstant()
                }
                dateTime.formatShort(view.context)
            }
            FileSortOptions.By.RATING -> {
                val rating = FileRatingManager.getRating(file.path)
                if (rating > 0) rating.toString() else "-"
            }
            FileSortOptions.By.DURATION -> {
                var duration: Long? = null
                // Use runBlocking carefully, okay for potentially quick DB lookup
                runBlocking {
                    duration = try {
                         metadataRepository.getDurationMillis(file.path)
                    } catch (e: Exception) {
                        null
                    }
                }
                duration?.format() ?: "-"
            }
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    override fun clear() {
        super.clear()
        selectedFiles.clear()
        filePositionMap.clear()
    }

    fun refreshFileItemsWithUpdatedTags() {
        Log.d("FileListAdapter", "Refreshing file items with updated tags")
        // Notify items that they need to update their tag information
        for (index in 0..<itemCount) {
            notifyItemChanged(index, PAYLOAD_TAGS_CHANGED)
        }
    }

    private fun getAspectRatioForFile(file: FileItem): Float {
        // For videos, try to use their stored aspect ratio from metadata cache
        if (file.mimeType.isVideo && file.path.isMediaMetadataRetrieverCompatible) {
            // Use a cached value if available - this operation is not suspending so safe to use here
            // Try to get from DB first, which is very fast
            try {
                // Use direct return from runBlocking instead of assigning to a variable
                val ratio = runBlocking {
                    metadataRepository.getVideoAspectRatio(file.path)
                }
                
                // If we got a valid ratio from the repository, use it
                if (ratio != null && ratio > 0f) {
                    return ratio
                }
            } catch (e: Exception) {
                // Ignore errors and use default ratio
                Log.d("FileListAdapter", "Error getting aspect ratio, using default", e)
            }
        }
        
        // Default to 16:9 (1.78) for other files
        return 1.78f
    }

    // Cancel coroutine scope when adapter is detached
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel() // Cancel running coroutines
    }

    // Helper function to save bitmap and update DB
    private suspend fun saveThumbnailToFile(originalPath: Path, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val thumbnailFile = getThumbnailFileForPath(originalPath)
        try {
            FileOutputStream(thumbnailFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos) // Save as JPEG
            }
            Log.d("FileListAdapter", "Saved thumbnail to ${thumbnailFile.path}")
            // Update the database with the path to the saved thumbnail
            metadataRepository.updateThumbnailPath(originalPath, thumbnailFile.absolutePath)
        } catch (e: Exception) {
            Log.e("FileListAdapter", "Failed to save thumbnail for $originalPath", e)
            // Clean up partial file if save failed
            thumbnailFile.delete()
            // Optionally clear the thumbnail path in DB if save fails
            // metadataRepository.updateThumbnailPath(originalPath, null)
        }
    }

    // Helper to generate a unique, stable filename for the thumbnail
    private fun getThumbnailFileForPath(originalPath: Path): File {
        val pathString = originalPath.pathString
        // Use SHA-256 hash of the path for a unique filename
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pathString.toByteArray())
        val fileName = hashBytes.toHex() + ".jpg"
        return File(thumbnailDir, fileName)
    }

    companion object {
        private val PAYLOAD_STATE_CHANGED = Any()
        private val PAYLOAD_TAGS_CHANGED = Any()
        private val PAYLOAD_SQUARE_THUMBNAILS_CHANGED = Any()
        private val PAYLOAD_SCALE_CHANGED = Any()

        private val CALLBACK = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean =
                oldItem == newItem
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View?,
        val iconImage: ImageView?,
        val directoryThumbnailImage: ImageView?,
        val thumbnailOutlineView: View?,
        val thumbnailIconImage: ImageView?,
        val thumbnailImage: ImageView,
        val appIconBadgeImage: ImageView?,
        val badgeImage: ImageView?,
        val nameText: TextView,
        val descriptionText: TextView?,
        val menuButton: ImageButton,
        val tagsView: TagsView?,
        val ratingText: TextView?,
        val infoContainer: View?,
        val thumbnailLayout: AspectRatioFrameLayout?,
        val thumbnailClickArea: View?
    ) : RecyclerView.ViewHolder(root) {
        constructor(binding: FileItemListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton,
            binding.tagsView,
            binding.ratingText,
            null,
            binding.thumbnailLayout,
            binding.thumbnailClickArea
        )

        constructor(binding: FileItemGridBinding) : this(
            binding.root,
            binding.itemLayout,
            null,
            null,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            null,
            null,
            binding.nameText,
            null,
            binding.menuButton,
            binding.tagsView,
            binding.ratingText,
            binding.infoContainer,
            binding.thumbnailLayout,
            binding.thumbnailClickArea
        )

        lateinit var popupMenu: PopupMenu
    }

    interface Listener {
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun openFile(file: FileItem)
        fun openFileWith(file: FileItem)
        fun cutFile(file: FileItem)
        fun copyFile(file: FileItem)
        fun confirmDeleteFile(file: FileItem)
        fun showRenameFileDialog(file: FileItem)
        fun extractFile(file: FileItem)
        fun showCreateArchiveDialog(file: FileItem)
        fun shareFile(file: FileItem)
        fun copyPath(file: FileItem)
        fun addBookmark(file: FileItem)
        fun createShortcut(file: FileItem)
        fun showPropertiesDialog(file: FileItem)
        fun onTagClick(tag: FileTag)
    }
}
