/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewSwitcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.VideoMetadataRepository
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import java.io.File
import java.io.FileOutputStream
import kotlinx.parcelize.RawValue
import android.content.Context
import android.util.AttributeSet
import kotlin.math.max

/**
 * Custom view to display a 16:9 crop area overlay on top of a video/image
 */
class AspectRatioCropOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val maskPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180 // Semi-transparent
        style = Paint.Style.FILL
    }
    
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    private val cropRect = RectF()
    private val videoRect = RectF()
    private var videoWidth = 0
    private var videoHeight = 0
    
    fun setVideoDimensions(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (videoWidth <= 0 || videoHeight <= 0) {
            // If we don't know video dimensions yet, use the view dimensions
            videoRect.set(0f, 0f, width.toFloat(), height.toFloat())
        } else {
            // Calculate how the video is actually displayed within this view
            calculateVideoRect()
        }
        
        // Create an offscreen bitmap for the mask
        val tempBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        
        // Fill the entire canvas with the semi-transparent mask
        tempCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        
        // Calculate the 16:9 crop area
        calculateCropRect()
        
        // Clear out the 16:9 area in the center
        tempCanvas.drawRect(cropRect, clearPaint)
        
        // Draw the result onto the main canvas
        canvas.drawBitmap(tempBitmap, 0f, 0f, null)
        
        // Draw the border around the crop area
        canvas.drawRect(cropRect, paint)
    }
    
    private fun calculateVideoRect() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val viewRatio = viewWidth / viewHeight
        
        if (viewRatio > videoRatio) {
            // View is wider than video
            val scaledVideoWidth = viewHeight * videoRatio
            val leftPadding = (viewWidth - scaledVideoWidth) / 2
            videoRect.set(leftPadding, 0f, leftPadding + scaledVideoWidth, viewHeight)
        } else {
            // View is taller than video
            val scaledVideoHeight = viewWidth / videoRatio
            val topPadding = (viewHeight - scaledVideoHeight) / 2
            videoRect.set(0f, topPadding, viewWidth, topPadding + scaledVideoHeight)
        }
    }
    
    private fun calculateCropRect() {
        // Calculate 16:9 crop rect within the video rect
        val targetRatio = 16f / 9f
        val videoRectRatio = (videoRect.right - videoRect.left) / (videoRect.bottom - videoRect.top)
        
        if (videoRectRatio > targetRatio) {
            // Video is wider than 16:9, need to crop width
            val cropHeight = videoRect.height()
            val cropWidth = cropHeight * targetRatio
            val leftInset = (videoRect.width() - cropWidth) / 2
            
            cropRect.set(
                videoRect.left + leftInset,
                videoRect.top,
                videoRect.right - leftInset,
                videoRect.bottom
            )
        } else {
            // Video is taller than 16:9, need to crop height
            val cropWidth = videoRect.width()
            val cropHeight = cropWidth / targetRatio
            val topInset = (videoRect.height() - cropHeight) / 2
            
            cropRect.set(
                videoRect.left,
                videoRect.top + topInset,
                videoRect.right,
                videoRect.bottom - topInset
            )
        }
    }
}

class VideoThumbnailManagementDialogFragment : AppCompatDialogFragment() {

    private val args by args<Args>()
    private lateinit var path: Path
    private var durationMillis: Long = 0
    private var currentTimeMillis: Long = 0
    private var videoBitmap: Bitmap? = null
    private var customImageUri: Uri? = null
    private var use16by9Ratio: Boolean = false
    
