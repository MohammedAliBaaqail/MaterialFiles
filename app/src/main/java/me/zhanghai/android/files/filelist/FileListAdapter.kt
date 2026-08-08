/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isVideo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Scale
import coil.size.ViewSizeResolver
import coil.size.Precision
import java8.nio.file.Path
import java.io.File
import java.io.FileOutputStream
import me.zhanghai.android.files.filelist.FolderThumbnailManager
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.isSingleLineCompat
import me.zhanghai.android.files.databinding.FileItemGridBinding
import me.zhanghai.android.files.databinding.FileItemListBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.FileRatingManager
import me.zhanghai.android.files.file.FileTag
import me.zhanghai.android.files.file.FileTagManager
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.VideoMetadataRepository
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AnimatedListAdapter
import me.zhanghai.android.files.ui.AspectRatioFrameLayout
import me.zhanghai.android.files.ui.CheckableForegroundLinearLayout
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.ui.TagsView
import me.zhanghai.android.files.util.getQuantityString
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.pathString
import me.zhanghai.android.files.util.toHex
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.file.FolderItemCountManager
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(FileItemCallback()),
    PopupTextProvider {
    private var isSearching = false
    private var quickPreviewPopup: QuickPreviewPopup? = null

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

    val selectedFileItems: FileItemSet
        get() = selectedFiles.toFileItemSet()

    private val filePositionMap = mutableMapOf<Path, Int>()

    // Limit concurrent folder item count computations to avoid flooding on large folders
    private val folderCountDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

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
        
    private var _isPortraitModeInGrid: Boolean = false
    var isPortraitModeInGrid: Boolean
        get() = _isPortraitModeInGrid
        set(value) {
            if (_isPortraitModeInGrid == value) {
                return
            }
            _isPortraitModeInGrid = value
            // Force a complete redraw of all items
            notifyItemRangeChanged(0, itemCount, PAYLOAD_PORTRAIT_MODE_CHANGED)
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

    private var _selectionMode: SelectionMode = SelectionMode.NONE
    var selectionMode: SelectionMode
        get() = _selectionMode
        set(value) {
            if (_selectionMode == value) {
                return
            }
            _selectionMode = value
        }

    var isGridOverlayInfo: Boolean = Settings.GRID_OVERLAY_INFO.valueCompat
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
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

    fun selectRange(file1: FileItem, file2: FileItem) {
        val pos1 = getFilePosition(file1.path)
        val pos2 = getFilePosition(file2.path)
        if (pos1 == RecyclerView.NO_POSITION || pos2 == RecyclerView.NO_POSITION) {
            return
        }
        val start = minOf(pos1, pos2)
        val end = maxOf(pos1, pos2)
        val filesToSelect = fileItemSetOf()
        for (i in start..end) {
            val file = getItem(i)
            if (isFileSelectable(file)) {
                filesToSelect.add(file)
            }
        }
        listener.selectFiles(filesToSelect, true)
    }

    fun isFileSelectable(file: FileItem): Boolean {
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

    fun getFilePosition(path: Path): Int = filePositionMap[path] ?: RecyclerView.NO_POSITION

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
                .apply { menuInflater.inflate(R.menu.file_item, menu) }
            menuButton.setOnClickListener { popupMenu.show() }
            overlayMenuButton?.setOnClickListener { popupMenu.show() }
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
        if (payloads.contains(PAYLOAD_SQUARE_THUMBNAILS_CHANGED) || 
            payloads.contains(PAYLOAD_PORTRAIT_MODE_CHANGED)) {
            holder.thumbnailLayout?.let { thumbnailLayout ->
                val newRatio = when {
                    isSquareThumbnailsInGrid -> 1.0f
                    isPortraitModeInGrid -> 0.5625f  // 9:16 ratio (portrait mode)
                    else -> getAspectRatioForFile(file)
                }
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
            clickArea.isLongClickable = true
            
            // Set up the most direct click handler possible - using the same behavior as the main layout
            clickArea.setOnClickListener { view ->
                Log.d("FileListAdapter", "Thumbnail area clicked for file: ${file.name}")
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            
            // Add robust long-click handler that matches main item behavior
            clickArea.setOnLongClickListener { view ->
                Log.d("FileListAdapter", "Thumbnail area long-clicked for file: ${file.name}")
                if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                // Return true to indicate the long-click was handled and shouldn't propagate
                true
            }
        }

        // Force thumbnail clickable state by also setting listener on the image itself as a fallback
        holder.thumbnailImage.let { thumbnailImage ->
            thumbnailImage.isClickable = true
            thumbnailImage.isFocusable = true
            thumbnailImage.isLongClickable = true
            
            thumbnailImage.setOnClickListener { view ->
                Log.d("FileListAdapter", "Thumbnail image directly clicked for file: ${file.name}")
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            
            // Also add a long-click listener directly to the image
            thumbnailImage.setOnLongClickListener { view ->
                Log.d("FileListAdapter", "Thumbnail image directly long-clicked for file: ${file.name}")
                if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                // Return true to consume the event
                true
            }
        }

        // Configure Quick Peek touch gesture for media files
        val isMedia = file.mimeType.isImage || file.mimeType.isVideo
        val quickPreviewEnabled = Settings.QUICK_PREVIEW_ENABLED.valueCompat

        if (quickPreviewEnabled && isMedia) {
            // Disable default selection on long click for media files when Quick Preview is ON
            holder.itemLayout.setOnLongClickListener { true }
            holder.thumbnailClickArea?.setOnLongClickListener { true }
            holder.thumbnailImage.setOnLongClickListener { true }

            val touchListener = object : View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                private var isLongPressActive = false
                private val handler = Handler(Looper.getMainLooper())
                private var currentView: View? = null
                private val longPressRunnable = Runnable {
                    isLongPressActive = true
                    currentView?.parent?.requestDisallowInterceptTouchEvent(true)
                    if (quickPreviewPopup == null) {
                        quickPreviewPopup = QuickPreviewPopup(holder.itemView.context)
                    }
                    quickPreviewPopup?.show(holder.itemView, file)
                }

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    currentView = v
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            isLongPressActive = false
                            handler.postDelayed(longPressRunnable, 250)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX
                            val dy = event.rawY - downY
                            if (!isLongPressActive && (Math.abs(dx) > 25 || Math.abs(dy) > 25)) {
                                handler.removeCallbacks(longPressRunnable)
                            } else if (isLongPressActive && quickPreviewPopup?.isShowing() == true) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                if (dy < -100 && quickPreviewPopup?.isLocked == false) {
                                    quickPreviewPopup?.lockPreview()
                                }
                                if (dy > 30f) {
                                    quickPreviewPopup?.updatePlaybackSpeed(dy)
                                } else {
                                    quickPreviewPopup?.resetPlaybackSpeed()
                                    if (Math.abs(dx) > 15) {
                                        quickPreviewPopup?.onDragDelta(dx)
                                    }
                                }
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            handler.removeCallbacks(longPressRunnable)
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            if (isLongPressActive) {
                                quickPreviewPopup?.onTouchUp()
                                if (quickPreviewPopup?.isLocked != true) {
                                    quickPreviewPopup?.dismiss()
                                }
                                isLongPressActive = false
                                return true
                            }
                        }
                    }
                    return false
                }
            }

            holder.itemView.setOnTouchListener(touchListener)
            holder.thumbnailClickArea?.setOnTouchListener(touchListener)
            holder.thumbnailImage.setOnTouchListener(touchListener)
        } else {
            holder.itemView.setOnTouchListener(null)
            holder.thumbnailClickArea?.setOnTouchListener(null)
            holder.thumbnailImage.setOnTouchListener(null)
        }

        // Remove any potentially conflicting click listeners
        holder.iconLayout?.setOnClickListener(null)

        val iconRes = file.mimeType.iconRes
        holder.iconImage?.apply {
            isVisible = viewType != FileViewType.GRID || isDirectory
            setImageResource(iconRes)
        }
        holder.iconImage?.isEnabled = isEnabled
        holder.directoryThumbnailImage?.isVisible = isDirectory
        
        // Configure thumbnail layout aspect ratio based on mode
        holder.thumbnailLayout?.let { thumbnailLayout ->
            val ratio = when {
                isSquareThumbnailsInGrid -> 1.0f
                isPortraitModeInGrid -> 0.5625f  // 9:16 ratio (portrait mode)
                else -> getAspectRatioForFile(file)
            }
            thumbnailLayout.ratio = ratio
        }

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
            
            // Set the appropriate scaleType based on mode
            if (viewType == FileViewType.GRID && isPortraitModeInGrid) {
                // In portrait mode, use CENTER_CROP to ensure it fills the entire space
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else if (viewType == FileViewType.GRID && isSquareThumbnailsInGrid) {
                // For square thumbnails, also use CENTER_CROP
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                // For regular thumbnails, use FIT_CENTER which preserves aspect ratio
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            
            // Check for custom folder thumbnail first
            var shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
            if (isDirectory) {
                Log.d("FileListAdapter", "Folder path: ${path}")
                Log.d("FileListAdapter", "Folder path toString: ${path.toString()}")
                val context = holder.thumbnailImage.context
                val customThumbnail = FolderThumbnailManager.getFolderThumbnail(context, File(path.toString()))
                Log.d("FileListAdapter", "Checking for custom thumbnail for folder: ${path} - found: ${customThumbnail != null}")
                if (customThumbnail != null) {
                    Log.d("FileListAdapter", "Loading custom thumbnail: ${customThumbnail.absolutePath}")
                    Log.d("FileListAdapter", "Custom thumbnail exists: ${customThumbnail.exists()}")
                    Log.d("FileListAdapter", "Custom thumbnail length: ${customThumbnail.length()}")
                    
                    // Check if file is readable
                    if (!customThumbnail.canRead()) {
                        Log.e("FileListAdapter", "Custom thumbnail file is not readable")
                    }
                    
                    // Load custom folder thumbnail
                    Log.d("FileListAdapter", "Attempting to load custom thumbnail: ${customThumbnail.absolutePath}")
                    load(customThumbnail) {
                        crossfade(true)
                        // Load at view size with exact precision and FIT scale for smoother animated WebP
                        size(ViewSizeResolver(this@apply))
                        precision(Precision.EXACT)
                        scale(Scale.FIT)
                        listener(
                            onSuccess = { _, _ ->
                                Log.d("FileListAdapter", "Custom thumbnail loaded successfully for ${customThumbnail.absolutePath}")
                                // Check if the view is still valid
                                if (holder.thumbnailImage.isAttachedToWindow) {
                                    // Hide icon if thumbnail loads
                                    val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                                    iconImage?.isVisible = false
                                    // Make sure the thumbnail image is visible
                                    holder.thumbnailImage.isVisible = true
                                } else {
                                    Log.d("FileListAdapter", "ViewHolder recycled before thumbnail load completed for ${customThumbnail.absolutePath}")
                                }
                            },
                            onError = { request, errorResult ->
                                Log.e("FileListAdapter", "Error loading custom thumbnail for ${customThumbnail.absolutePath}", errorResult.throwable)
                                Log.e("FileListAdapter", "Error request: $request")
                                Log.e("FileListAdapter", "Error result: $errorResult")
                                // Check if the view is still valid
                                if (holder.thumbnailImage.isAttachedToWindow) {
                                    // Try to show the default folder icon if custom thumbnail fails
                                    val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                                    iconImage?.isVisible = true
                                    iconImage?.setImageResource(R.drawable.ic_folder_white_24dp)
                                } else {
                                    Log.d("FileListAdapter", "ViewHolder recycled before thumbnail load error for ${customThumbnail.absolutePath}")
                                }
                            }
                        )
                    }
                    shouldLoadThumbnail = false
                    // For custom thumbnails, we still want the thumbnail image to be visible
                    // but we don't want to load a thumbnail from the regular thumbnail handling code
                    isVisible = true
                }
            }
            
            if (isVisible != (shouldLoadThumbnail || isDirectory)) {
                isVisible = shouldLoadThumbnail
            }
            if (shouldLoadThumbnail) {
                // Launch coroutine for persistent thumbnail handling
                adapterScope.launch {
                    var thumbnailLoaded = false
                    // 1. Check database for persisted thumbnail path - this is fast
                    val persistedPath = metadataRepository.getPersistedThumbnailPath(path)
                    if (persistedPath != null) {
                        val persistedFile = File(persistedPath)
                        if (persistedFile.exists()) {
                            // Load from persisted file
                            load(persistedFile) {
                                // Load at view size with exact precision and FIT scale for smoother animated WebP
                                size(ViewSizeResolver(this@apply))
                                precision(Precision.EXACT)
                                scale(Scale.FIT)
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
                    
                    // 2. If not loaded from persisted file and it's a video, delay generation
                    // until user stops scrolling to avoid lag
                    if (!thumbnailLoaded) {
                        // For images, load immediately
                        if (!file.mimeType.isVideo) {
                            generateAndSaveThumbnail(path, attributes, this@apply)
                        } else {
                            // For videos, show a placeholder icon and load thumbnail after a delay
                            // to avoid frame drops during scrolling
                            val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                            iconImage?.isVisible = true
                            
                            // Load with a delay to avoid jank during scrolling
                            withContext(Dispatchers.Main) {
                                delay(500) // Short delay to let scrolling finish
                                if (isActive && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                    generateAndSaveThumbnail(path, attributes, this@apply)
                                }
                            }
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
        
        // Show file count for folders instead of size
        val context = holder.descriptionText?.context ?: holder.nameText.context
        val size = if (file.attributes.isDirectory) {
            // For directories, display item count instead of size
            context.getQuantityString(R.plurals.file_list_item_count_format, 0, 0)
        } else {
            // For files, use the regular file size format
            attributes.fileSize.formatHumanReadable(context)
        }
        
        val descriptionSeparator = context.getString(R.string.file_item_description_separator)
        
        // Set the initial description
        val descriptionParts = mutableListOf(formattedDate, size)
        holder.descriptionText?.text = descriptionParts.joinToString(descriptionSeparator)
        
        // For folders, start a coroutine to count files and update the display
        if (file.attributes.isDirectory) {
            val viewPosition = holder.bindingAdapterPosition
            if (viewPosition != RecyclerView.NO_POSITION) {
                adapterScope.launch {
                    try {
                        // Try cached count first
                        val lastModified = file.attributes.lastModifiedTime().toMillis()
                        var fileCount = FolderItemCountManager.getItemCount(file.path, lastModified)
                            ?: -1
                        if (fileCount < 0) {
                            // Count with limited concurrency to reduce contention
                            fileCount = withContext(folderCountDispatcher) {
                                var count = 0
                            try {
                                    java8.nio.file.Files.newDirectoryStream(file.path).use { ds ->
                                        for (p in ds) {
                                            count++
                                        if (!isActive) break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("FileListAdapter", "Error counting files in ${file.path}", e)
                                }
                                count
                            }
                            // Persist count for future sessions
                            if (fileCount >= 0) {
                                FolderItemCountManager.setItemCount(file.path, fileCount, lastModified)
                            }
                        }
                        
                        // Update UI only if view hasn't been recycled
                        if (holder.bindingAdapterPosition == viewPosition) {
                            val updatedSize = context.getQuantityString(R.plurals.file_list_item_count_format, fileCount, fileCount)
                            val updatedParts = listOfNotNull(formattedDate, updatedSize)
                        holder.descriptionText?.text = updatedParts.joinToString(descriptionSeparator)
                        }
                    } catch (e: Exception) {
                        Log.e("FileListAdapter", "Error getting file count", e)
                    }
                }
            }
        }
        
        val isGridView = viewType == FileViewType.GRID
        val hideInfoInGrid = isGridView && Settings.FILE_LIST_HIDE_INFO_IN_GRID.valueCompat
        val isOverlayMode = isGridView && isGridOverlayInfo

        holder.infoContainer?.isVisible = !hideInfoInGrid && !isOverlayMode
        holder.overlayTopContainer?.isVisible = isOverlayMode
        holder.overlayBottomContainer?.isVisible = isOverlayMode

        val fileName = file.name
        holder.nameText.text = fileName
        holder.overlayNameText?.text = fileName

        val rating = FileRatingManager.getRating(file.path)
        val ratingString = if (rating > 0) rating.toString() else ""
        holder.ratingText?.apply {
            text = ratingString
            isVisible = true
        }
        holder.overlayRatingText?.apply {
            text = ratingString
            isVisible = rating > 0
        }
        
        // Always update tags view regardless of view type
        updateTagsView(holder, file)
        
        // Set up popup menu click listener
        holder.popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
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
                R.id.action_manage_thumbnail -> {
                    listener.manageVideoThumbnail(file)
                    true
                }
                R.id.action_manage_folder_thumbnail -> {
                    listener.manageFolderThumbnail(file)
                    true
                }
                else -> false
            }
        }

        // Apply the current scale
        applyItemScale(holder)

        // Make sure all view elements in the thumbnail hierarchy are properly set up for long-clicks
        holder.iconLayout?.isLongClickable = false
        
        holder.iconImage?.isLongClickable = false
        
        holder.thumbnailLayout?.isLongClickable = false
        
        // This prevents other elements from intercepting the long-click
        // and ensures the click area's long-click handler is called
        holder.badgeImage?.isLongClickable = false
        holder.appIconBadgeImage?.isLongClickable = false

        // Only show "Manage Video Thumbnail" for video files
        menu.findItem(R.id.action_manage_thumbnail).isVisible = file.mimeType.isVideo
        
        // Only show "Manage Folder Thumbnail" for directories
        menu.findItem(R.id.action_manage_folder_thumbnail).isVisible = file.attributes.isDirectory

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
    }

    private fun updateTagsView(holder: ViewHolder, file: FileItem) {
        val bindTags: (List<FileTag>, TagsView, Boolean) -> Unit = { tags, tagsView, isOverlay ->
            if (tags.isNotEmpty()) {
                tagsView.visibility = View.VISIBLE
                tagsView.setTags(tags)
                tagsView.setOnTagClickListener { tag -> listener.onTagClick(tag) }
            } else {
                tagsView.visibility = if (isOverlay) View.INVISIBLE else View.GONE
            }
        }

        // Use in-memory tag cache first for instant UI, then refresh in background if needed
        val cached = me.zhanghai.android.files.file.FileTagCache.get(file.path)
        if (cached != null) {
            holder.tagsView?.let { bindTags(cached, it, false) }
            holder.overlayTagsView?.let { bindTags(cached, it, true) }
            return
        }
        holder.tagsView?.visibility = View.GONE
        holder.overlayTagsView?.visibility = View.INVISIBLE
        val viewPosition = holder.bindingAdapterPosition
        if (viewPosition == RecyclerView.NO_POSITION) return
        adapterScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val tags = FileTagManager.getTagsForFile(file.path)
            me.zhanghai.android.files.file.FileTagCache.put(file.path, tags)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (holder.bindingAdapterPosition == viewPosition) {
                    holder.tagsView?.let { bindTags(tags, it, false) }
                    holder.overlayTagsView?.let { bindTags(tags, it, true) }
                }
            }
        }
    }

    private fun applyItemScale(holder: ViewHolder) {
        val scale = itemScale / 100f

        if (viewType == FileViewType.GRID) {
            val context = holder.itemView.context
            val paddingPx = (0.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            holder.itemLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        } else {
            holder.itemLayout.setPadding(0, 0, 0, 0)
        }

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
                // Return placeholder and let background loading update it
                "-"
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
        // Use predefined aspect ratios instead of calculating dimensions
        return when {
            isSquareThumbnailsInGrid -> 1.0f  // Square
            isPortraitModeInGrid -> 0.5625f  // 9:16 portrait mode (0.5625 = 9/16)
            else -> 1.778f  // 16:9 landscape (default)
        }
    }
    
    private fun generateAndSaveThumbnail(path: Path, attributes: java8.nio.file.attribute.BasicFileAttributes, imageView: ImageView) {
        imageView.load(path to attributes) { 
            allowHardware(false) // Need software bitmap to save
            // Decode at the target view size with exact precision for smoother scaling
            size(ViewSizeResolver(imageView))
            precision(Precision.EXACT)
            scale(Scale.FIT)
            
            // Configure scaling overrides based on grid mode
            if (viewType == FileViewType.GRID) {
                if (isPortraitModeInGrid) {
                    // In portrait mode, fill to match the aspect ratio box
                    scale(Scale.FILL)
                } else if (isSquareThumbnailsInGrid) {
                    // For square thumbnails, also fill
                    scale(Scale.FILL)
                }
            }
            
            listener(
                onSuccess = { _, result ->
                    // Hide icon when thumbnail loads
                    val parent = imageView.parent.parent as? ViewGroup
                    val iconImage = parent?.findViewById<ImageView?>(R.id.thumbnailIconImage)
                        ?: parent?.findViewById<ImageView?>(R.id.iconImage)
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
                    // On error, keep the icon visible
                }
            )
        }
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
                // Increase quality from 85 to 95 for higher quality thumbnails
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            Log.d("FileListAdapter", "Saved thumbnail to ${thumbnailFile.path}")
            // Update the database with the path to the saved thumbnail
            metadataRepository.updateThumbnailPath(originalPath, thumbnailFile.absolutePath)
        } catch (e: Exception) {
            Log.e("FileListAdapter", "Failed to save thumbnail for $originalPath", e)
            // Clean up partial file if save failed
            thumbnailFile.delete()
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
        private val PAYLOAD_PORTRAIT_MODE_CHANGED = Any()
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
        val thumbnailClickArea: View?,
        val overlayTopContainer: View? = null,
        val overlayNameText: TextView? = null,
        val overlayBottomContainer: View? = null,
        val overlayRatingText: TextView? = null,
        val overlayTagsView: TagsView? = null,
        val overlayMenuButton: ImageButton? = null
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
            binding.thumbnailClickArea,
            binding.overlayTopContainer,
            binding.overlayNameText,
            binding.overlayBottomContainer,
            binding.overlayRatingText,
            binding.overlayTagsView,
            binding.overlayMenuButton
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
        fun manageVideoThumbnail(file: FileItem)
        fun manageFolderThumbnail(file: FileItem)
    }
}