    private lateinit var metadataRepository: VideoMetadataRepository
    private var player: ExoPlayer? = null
    private var seekUpdateJob: Job? = null
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleCustomImageSelected(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.video_thumbnail_management_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        path = args.path
        metadataRepository = VideoMetadataRepository(requireContext())

        val mediaViewSwitcher = view.findViewById<ViewSwitcher>(R.id.media_view_switcher)
        val thumbnailImageView = view.findViewById<ImageView>(R.id.thumbnail_image)
        val playerView = view.findViewById<PlayerView>(R.id.player_view)
        val timeSeekBar = view.findViewById<SeekBar>(R.id.time_seek_bar)
        val currentTimeText = view.findViewById<TextView>(R.id.current_time_text)
        val regenerateButton = view.findViewById<MaterialButton>(R.id.regenerate_button)
        val useCurrentTimeButton = view.findViewById<MaterialButton>(R.id.use_current_time_button)
        val chooseCustomImageButton = view.findViewById<MaterialButton>(R.id.choose_custom_image_button)
        val saveButton = view.findViewById<MaterialButton>(R.id.save_button)
        val progressIndicator = view.findViewById<LinearProgressIndicator>(R.id.progress_indicator)
        val previewContainer = view.findViewById<FrameLayout>(R.id.preview_container)
        val playerModeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.player_mode_toggle)
        val aspectRatioSwitch = view.findViewById<SwitchMaterial>(R.id.aspect_ratio_switch)
        val cropOverlay = view.findViewById<View>(R.id.crop_overlay)
        val mediaContainer = view.findViewById<FrameLayout>(R.id.media_container)
        
        // Set player to resize to fit the container properly
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        
        // Replace existing crop overlay with custom implementation
        if (cropOverlay.parent is ViewGroup) {
            val parent = cropOverlay.parent as ViewGroup
            val index = parent.indexOfChild(cropOverlay)
            parent.removeView(cropOverlay)
            
            val customCropOverlay = AspectRatioCropOverlayView(requireContext())
            customCropOverlay.id = R.id.crop_overlay
            customCropOverlay.visibility = View.GONE
            parent.addView(customCropOverlay, index, cropOverlay.layoutParams)
            
            // Update reference to the new crop overlay
            val newCropOverlay = parent.findViewById<AspectRatioCropOverlayView>(R.id.crop_overlay)
            
            // Initialize aspect ratio toggle with the new overlay
            aspectRatioSwitch.setOnCheckedChangeListener { _, isChecked ->
                use16by9Ratio = isChecked
                newCropOverlay.visibility = if (isChecked) View.VISIBLE else View.GONE
                
                // Make sure crop overlay is in front of everything
                if (isChecked) {
                    newCropOverlay.bringToFront()
                    
                    // Get and set video dimensions for accurate cropping
                    lifecycleScope.launch {
                        try {
                            val dimensions = metadataRepository.getVideoDimensions(path)
                            dimensions?.let {
                                newCropOverlay.setVideoDimensions(it.first, it.second)
                            }
                        } catch (e: Exception) {
                            // If we can't get dimensions, we'll use the view dimensions
                        }
                        mediaContainer.invalidate()
                    }
                }
            }
        } else {
            // Fallback to original implementation if replacement fails
            aspectRatioSwitch.setOnCheckedChangeListener { _, isChecked ->
                use16by9Ratio = isChecked
                cropOverlay.visibility = if (isChecked) View.VISIBLE else View.GONE
                
                if (isChecked) {
                    cropOverlay.bringToFront()
                    mediaContainer?.invalidate()
                }
            }
        }
        
        // Initialize player mode toggle
        playerModeToggle.check(R.id.mode_image)
        playerModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.mode_image -> {
                        mediaViewSwitcher.displayedChild = 0 // Show image view
                        player?.pause()
                        timeSeekBar.isEnabled = true
                    }
                    R.id.mode_video -> {
                        mediaViewSwitcher.displayedChild = 1 // Show video player
                        initializePlayer(playerView)
                        timeSeekBar.isEnabled = false
                    }
                }
            }
        }
        
        // Load video info
        lifecycleScope.launch {
            progressIndicator.isVisible = true
            previewContainer.isVisible = false
            
            try {
                // Get video duration
                durationMillis = withContext(Dispatchers.IO) {
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(path.toString())
                        
                        // Get duration
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                }
                
                // Configure time seek bar
                timeSeekBar.max = durationMillis.toInt()
                currentTimeMillis = durationMillis / 3 // Start at 1/3 of video by default
                timeSeekBar.progress = currentTimeMillis.toInt()
                updateTimeText(currentTimeText, currentTimeMillis)
                
                // Load current thumbnail
                val currentThumbnailPath = metadataRepository.getPersistedThumbnailPath(path)
                if (currentThumbnailPath != null) {
                    val file = File(currentThumbnailPath)
                    if (file.exists()) {
                        thumbnailImageView.setImageURI(Uri.fromFile(file))
                    }
                }
                
                // Load preview frame at current position
                lifecycleScope.launch {
                    loadFrameAtTime(currentTimeMillis, thumbnailImageView)
                }
                
                // Setup seekbar listener
                timeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        currentTimeMillis = progress.toLong()
                        updateTimeText(currentTimeText, currentTimeMillis)
                    }
                    
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        lifecycleScope.launch {
                            loadFrameAtTime(currentTimeMillis, thumbnailImageView)
                        }
                    }
                })
                
                // Setup button listeners
                regenerateButton.setOnClickListener {
                    lifecycleScope.launch {
                        progressIndicator.isVisible = true
                        customImageUri = null
                        
                        // Generate a random position between 10% and 90% of the duration
                        val randomPosition = (durationMillis * (0.1 + Math.random() * 0.8)).toLong()
                        currentTimeMillis = randomPosition
                        timeSeekBar.progress = randomPosition.toInt()
                        updateTimeText(currentTimeText, currentTimeMillis)
                        
                        loadFrameAtTime(currentTimeMillis, thumbnailImageView)
                        progressIndicator.isVisible = false
                    }
                }
                
                useCurrentTimeButton.setOnClickListener {
                    lifecycleScope.launch {
                        progressIndicator.isVisible = true
                        customImageUri = null
                        
                        // If in video mode, get current player position
                        if (mediaViewSwitcher.displayedChild == 1 && player != null) {
                            val playerPosition = player!!.currentPosition
                            currentTimeMillis = playerPosition
                            timeSeekBar.progress = playerPosition.toInt()
                            updateTimeText(currentTimeText, currentTimeMillis)
                        }
                        
                        loadFrameAtTime(currentTimeMillis, thumbnailImageView)
                        progressIndicator.isVisible = false
                    }
                }
                
                chooseCustomImageButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    pickImageLauncher.launch(intent)
                }
                
                saveButton.setOnClickListener {
                    saveCurrentThumbnail()
                }
                
                progressIndicator.isVisible = false
                previewContainer.isVisible = true
                
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.video_thumbnail_management_error_loading, e.message),
                    Toast.LENGTH_LONG
                ).show()
                dismissAllowingStateLoss()
            }
        }
    }
    
    private fun updateTimeText(textView: TextView, timeMillis: Long) {
        val seconds = timeMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val displaySeconds = seconds % 60
        val displayMinutes = minutes % 60
        val displayMillis = timeMillis % 1000
        
        textView.text = if (hours > 0) {
            String.format("%d:%02d:%02d.%03d", hours, displayMinutes, displaySeconds, displayMillis)
        } else {
            String.format("%02d:%02d.%03d", displayMinutes, displaySeconds, displayMillis)
        }
    }
    
    private fun initializePlayer(playerView: PlayerView) {
        // Release any existing player
        releasePlayer()
        
        // Create a new player instance
        player = ExoPlayer.Builder(requireContext())
            .build().apply {
            // Set media item
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path.toString())))
            setMediaItem(mediaItem)
            
            // Prepare the player
            prepare()
            
            // Set initial position to match the time bar
            seekTo(currentTimeMillis)
            
            // Pause initially to let user control playback
            playWhenReady = false
        }
        
        // Attach player to view
        playerView.player = player
        playerView.controllerHideOnTouch = false
        
        // Setup frame-by-frame navigation
        val frameBackButton = playerView.findViewById<ImageButton>(R.id.exo_frame_back)
        val frameForwardButton = playerView.findViewById<ImageButton>(R.id.exo_frame_forward)
        
        // Assuming 30 fps video, move approximately one frame (33ms) per click
        val FRAME_MS: Long = 33
        
        frameBackButton?.setOnClickListener {
            player?.let {
                // Seek backward by one frame
                val newPosition = maxOf(0, it.currentPosition - FRAME_MS)
                it.seekTo(newPosition)
                currentTimeMillis = newPosition
                view?.findViewById<SeekBar>(R.id.time_seek_bar)?.progress = newPosition.toInt()
                updateTimeText(view?.findViewById<TextView>(R.id.current_time_text) ?: return@let, newPosition)
            }
        }
        
        frameForwardButton?.setOnClickListener {
            player?.let {
                // Seek forward by one frame
                val duration = it.duration
                val newPosition = minOf(duration, it.currentPosition + FRAME_MS)
                it.seekTo(newPosition)
                currentTimeMillis = newPosition
                view?.findViewById<SeekBar>(R.id.time_seek_bar)?.progress = newPosition.toInt()
                updateTimeText(view?.findViewById<TextView>(R.id.current_time_text) ?: return@let, newPosition)
            }
        }
        
        // Start a job to update the seek bar without relying on callbacks
        startSeekBarUpdateJob()
        
        // Get video dimensions directly from the repository for the crop overlay
        view?.findViewById<AspectRatioCropOverlayView>(R.id.crop_overlay)?.let { cropView ->
            lifecycleScope.launch {
                try {
                    val dimensions = metadataRepository.getVideoDimensions(path)
                    dimensions?.let {
                        cropView.setVideoDimensions(it.first, it.second)
                    }
                } catch (e: Exception) {
                    // If we can't get dimensions, we'll use the view dimensions
                }
            }
        }
    }
    
    private fun startSeekBarUpdateJob() {
        val timeSeekBar = view?.findViewById<SeekBar>(R.id.time_seek_bar) ?: return
        val currentTimeText = view?.findViewById<TextView>(R.id.current_time_text) ?: return
        
        seekUpdateJob?.cancel()
        seekUpdateJob = lifecycleScope.launch {
            while (true) {
                player?.let {
                    val currentPos = it.currentPosition
                    timeSeekBar.progress = currentPos.toInt()
                    updateTimeText(currentTimeText, currentPos)
                    currentTimeMillis = currentPos
                }
                delay(200) // Update 5 times per second
            }
        }
    }
    
    private fun releasePlayer() {
        seekUpdateJob?.cancel()
        seekUpdateJob = null
        player?.release()
        player = null
    }
    
    private suspend fun loadFrameAtTime(timeMillis: Long, imageView: ImageView) {
        withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path.toString())
                    
                    // Convert to microseconds
                    val timeMicros = timeMillis * 1000
                    
                    // Extract frame at specified time
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        // For newer devices, request at higher resolution for quality
                        retriever.getScaledFrameAtTime(
                            timeMicros,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            1920, // Higher resolution for better quality
                            1080
                        )
                    } else {
                        retriever.getFrameAtTime(
                            timeMicros,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        )
                    }
                    
                    videoBitmap = bitmap
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            customImageUri = null
                        } else {
                            Toast.makeText(
                                requireContext(),
                                R.string.video_thumbnail_management_error_loading_frame,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.video_thumbnail_management_error_loading, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun handleCustomImageSelected(uri: Uri) {
        val imageView = view?.findViewById<ImageView>(R.id.thumbnail_image) ?: return
        val progressIndicator = view?.findViewById<LinearProgressIndicator>(R.id.progress_indicator) ?: return
        
        lifecycleScope.launch {
            progressIndicator.isVisible = true
            customImageUri = uri
            videoBitmap = null
            
            try {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        // Just set the URI directly for preview
                        withContext(Dispatchers.Main) {
                            imageView.setImageURI(uri)
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.video_thumbnail_management_error_loading_image_detailed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                progressIndicator.isVisible = false
            }
        }
    }
    
    private fun saveCurrentThumbnail() {
        val progressIndicator = view?.findViewById<LinearProgressIndicator>(R.id.progress_indicator) ?: return
        
        lifecycleScope.launch {
            progressIndicator.isVisible = true
            
            try {
                var bitmap = when {
                    customImageUri != null -> {
                        withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openInputStream(customImageUri!!)?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)
                            }
                        }
                    }
                    videoBitmap != null -> videoBitmap
                    else -> null
                }
                
                // Apply 16:9 crop if enabled
                if (bitmap != null && use16by9Ratio) {
                    bitmap = cropTo16by9AspectRatio(bitmap)
                }
                
                if (bitmap != null) {
                    // Ensure the bitmap fills the container completely without black areas
                    bitmap = ensureThumbnailFillsContainer(bitmap)
                    
                    // Generate unique filename based on video path and include scale factor
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(path.toString().toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    
                    val thumbnailFile = File(requireContext().filesDir, "video_thumbnails/${hash}_custom.jpg")
                    thumbnailFile.parentFile?.mkdirs()
                    
                    withContext(Dispatchers.IO) {
                        // Clean up any old thumbnails for this video
                        val basePrefix = hash
                        thumbnailFile.parentFile?.listFiles()?.forEach { file ->
                            if (file.name.startsWith(basePrefix) && 
                                file.absolutePath != thumbnailFile.absolutePath) {
                                file.delete()
                            }
                        }
                        
                        // Save new thumbnail
                        FileOutputStream(thumbnailFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        
                        // Update database with new thumbnail path
                        metadataRepository.updateThumbnailPath(path, thumbnailFile.absolutePath)
                    }
                    
                    Toast.makeText(
                        requireContext(),
                        R.string.video_thumbnail_management_saved_simple,
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Notify listeners that thumbnail has been updated
                    val listener = parentFragment as? Listener ?: activity as? Listener
                    listener?.onVideoThumbnailUpdated(path)
                    
                    dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.video_thumbnail_management_no_thumbnail_simple,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.video_thumbnail_management_error_saving_detailed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                progressIndicator.isVisible = false
            }
        }
    }
    
    private fun cropTo16by9AspectRatio(source: Bitmap): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height
        
        // Target aspect ratio is 16:9
        val targetRatio = 16f / 9f
        
        // Calculate dimensions for the crop
        val resultWidth: Int
        val resultHeight: Int
        
        if (sourceWidth.toFloat() / sourceHeight > targetRatio) {
            // If source is wider than 16:9, crop width
            resultHeight = sourceHeight
            resultWidth = (resultHeight * targetRatio).toInt()
        } else {
            // If source is taller than 16:9, crop height
            resultWidth = sourceWidth
            resultHeight = (resultWidth / targetRatio).toInt()
        }
        
        // Calculate the center crop area
        val cropX = (sourceWidth - resultWidth) / 2
        val cropY = (sourceHeight - resultHeight) / 2
        
        // Create a new bitmap with the cropped image
        return Bitmap.createBitmap(source, cropX, cropY, resultWidth, resultHeight)
    }
    
    // New function to ensure thumbnails fill container completely
    private fun ensureThumbnailFillsContainer(source: Bitmap, targetWidth: Int = 1920, targetHeight: Int = 1080): Bitmap {
        // If source already has same or larger dimensions, just return it
        if (source.width >= targetWidth && source.height >= targetHeight) {
            return source
        }
        
        // Create canvas for drawing
        val canvas = Canvas()
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        
        // Calculate scaling factors to fill the target dimensions
        val scaleWidth = targetWidth / source.width.toFloat()
        val scaleHeight = targetHeight / source.height.toFloat()
        val scaleFactor = max(scaleWidth, scaleHeight)
        
        // Create a bitmap with the target dimensions
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        
        canvas.setBitmap(bitmap)
        
        // Calculate centered position
        val left = (targetWidth - source.width * scaleFactor) / 2
        val top = (targetHeight - source.height * scaleFactor) / 2
        
        // Setup matrix for scaling
        val matrix = Matrix()
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(left, top)
        
        // Draw the scaled bitmap centered in the container
        canvas.drawBitmap(source, matrix, paint)
        
        return bitmap
    }
    
    override fun onDestroyView() {
        releasePlayer()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PATH = "path"
        
        fun show(path: Path, fragment: Fragment) {
            VideoThumbnailManagementDialogFragment().putArgs(
                Args(path)
            ).show(fragment.childFragmentManager, null)
        }
    }

    @kotlinx.parcelize.Parcelize
    class Args(val path: @RawValue Path) : me.zhanghai.android.files.util.ParcelableArgs

    interface Listener {
        fun onVideoThumbnailUpdated(path: Path)
    }
} 