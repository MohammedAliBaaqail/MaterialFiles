/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import coil.load
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isVideo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java8.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.provider.common.VideoMetadataRepository
import me.zhanghai.android.files.util.setDataSource
import me.zhanghai.android.files.util.valueCompat
import kotlin.math.max
import kotlin.math.min

class QuickPreviewPopup(private val context: Context) {
    private enum class GestureState { NONE, SPEED_LOCK, SEEKING, VOLUME, BRIGHTNESS }
    private enum class AspectRatioMode { FIT, FILL, STRETCH, RATIO_16_9, RATIO_4_3 }
    enum class RepeatMode { ALL, ONE, OFF }

    private var popupWindow: PopupWindow? = null
    private var rootView: FrameLayout? = null
    private var containerCard: LinearLayout? = null
    private var imageView: ImageView? = null
    private var videoView: android.widget.VideoView? = null
    private var mediaPlayerRef: MediaPlayer? = null
    private var progressView: ProgressBar? = null
    private var titleText: TextView? = null
    private var timeText: TextView? = null
    private var seekBarProgress: ProgressBar? = null
    private var seekContainer: LinearLayout? = null
    private var closeButton: TextView? = null
    private var originalFileName: String = ""
    private var originalStatusBarColor: Int? = null

    // Compact Ultra-Translucent Full Screen Controls Overlays
    private var topControlBar: LinearLayout? = null
    private var topTitleText: TextView? = null
    private var topBackButton: ImageView? = null
    private var topAspectButton: ImageView? = null
    private var topThumbnailButton: ImageView? = null
    private var topSpeedButton: ImageView? = null
    private var topSpeedBadgeText: TextView? = null
    private var topMoreButton: ImageView? = null

    private var bottomControlBar: LinearLayout? = null
    private var fullTimeText: TextView? = null
    private var fullAspectBadge: TextView? = null
    private var fullSeekBar: SeekBar? = null
    private var playPauseButton: ImageView? = null
    private var rewindButton: ImageView? = null
    private var forwardButton: ImageView? = null
    private var previousVideoButton: ImageView? = null
    private var nextVideoButton: ImageView? = null
    private var repeatButton: ImageView? = null

    // Image Zooming & Swiping Carousel
    private var imageCarouselScrollView: HorizontalScrollView? = null
    private var imageCarouselLayout: LinearLayout? = null
    private var imageScaleFactor = 1.0f

    // Dual Slider Horizontal Speed HUD Overlay Views
    private var dualSliderCard: LinearLayout? = null
    private var speedHudLayout: LinearLayout? = null
    private var speedProgressBar: ProgressBar? = null
    private var speedHudValueText: TextView? = null

    private var seekHudLayout: LinearLayout? = null
    private var seekLabelText: TextView? = null
    private var seekProgressBar: ProgressBar? = null

    // Vertical Volume & Brightness HUD Views
    private var verticalHudCard: LinearLayout? = null
    private var verticalHudValueText: TextView? = null
    private var verticalHudTrackContainer: FrameLayout? = null
    private var verticalHudFillView: View? = null
    private var verticalHudIconView: ImageView? = null

    // Compact Floating Timeline HUD (Transparent, at very bottom)
    private var seekTimelineHudCard: LinearLayout? = null
    private var seekTimelineText: TextView? = null
    private var seekTimelineProgressBar: ProgressBar? = null
    private var seekTimelineSpeedBadge: TextView? = null

    // Double Tap Animation Overlay Views
    private var leftDoubleTapCard: LinearLayout? = null
    private var leftDoubleTapText: TextView? = null
    private var rightDoubleTapCard: LinearLayout? = null
    private var rightDoubleTapText: TextView? = null

    private var currentFilePathString: String = ""
    private var currentPath: Path? = null
    private var currentPlaylist: List<FileItem> = emptyList()
    private var repeatMode: RepeatMode = RepeatMode.ALL
    private var isVideo = false
    private var videoDurationMs = 0
    private var currentSeekPositionMs = 0
    private var isPausedByUser = false
    private var isControlsLocked = false
    private var areControlsVisible = true
    private var isUserDragging = false
    private var isTouchOnSeekBar = false
    private var isActivelyMovingFinger = false
    private var lastActivePointerCount = 1
    private var dragStartFingerX = 0f
    private var dragStartSeekPositionMs = 0
    private var lastSeekTimestampMs = 0L
    private var currentAspectRatio = 1.0f
    private var currentSpeed = 1.0f
    private var gestureState = GestureState.NONE
    private var aspectRatioMode = AspectRatioMode.FIT
    private var isAudioMuted = false
    private var videoRotationDegree = 0f

    var isFullScreen = false
        private set

    // Legacy property alias for compatibility
    val isLocked: Boolean
        get() = isFullScreen

    private var singleTapToggleRunnable: Runnable? = null
    private var lastTapTimeMs = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        dualSliderCard?.visibility = View.GONE
        speedHudLayout?.visibility = View.GONE
        seekHudLayout?.visibility = View.GONE
        verticalHudCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE
    }

    private val hideControlsRunnable = Runnable {
        setControlsVisible(false)
    }

    private val resetActiveMovingRunnable = Runnable {
        isActivelyMovingFinger = false
        isUserDragging = false
        isTouchOnSeekBar = false
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            videoView?.let { v ->
                if (!isActivelyMovingFinger && !isTouchOnSeekBar) {
                    val pos = v.currentPosition
                    if (pos >= 0) {
                        currentSeekPositionMs = pos
                        updateSeekDisplay(currentSeekPositionMs)
                        if (currentFilePathString.isNotEmpty() && pos > 0) {
                            me.zhanghai.android.files.file.VideoPreviewPositionManager.setPosition(
                                currentFilePathString, currentSeekPositionMs
                            )
                        }
                    }
                    if (!v.isPlaying && mediaPlayerRef != null && !isPausedByUser) {
                        try {
                            v.start()
                        } catch (_: Exception) {}
                    }
                }
            }
            if (popupWindow?.isShowing == true && isVideo) {
                mainHandler.postDelayed(this, 200)
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun getPhysicalScreenSize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                return Pair(bounds.width(), bounds.height())
            } else {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                return Pair(metrics.widthPixels, metrics.heightPixels)
            }
        }
        val displayMetrics = context.resources.displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    private fun getMinPlaybackSpeed(): Float {
        return me.zhanghai.android.files.file.VideoPreviewPositionManager.getMinSpeed()
    }

    private fun getMaxPlaybackSpeed(): Float {
        val minS = getMinPlaybackSpeed()
        return me.zhanghai.android.files.file.VideoPreviewPositionManager.getMaxSpeed().coerceAtLeast(minS)
    }

    private fun showSpeedHud(speed: Float) {
        if (!isFullScreen || !isVideo) return
        mainHandler.removeCallbacks(hideHudRunnable)
        verticalHudCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE
        dualSliderCard?.visibility = View.VISIBLE
        speedHudLayout?.visibility = View.VISIBLE
        seekHudLayout?.visibility = View.GONE

        val minS = getMinPlaybackSpeed()
        val maxS = getMaxPlaybackSpeed()
        val progressVal = if (maxS > minS) {
            (((speed - minS) / (maxS - minS)) * 1000).toInt().coerceIn(0, 1000)
        } else 0
        speedProgressBar?.max = 1000
        speedProgressBar?.progress = progressVal

        val formattedSpeed = if (speed % 1.0f == 0f || ((speed * 10) % 1.0f == 0f)) {
            String.format(java.util.Locale.US, "%.1fx", speed)
        } else {
            String.format(java.util.Locale.US, "%.2fx", speed)
        }
        speedHudValueText?.text = formattedSpeed
        topSpeedBadgeText?.text = formattedSpeed
        dualSliderCard?.bringToFront()
    }

    private fun showSeekHud(currentMs: Int, durationMs: Int) {
        if (!isFullScreen || !isVideo) return
        mainHandler.removeCallbacks(hideHudRunnable)
        verticalHudCard?.visibility = View.GONE
        dualSliderCard?.visibility = View.GONE

        if (!areControlsVisible) {
            seekTimelineHudCard?.visibility = View.VISIBLE
            seekTimelineHudCard?.bringToFront()
            mainHandler.postDelayed(hideHudRunnable, 1200)
        } else {
            seekTimelineHudCard?.visibility = View.GONE
        }
    }

    private fun showVerticalHud(isVolume: Boolean, percent: Int) {
        if (!isFullScreen) return
        mainHandler.removeCallbacks(hideHudRunnable)
        dualSliderCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE

        val density = context.resources.displayMetrics.density
        val lp = verticalHudCard?.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            lp.gravity = (if (isVolume) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            if (isVolume) {
                lp.marginEnd = (36 * density).toInt()
                lp.marginStart = 0
            } else {
                lp.marginStart = (36 * density).toInt()
                lp.marginEnd = 0
            }
            verticalHudCard?.layoutParams = lp
        }

        verticalHudValueText?.text = "$percent"
        verticalHudIconView?.setImageResource(
            if (isVolume) R.drawable.ic_volume_white_24dp else R.drawable.ic_brightness_white_24dp
        )

        val totalTrackPx = (140 * density).toInt()
        val fillPx = ((percent / 100f) * totalTrackPx).toInt().coerceIn(0, totalTrackPx)
        val fillLp = verticalHudFillView?.layoutParams as? FrameLayout.LayoutParams
        if (fillLp != null) {
            fillLp.height = fillPx
            verticalHudFillView?.layoutParams = fillLp
        }

        verticalHudCard?.visibility = View.VISIBLE
        verticalHudCard?.bringToFront()
        mainHandler.postDelayed(hideHudRunnable, 1500)
    }

    private fun getExtraHeightPx(): Int {
        val density = context.resources.displayMetrics.density
        return (72 * density).toInt()
    }

    private fun adjustContainerAspectRatio(contentWidth: Int, contentHeight: Int) {
        if (contentWidth <= 0 || contentHeight <= 0) return
        currentAspectRatio = contentWidth.toFloat() / contentHeight.toFloat()

        if (isFullScreen) {
            val (screenW, screenH) = getPhysicalScreenSize()

            when (aspectRatioMode) {
                AspectRatioMode.FIT -> {
                    var targetW = screenW
                    var targetH = (targetW / currentAspectRatio).toInt()
                    if (targetH > screenH) {
                        targetH = screenH
                        targetW = (targetH * currentAspectRatio).toInt()
                    }
                    videoView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                    imageView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                }
                AspectRatioMode.FILL -> {
                    var targetW = screenW
                    var targetH = (targetW / currentAspectRatio).toInt()
                    if (targetH < screenH) {
                        targetH = screenH
                        targetW = (targetH * currentAspectRatio).toInt()
                    }
                    videoView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                    imageView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                }
                AspectRatioMode.STRETCH -> {
                    videoView?.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    imageView?.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                AspectRatioMode.RATIO_16_9 -> {
                    val ratio = 16f / 9f
                    var targetW = screenW
                    var targetH = (targetW / ratio).toInt()
                    if (targetH > screenH) {
                        targetH = screenH
                        targetW = (targetH * ratio).toInt()
                    }
                    videoView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                    imageView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                }
                AspectRatioMode.RATIO_4_3 -> {
                    val ratio = 4f / 3f
                    var targetW = screenW
                    var targetH = (targetW / ratio).toInt()
                    if (targetH > screenH) {
                        targetH = screenH
                        targetW = (targetH * ratio).toInt()
                    }
                    videoView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                    imageView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                        gravity = Gravity.CENTER
                    }
                }
            }
            fullAspectBadge?.text = "[${aspectRatioMode.name.replace('_', ':')}]"
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val extraHeight = getExtraHeightPx()
        val maxW = (displayMetrics.widthPixels * 0.92).toInt()
        val maxH = (displayMetrics.heightPixels * 0.80).toInt()

        var targetW = maxW
        var targetH = (targetW / currentAspectRatio).toInt() + extraHeight

        if (targetH > maxH) {
            targetH = maxH
            val hVid = (targetH - extraHeight).coerceAtLeast(100)
            targetW = (hVid * currentAspectRatio).toInt()
        }

        containerCard?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun setControlsVisible(visible: Boolean) {
        areControlsVisible = visible
        mainHandler.removeCallbacks(hideControlsRunnable)

        if (!isFullScreen) {
            topControlBar?.visibility = View.GONE
            bottomControlBar?.visibility = View.GONE
            seekTimelineHudCard?.visibility = View.GONE
            return
        }

        val targetVisibility = if (visible && !isControlsLocked) View.VISIBLE else View.GONE
        topControlBar?.visibility = targetVisibility
        bottomControlBar?.visibility = if (isVideo) targetVisibility else View.GONE
        topThumbnailButton?.visibility = if (isVideo) targetVisibility else View.GONE
        topSpeedButton?.visibility = if (isVideo) targetVisibility else View.GONE
        topSpeedBadgeText?.visibility = if (isVideo) targetVisibility else View.GONE

        // Update badge with current playback speed every time controls become visible
        if (visible && isVideo) {
            val formattedSpeed = if (currentSpeed % 1.0f == 0f || ((currentSpeed * 10) % 1.0f == 0f)) {
                String.format(java.util.Locale.US, "%.1fx", currentSpeed)
            } else {
                String.format(java.util.Locale.US, "%.2fx", currentSpeed)
            }
            topSpeedBadgeText?.text = formattedSpeed

            val pos = videoView?.currentPosition ?: currentSeekPositionMs
            if (pos > 0) currentSeekPositionMs = pos
            updateSeekDisplay(currentSeekPositionMs)
        }

        if (visible) {
            seekTimelineHudCard?.visibility = View.GONE
        }

        if (visible && !isControlsLocked) {
            mainHandler.postDelayed(hideControlsRunnable, 4000)
        }
    }

    private fun togglePlayPause() {
        videoView?.let { v ->
            if (v.isPlaying) {
                isPausedByUser = true
                v.pause()
                playPauseButton?.setImageResource(R.drawable.ic_play_white_24dp)
            } else {
                isPausedByUser = false
                v.start()
                playPauseButton?.setImageResource(R.drawable.ic_pause_white_24dp)
            }
        }
    }

    private fun performSeek(targetMs: Int) {
        videoView?.let { v ->
            val mp = mediaPlayerRef
            val isExact = me.zhanghai.android.files.file.VideoPreviewPositionManager.getExactSeekMode()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mp != null) {
                try {
                    val mode = if (isExact) MediaPlayer.SEEK_CLOSEST else MediaPlayer.SEEK_CLOSEST_SYNC
                    mp.seekTo(targetMs.toLong(), mode)
                    if (!isPausedByUser) {
                        mp.start()
                    }
                    return
                } catch (_: Exception) {}
            }
            try {
                v.seekTo(targetMs)
                if (!isPausedByUser) {
                    v.start()
                }
            } catch (_: Exception) {}
        }
    }

    private fun getSeekStepMs(): Int =
        me.zhanghai.android.files.file.VideoPreviewPositionManager.getSeekStepSeconds() * 1000

    private fun seekRelative(offsetMs: Int) {
        videoView?.let { v ->
            val dur = if (videoDurationMs > 0) videoDurationMs else v.duration
            val targetMs = (v.currentPosition + offsetMs).coerceIn(0, dur)
            performSeek(targetMs)
            currentSeekPositionMs = targetMs
            updateSeekDisplay(targetMs)
            val isForward = offsetMs > 0
            val stepSec = Math.abs(offsetMs) / 1000
            if (isFullScreen) {
                showDoubleTapAnimation(isForward, stepSec)
                showDoubleTapSeekHud(isForward, stepSec)
            }
        }
    }

    private fun playFile(file: FileItem) {
        currentFilePathString = file.path.toString()
        currentPath = file.path
        originalFileName = file.name
        topTitleText?.text = file.name
        titleText?.text = file.name

        try {
            progressView?.visibility = View.VISIBLE
            val fileObj = try { file.path.toFile() } catch (_: Exception) { null }
            if (fileObj != null && fileObj.exists() && fileObj.canRead()) {
                videoView?.setVideoPath(fileObj.absolutePath)
            } else {
                val contentUri = file.path.fileProviderUri
                videoView?.setVideoURI(contentUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            progressView?.visibility = View.GONE
        }
    }

    fun playNextVideoInFolder(): Boolean {
        val mediaFiles = currentPlaylist.filter { it.mimeType.isVideo }
        if (mediaFiles.isEmpty()) return false
        val currentIndex = mediaFiles.indexOfFirst { it.path.toString() == currentFilePathString }
        val nextIndex = if (currentIndex != -1 && currentIndex + 1 < mediaFiles.size) {
            currentIndex + 1
        } else if (mediaFiles.isNotEmpty()) {
            0
        } else {
            -1
        }
        if (nextIndex != -1) {
            playFile(mediaFiles[nextIndex])
            return true
        }
        return false
    }

    fun playPreviousVideoInFolder(): Boolean {
        val mediaFiles = currentPlaylist.filter { it.mimeType.isVideo }
        if (mediaFiles.isEmpty()) return false
        val currentIndex = mediaFiles.indexOfFirst { it.path.toString() == currentFilePathString }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else if (mediaFiles.isNotEmpty()) {
            mediaFiles.size - 1
        } else {
            -1
        }
        if (prevIndex != -1) {
            playFile(mediaFiles[prevIndex])
            return true
        }
        return false
    }

    fun playNextImageInFolder(): Boolean {
        val imageFiles = currentPlaylist.filter { it.mimeType.isImage }
        if (imageFiles.isEmpty()) return false
        val currentIndex = imageFiles.indexOfFirst { it.path.toString() == currentFilePathString }
        val nextIndex = if (currentIndex != -1 && currentIndex + 1 < imageFiles.size) {
            currentIndex + 1
        } else if (imageFiles.isNotEmpty()) {
            0
        } else {
            -1
        }
        if (nextIndex != -1) {
            playImageFile(imageFiles[nextIndex])
            return true
        }
        return false
    }

    fun playPreviousImageInFolder(): Boolean {
        val imageFiles = currentPlaylist.filter { it.mimeType.isImage }
        if (imageFiles.isEmpty()) return false
        val currentIndex = imageFiles.indexOfFirst { it.path.toString() == currentFilePathString }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else if (imageFiles.isNotEmpty()) {
            imageFiles.size - 1
        } else {
            -1
        }
        if (prevIndex != -1) {
            playImageFile(imageFiles[prevIndex])
            return true
        }
        return false
    }

    private fun resetImageZoom() {
        imageScaleFactor = 1.0f
        imageView?.scaleX = 1.0f
        imageView?.scaleY = 1.0f
    }

    private fun playImageFile(file: FileItem) {
        currentFilePathString = file.path.toString()
        originalFileName = file.name
        topTitleText?.text = file.name
        titleText?.text = file.name

        resetImageZoom()
        progressView?.visibility = View.VISIBLE
        imageView?.load(file.path to file.attributes) {
            listener(
                onSuccess = { _, result ->
                    progressView?.visibility = View.GONE
                    val d = result.drawable
                    adjustContainerAspectRatio(d.intrinsicWidth, d.intrinsicHeight)
                },
                onError = { _, _ ->
                    progressView?.visibility = View.GONE
                }
            )
        }
        updateImageCarouselSelection()
    }

    private fun populateImageCarousel() {
        imageCarouselLayout?.removeAllViews()
        val imageFiles = currentPlaylist.filter { it.mimeType.isImage }
        if (imageFiles.isEmpty()) return

        for (imgFile in imageFiles) {
            val isSelected = imgFile.path.toString() == currentFilePathString
            val thumbView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                    marginStart = 4.toPx()
                    marginEnd = 4.toPx()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(2.toPx(), 2.toPx(), 2.toPx(), 2.toPx())
                if (isSelected) {
                    setBackgroundColor(Color.WHITE)
                } else {
                    setBackgroundColor(Color.parseColor("#33FFFFFF"))
                }
                load(imgFile.path to imgFile.attributes)
                setOnClickListener {
                    playImageFile(imgFile)
                }
            }
            imageCarouselLayout?.addView(thumbView)
        }
    }

    private fun updateImageCarouselSelection() {
        val layout = imageCarouselLayout ?: return
        val imageFiles = currentPlaylist.filter { it.mimeType.isImage }
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i) as? ImageView ?: continue
            val imgFile = imageFiles.getOrNull(i) ?: continue
            val isSelected = imgFile.path.toString() == currentFilePathString
            if (isSelected) {
                child.setBackgroundColor(Color.WHITE)
            } else {
                child.setBackgroundColor(Color.parseColor("#33FFFFFF"))
            }
        }
    }

    private fun updateRepeatButtonUI() {
        when (repeatMode) {
            RepeatMode.ALL -> {
                repeatButton?.setImageResource(R.drawable.ic_repeat_white_24dp)
                repeatButton?.setColorFilter(Color.WHITE)
                Toast.makeText(context, "Repeat Mode: Folder (All Videos)", Toast.LENGTH_SHORT).show()
            }
            RepeatMode.ONE -> {
                repeatButton?.setImageResource(R.drawable.ic_repeat_one_white_24dp)
                repeatButton?.setColorFilter(Color.WHITE)
                Toast.makeText(context, "Repeat Mode: Single Video", Toast.LENGTH_SHORT).show()
            }
            RepeatMode.OFF -> {
                repeatButton?.setImageResource(R.drawable.ic_repeat_white_24dp)
                repeatButton?.setColorFilter(Color.parseColor("#55FFFFFF"))
                Toast.makeText(context, "Repeat Mode: Off", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cycleAspectRatio() {
        aspectRatioMode = when (aspectRatioMode) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.STRETCH
            AspectRatioMode.STRETCH -> AspectRatioMode.RATIO_16_9
            AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
            AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
        }
        val vw = mediaPlayerRef?.videoWidth ?: 0
        val vh = mediaPlayerRef?.videoHeight ?: 0
        adjustContainerAspectRatio(vw, vh)
    }

    private fun showSpeedMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        val minS = getMinPlaybackSpeed()
        val maxS = getMaxPlaybackSpeed()
        val candidateSpeeds = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 6.0f)
        val validSpeeds = (candidateSpeeds + listOf(minS, maxS))
            .filter { it in minS..maxS }
            .distinct()
            .sorted()

        for (s in validSpeeds) {
            val label = if (s % 1.0f == 0f || ((s * 10) % 1.0f == 0f)) {
                String.format(java.util.Locale.US, "%.1fx", s)
            } else {
                String.format(java.util.Locale.US, "%.2fx", s)
            }
            popup.menu.add(label)
        }
        popup.setOnMenuItemClickListener { item ->
            val speedVal = item.title.toString().removeSuffix("x").toFloatOrNull() ?: 1.0f
            currentSpeed = speedVal
            try {
                mediaPlayerRef?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = speedVal
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            me.zhanghai.android.files.file.VideoPreviewPositionManager.setLastSpeed(speedVal)
            updateSeekDisplay(currentSeekPositionMs)
            showSpeedHud(speedVal)
            true
        }
        popup.show()
    }

    private fun showMoreOptionsMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(if (isAudioMuted) "Unmute Audio" else "Mute Audio")
        popup.menu.add("Rotate 90°")
        popup.menu.add("Repeat Mode")
        if (isVideo) popup.menu.add("Set Frame as Thumbnail")
        if (isVideo) {
            val stepSec = me.zhanghai.android.files.file.VideoPreviewPositionManager.getSeekStepSeconds()
            popup.menu.add("Seek Step (${stepSec}s)")
            val speed1 = me.zhanghai.android.files.file.VideoPreviewPositionManager.getGestureSeekSpeed()
            val speed2 = me.zhanghai.android.files.file.VideoPreviewPositionManager.getTwoFingerGestureSeekSpeed()
            popup.menu.add(String.format(java.util.Locale.US, "Gesture Sliding Speed (1F: %.1fx, 2F: %.1fx)", speed1, speed2))
            val isExact = me.zhanghai.android.files.file.VideoPreviewPositionManager.getExactSeekMode()
            popup.menu.add(if (isExact) "Seeking: Exact (Accurate) Frame" else "Seeking: Keyframe (Fast)")
            popup.menu.add("Speed Range Settings")
        }
        popup.setOnMenuItemClickListener { item ->
            val title = item.title.toString()
            when {
                title == "Unmute Audio" || title == "Mute Audio" -> {
                    isAudioMuted = !isAudioMuted
                    mediaPlayerRef?.setVolume(if (isAudioMuted) 0f else 1f, if (isAudioMuted) 0f else 1f)
                }
                title == "Rotate 90°" -> {
                    videoRotationDegree = (videoRotationDegree + 90f) % 360f
                    videoView?.rotation = videoRotationDegree
                    imageView?.rotation = videoRotationDegree
                }
                title == "Repeat Mode" -> {
                    repeatMode = when (repeatMode) {
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                        RepeatMode.OFF -> RepeatMode.ALL
                    }
                    updateRepeatButtonUI()
                }
                title == "Set Frame as Thumbnail" -> captureCurrentFrameAsThumbnail()
                title.startsWith("Seek Step") -> showSeekStepDialog()
                title.startsWith("Gesture Sliding Speed") -> showGestureSpeedDialog()
                title.startsWith("Seeking:") -> {
                    val newExact = !me.zhanghai.android.files.file.VideoPreviewPositionManager.getExactSeekMode()
                    me.zhanghai.android.files.file.VideoPreviewPositionManager.setExactSeekMode(newExact)
                    val modeName = if (newExact) "Exact (Accurate) Frame" else "Keyframe (Fast)"
                    Toast.makeText(context, "Seeking Mode: $modeName", Toast.LENGTH_SHORT).show()
                }
                title == "Speed Range Settings" -> showSpeedRangeDialog()
            }
            true
        }
        popup.show()
    }

    private fun showSeekStepDialog() {
        val density = context.resources.displayMetrics.density
        val currentStep = me.zhanghai.android.files.file.VideoPreviewPositionManager.getSeekStepSeconds().coerceIn(1, 30).toFloat()

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(Color.parseColor("#1E2030"))
        }

        val title = TextView(context).apply {
            text = "Forward / Backward Step Duration"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        dialogLayout.addView(title)

        val valueLabel = TextView(context).apply {
            text = "Step Duration: ${currentStep.toInt()}s"
            setTextColor(Color.parseColor("#7C85FC"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        dialogLayout.addView(valueLabel)

        val slider = com.google.android.material.slider.Slider(context).apply {
            valueFrom = 1.0f
            valueTo = 30.0f
            stepSize = 1.0f
            value = currentStep
            setLabelFormatter { value -> "${value.toInt()}s" }
            val colorAccent = Color.parseColor("#7C85FC")
            trackActiveTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            addOnChangeListener { _, value, _ ->
                valueLabel.text = "Step Duration: ${value.toInt()}s"
            }
        }
        dialogLayout.addView(slider)

        val hint = TextView(context).apply {
            text = "Controls the forward / backward jump duration (1s to 30s) used by the on-screen buttons and double-tap gestures."
            setTextColor(Color.parseColor("#607090"))
            textSize = 11.5f
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        dialogLayout.addView(hint)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                val newSec = slider.value.toInt().coerceIn(1, 30)
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setSeekStepSeconds(newSec)
                Toast.makeText(context, "Seek step set to ${newSec}s", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showGestureSpeedDialog() {
        val density = context.resources.displayMetrics.density
        val currentSpeed1 = me.zhanghai.android.files.file.VideoPreviewPositionManager.getGestureSeekSpeed().coerceIn(0.1f, 3.0f)
        val currentSpeed2 = me.zhanghai.android.files.file.VideoPreviewPositionManager.getTwoFingerGestureSeekSpeed().coerceIn(0.1f, 3.0f)

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(Color.parseColor("#1E2030"))
        }

        val title = TextView(context).apply {
            text = "Screen Gesture Sliding Speed"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        dialogLayout.addView(title)

        // 1-Finger Section
        val label1 = TextView(context).apply {
            val speedStr = String.format(java.util.Locale.US, "%.1fx", currentSpeed1)
            text = "1-Finger Speed: $speedStr"
            setTextColor(Color.parseColor("#7C85FC"))
            textSize = 14.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        dialogLayout.addView(label1)

        val slider1 = com.google.android.material.slider.Slider(context).apply {
            valueFrom = 0.1f
            valueTo = 3.0f
            stepSize = 0.1f
            value = (Math.round(currentSpeed1 * 10) / 10.0f).coerceIn(0.1f, 3.0f)
            setLabelFormatter { value -> String.format(java.util.Locale.US, "%.1fx", value) }
            val colorAccent = Color.parseColor("#7C85FC")
            trackActiveTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            addOnChangeListener { _, value, _ ->
                val v = Math.round(value * 10) / 10.0f
                label1.text = String.format(java.util.Locale.US, "1-Finger Speed: %.1fx", v)
            }
        }
        dialogLayout.addView(slider1)

        // 2-Fingers Section
        val label2 = TextView(context).apply {
            val speedStr = String.format(java.util.Locale.US, "%.1fx", currentSpeed2)
            text = "2-Fingers Speed: $speedStr"
            setTextColor(Color.parseColor("#4DEEEA"))
            textSize = 14.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }
        dialogLayout.addView(label2)

        val slider2 = com.google.android.material.slider.Slider(context).apply {
            valueFrom = 0.1f
            valueTo = 3.0f
            stepSize = 0.1f
            value = (Math.round(currentSpeed2 * 10) / 10.0f).coerceIn(0.1f, 3.0f)
            setLabelFormatter { value -> String.format(java.util.Locale.US, "%.1fx", value) }
            val colorAccent = Color.parseColor("#4DEEEA")
            trackActiveTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            addOnChangeListener { _, value, _ ->
                val v = Math.round(value * 10) / 10.0f
                label2.text = String.format(java.util.Locale.US, "2-Fingers Speed: %.1fx", v)
            }
        }
        dialogLayout.addView(slider2)

        val hint = TextView(context).apply {
            text = "Slide with 1 finger for primary speed. Touching with a 2nd finger dynamically switches to 2-finger speed, and releasing it returns to 1-finger speed."
            setTextColor(Color.parseColor("#8090B0"))
            textSize = 11.5f
            setPadding(0, (8 * density).toInt(), 0, (6 * density).toInt())
        }
        dialogLayout.addView(hint)

        // Show Indicator Checkbox Option
        val showIndicatorCurrent = me.zhanghai.android.files.file.VideoPreviewPositionManager.getShowGestureSpeedIndicator()
        val checkboxRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }

        val checkbox = com.google.android.material.checkbox.MaterialCheckBox(context).apply {
            isChecked = showIndicatorCurrent
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7C85FC"))
        }
        checkboxRow.addView(checkbox)

        val checkboxTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * density).toInt(), 0, 0, 0)
        }
        val checkboxTitle = TextView(context).apply {
            text = "Show sliding speed indicator"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val checkboxSubtitle = TextView(context).apply {
            text = "Displays current speed & finger indicator on screen while sliding"
            setTextColor(Color.parseColor("#8090B0"))
            textSize = 11.5f
        }
        checkboxTextLayout.addView(checkboxTitle)
        checkboxTextLayout.addView(checkboxSubtitle)
        checkboxRow.addView(checkboxTextLayout)
        checkboxRow.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
        }
        dialogLayout.addView(checkboxRow)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                val newSpeed1 = Math.round(slider1.value * 10) / 10.0f
                val newSpeed2 = Math.round(slider2.value * 10) / 10.0f
                val newShowIndicator = checkbox.isChecked
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setGestureSeekSpeed(newSpeed1)
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setTwoFingerGestureSeekSpeed(newSpeed2)
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setShowGestureSpeedIndicator(newShowIndicator)
                Toast.makeText(context, String.format(java.util.Locale.US, "Gesture speeds: 1-Finger %.1fx, 2-Fingers %.1fx (Indicator: %s)", newSpeed1, newSpeed2, if (newShowIndicator) "On" else "Off"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showDoubleTapAnimation(isForward: Boolean, stepSec: Int) {
        val card = (if (isForward) rightDoubleTapCard else leftDoubleTapCard) ?: return
        val otherCard = if (isForward) leftDoubleTapCard else rightDoubleTapCard
        val textView = if (isForward) rightDoubleTapText else leftDoubleTapText

        otherCard?.visibility = View.GONE
        otherCard?.animate()?.cancel()

        val prefix = if (isForward) "+" else "-"
        textView?.text = "$prefix${stepSec}s"

        card.apply {
            animate()?.cancel()
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
            bringToFront()
            animate()
                .alpha(1f)
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(160)
                .withEndAction {
                    animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            postDelayed({
                                animate()
                                    .alpha(0f)
                                    .scaleX(0.7f)
                                    .scaleY(0.7f)
                                    .setDuration(220)
                                    .withEndAction {
                                        visibility = View.GONE
                                    }
                                    .start()
                            }, 450)
                        }
                        .start()
                }
                .start()
        }
    }

    private fun showDoubleTapSeekHud(isForward: Boolean, seconds: Int) {
        if (!isFullScreen || !isVideo) return
        mainHandler.removeCallbacks(hideHudRunnable)
        verticalHudCard?.visibility = View.GONE
        speedHudLayout?.visibility = View.GONE
        seekHudLayout?.visibility = View.VISIBLE
        dualSliderCard?.visibility = View.VISIBLE
        seekTimelineHudCard?.visibility = View.GONE

        val symbol = if (isForward) "⏩ +${seconds}s" else "⏪ -${seconds}s"
        seekLabelText?.text = symbol
        val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
        if (dur > 0) {
            val progressVal = ((currentSeekPositionMs.toFloat() / dur) * 1000).toInt().coerceIn(0, 1000)
            seekProgressBar?.max = 1000
            seekProgressBar?.progress = progressVal
        }
        dualSliderCard?.bringToFront()
        mainHandler.postDelayed(hideHudRunnable, 1000L)
    }

    private fun captureCurrentFrameAsThumbnail() {
        val targetPath = currentPath ?: return
        val posMs = (if (currentSeekPositionMs > 0) currentSeekPositionMs else (videoView?.currentPosition ?: 0)).toLong()
        Toast.makeText(context, "Capturing current frame for thumbnail...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var extractedBitmap: Bitmap? = null
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(targetPath)
                    val timeMicros = posMs * 1000L
                    extractedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeMicros,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            1920,
                            1080
                        ) ?: retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                }

                if (extractedBitmap != null) {
                    val hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(targetPath.toString().toByteArray())
                        .joinToString("") { "%02x".format(it) }

                    val thumbnailDir = File(context.filesDir, "video_thumbnails")
                    thumbnailDir.mkdirs()
                    val thumbnailFile = File(thumbnailDir, "${hash}_custom.jpg")

                    thumbnailDir.listFiles()?.forEach { f ->
                        if (f.name.startsWith(hash) && f.absolutePath != thumbnailFile.absolutePath) {
                            f.delete()
                        }
                    }

                    FileOutputStream(thumbnailFile).use { out ->
                        extractedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    val repo = VideoMetadataRepository(context)
                    repo.updateThumbnailPath(targetPath, thumbnailFile.absolutePath)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Thumbnail set to current frame!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not capture frame at current position", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error setting thumbnail: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSpeedRangeDialog() {
        val density = context.resources.displayMetrics.density

        val dialogLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(android.graphics.Color.parseColor("#1E2030"))
        }

        val title = android.widget.TextView(context).apply {
            text = "Speed Slider Range"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (14 * density).toInt())
        }
        dialogLayout.addView(title)

        // Snap to nearest 0.1 to satisfy RangeSlider stepSize validation
        val snapToStep: (Float) -> Float = { v -> (Math.round(v * 10) / 10.0f).coerceIn(0.1f, 6.0f) }
        val currentMin = snapToStep(me.zhanghai.android.files.file.VideoPreviewPositionManager.getMinSpeed())
        val currentMax = snapToStep(me.zhanghai.android.files.file.VideoPreviewPositionManager.getMaxSpeed())
            .coerceAtLeast(currentMin)

        // Label showing selected range
        val rangeLabel = android.widget.TextView(context).apply {
            val minStr = String.format(java.util.Locale.US, "%.1f", currentMin)
            val maxStr = String.format(java.util.Locale.US, "%.1f", currentMax)
            text = "Range: ${minStr}x  →  ${maxStr}x"
            setTextColor(android.graphics.Color.parseColor("#B0B8D0"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        dialogLayout.addView(rangeLabel)

        // Use Material RangeSlider for dual-thumb selection (stepSize=0.1, max 6.0f)
        val rangeSlider = com.google.android.material.slider.RangeSlider(context).apply {
            valueFrom = 0.1f
            valueTo = 6.0f
            stepSize = 0.1f
            // Values MUST be set after stepSize and must align to 0.1 steps
            values = listOf(currentMin, currentMax)
            setLabelFormatter { value ->
                String.format(java.util.Locale.US, "%.1fx", value)
            }
            val colorAccent = android.graphics.Color.parseColor("#7C85FC")
            trackActiveTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorAccent)
            addOnChangeListener { slider, _, _ ->
                val vals = slider.values
                val lo = vals[0]
                val hi = vals[1]
                val minStr2 = String.format(java.util.Locale.US, "%.1f", lo)
                val maxStr2 = String.format(java.util.Locale.US, "%.1f", hi)
                rangeLabel.text = "Range: ${minStr2}x  →  ${maxStr2}x"
            }
        }
        dialogLayout.addView(rangeSlider)

        val hintText = android.widget.TextView(context).apply {
            text = "Drag both thumbs to set the min and max speed for the speed slider."
            setTextColor(android.graphics.Color.parseColor("#607090"))
            textSize = 11.5f
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        }
        dialogLayout.addView(hintText)

        // Device speed limit note
        val limitNote = android.widget.TextView(context).apply {
            text = "⚠️ Android MediaPlayer typically supports speeds up to ~6x. Speeds above that may have no effect on some devices."
            setTextColor(android.graphics.Color.parseColor("#C09040"))
            textSize = 10.5f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }
        dialogLayout.addView(limitNote)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                val vals = rangeSlider.values
                val newMin = snapToStep(vals[0])
                val newMax = snapToStep(vals[1]).coerceAtLeast(newMin)
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setMinSpeed(newMin)
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setMaxSpeed(newMax)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1E2030"))
        )
        dialog.show()
    }

    fun applySavedSpeed() {
        if (!isVideo || mediaPlayerRef == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        var savedSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
        if (savedSpeed == 1.0f) {
            savedSpeed = 2.0f
        }
        currentSpeed = savedSpeed
        try {
            mediaPlayerRef?.let { mp ->
                val params = mp.playbackParams
                params.speed = savedSpeed
                mp.playbackParams = params
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateSeekDisplay(currentSeekPositionMs)
        showSpeedHud(currentSpeed)
    }

    fun updatePlaybackSpeedHorizontal(dxPx: Float) {
        if (!isVideo || mediaPlayerRef == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val savedSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
        val minS = getMinPlaybackSpeed()
        val maxS = getMaxPlaybackSpeed()
        val targetSpeed = (savedSpeed + (dxPx / 150f)).coerceIn(minS, maxS)

        if (Math.abs(targetSpeed - currentSpeed) > 0.05f || currentSpeed != targetSpeed) {
            currentSpeed = targetSpeed
            try {
                mediaPlayerRef?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = targetSpeed
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            me.zhanghai.android.files.file.VideoPreviewPositionManager.setLastSpeed(targetSpeed)
            updateSeekDisplay(currentSeekPositionMs)
        }
        showSpeedHud(currentSpeed)
    }

    fun updatePlaybackSpeed(offsetPx: Float) {
        updatePlaybackSpeedHorizontal(offsetPx)
    }

    fun resetPlaybackSpeed() {
        if (currentSpeed != 1.0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            currentSpeed = 1.0f
            try {
                mediaPlayerRef?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = 1.0f
                    mp.playbackParams = params
                }
            } catch (_: Exception) {}
            updateSeekDisplay(currentSeekPositionMs)
        }
    }

    fun openFullScreen() {
        if (isFullScreen) return
        isFullScreen = true
        popupWindow?.update()

        context.findActivity()?.window?.let { window ->
            if (originalStatusBarColor == null) {
                originalStatusBarColor = window.statusBarColor
            }
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        rootView?.setBackgroundColor(Color.BLACK)
        containerCard?.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        containerCard?.setBackgroundColor(Color.BLACK)
        containerCard?.setPadding(0, 0, 0, 0)
        titleText?.visibility = View.GONE
        seekContainer?.visibility = View.GONE
        closeButton?.visibility = View.GONE

        topControlBar?.visibility = View.VISIBLE
        bottomControlBar?.visibility = if (isVideo) View.VISIBLE else View.GONE
        topSpeedButton?.visibility = if (isVideo) View.VISIBLE else View.GONE
        topTitleText?.text = originalFileName

        val vw = mediaPlayerRef?.videoWidth ?: 0
        val vh = mediaPlayerRef?.videoHeight ?: 0
        adjustContainerAspectRatio(vw, vh)

        setControlsVisible(true)
        setupGestureControls()
    }

    fun lockPreview() {
        openFullScreen()
    }

    private fun setupGestureControls() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isVideo && imageView != null) {
                    imageScaleFactor = (imageScaleFactor * detector.scaleFactor).coerceIn(1.0f, 5.0f)
                    imageView?.scaleX = imageScaleFactor
                    imageView?.scaleY = imageScaleFactor
                    return true
                }
                return false
            }
        })

        rootView?.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var touchDownTimeMs = 0L
            private var isRightSide = false
            private var initialVolume = 0
            private var maxVolume = 15
            private var initialVolumePercent = 50
            private var initialBrightness = 0.5f
            private var initialHoldSpeed = 1.5f

            private val longPressSpeedRunnable = Runnable {
                if (isFullScreen && (gestureState == GestureState.NONE) && isVideo) {
                    gestureState = GestureState.SPEED_LOCK
                    initialHoldSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
                    applySavedSpeed()
                }
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (!isVideo && imageView != null) {
                    scaleDetector.onTouchEvent(event)
                }

                val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                val screenHeight = context.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastActivePointerCount = 1
                        downX = event.rawX
                        downY = event.rawY
                        touchDownTimeMs = System.currentTimeMillis()
                        isRightSide = downX > (screenWidth / 2f)
                        gestureState = GestureState.NONE
                        initialHoldSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()

                        audioManager?.let { am ->
                            maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            initialVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            initialVolumePercent = if (maxVolume > 0) ((initialVolume.toFloat() / maxVolume) * 100).toInt() else 50
                        }

                        val activity = context.findActivity()
                        val lp = activity?.window?.attributes
                        initialBrightness = lp?.screenBrightness ?: 0.5f
                        if (initialBrightness < 0f) initialBrightness = 0.5f

                        mainHandler.removeCallbacks(longPressSpeedRunnable)
                        if (isFullScreen && isVideo) {
                            mainHandler.postDelayed(longPressSpeedRunnable, 380L)
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        mainHandler.removeCallbacks(longPressSpeedRunnable)
                        if (gestureState == GestureState.SEEKING && isVideo) {
                            lastActivePointerCount = event.pointerCount
                            dragStartFingerX = getAveragePointerX(event)
                            dragStartSeekPositionMs = currentSeekPositionMs
                            onDragDeltaRaw(dragStartFingerX, event.pointerCount)
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (gestureState == GestureState.SEEKING && isVideo) {
                            val remainingCount = (event.pointerCount - 1).coerceAtLeast(1)
                            lastActivePointerCount = remainingCount
                            dragStartFingerX = getRemainingPointerX(event)
                            dragStartSeekPositionMs = currentSeekPositionMs
                            onDragDeltaRaw(dragStartFingerX, remainingCount)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pointerCount = event.pointerCount
                        val currentX = getAveragePointerX(event)
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)
                        val holdDurationMs = System.currentTimeMillis() - touchDownTimeMs

                        if (absDx > 25 || absDy > 25) {
                            mainHandler.removeCallbacks(longPressSpeedRunnable)
                        }

                        // SPEED_LOCK MODE (STRICTLY LEFT AND RIGHT DRAG ONLY FOR VIDEO)
                        if (gestureState == GestureState.SPEED_LOCK && isVideo) {
                            val minS = getMinPlaybackSpeed()
                            val maxS = getMaxPlaybackSpeed()
                            val targetSpeed = (initialHoldSpeed + (dx / 150f)).coerceIn(minS, maxS)
                            if (Math.abs(targetSpeed - currentSpeed) > 0.05f || currentSpeed != targetSpeed) {
                                currentSpeed = targetSpeed
                                try {
                                    mediaPlayerRef?.let { mp ->
                                        val params = mp.playbackParams
                                        params.speed = targetSpeed
                                        mp.playbackParams = params
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                me.zhanghai.android.files.file.VideoPreviewPositionManager.setLastSpeed(targetSpeed)
                                updateSeekDisplay(currentSeekPositionMs)
                            }
                            showSpeedHud(currentSpeed)
                            return true
                        }

                        // SEEKING MODE FOR VIDEO
                        if (gestureState == GestureState.SEEKING && isVideo) {
                            onDragDeltaRaw(currentX, pointerCount)
                            return true
                        }

                        // INITIAL GESTURE SELECTION
                        if (gestureState == GestureState.NONE && (absDx > 20 || absDy > 20)) {
                            val isPressAndHold = holdDurationMs >= 380L

                            if (dy < -100f && !isFullScreen) {
                                openFullScreen()
                            } else if (isFullScreen && isPressAndHold && isVideo) {
                                gestureState = GestureState.SPEED_LOCK
                                initialHoldSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
                                applySavedSpeed()
                            } else if (absDx > absDy && isVideo) {
                                gestureState = GestureState.SEEKING
                                startDrag()
                                onDragDeltaRaw(currentX, pointerCount)
                            } else if (!isPressAndHold && isVideo) {
                                // FAST SWIPE ONLY: Adjust Volume or Brightness (VIDEO ONLY)
                                if (isRightSide) {
                                    gestureState = GestureState.VOLUME
                                } else {
                                    gestureState = GestureState.BRIGHTNESS
                                }
                            }
                        }

                        // VOLUME & BRIGHTNESS ADJUSTMENTS
                        if (gestureState == GestureState.VOLUME) {
                            val deltaRatio = -dy / (screenHeight * 0.4f)
                            val deltaPercent = (deltaRatio * 100).toInt()
                            val targetPercent = (initialVolumePercent + deltaPercent).coerceIn(0, 100)

                            audioManager?.let { am ->
                                val targetVolStream = ((targetPercent / 100f) * maxVolume).toInt().coerceIn(0, maxVolume)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolStream, 0)
                            }
                            val volFloat = targetPercent / 100f
                            try {
                                mediaPlayerRef?.setVolume(volFloat, volFloat)
                            } catch (_: Exception) {}

                            showVerticalHud(isVolume = true, percent = targetPercent)
                        } else if (gestureState == GestureState.BRIGHTNESS) {
                            val deltaRatio = -dy / (screenHeight * 0.4f)
                            val activity = context.findActivity()
                            activity?.window?.let { window ->
                                val targetBrightness = (initialBrightness + deltaRatio).coerceIn(0.05f, 1.0f)
                                val lp = window.attributes
                                lp.screenBrightness = targetBrightness
                                window.attributes = lp
                                val percent = (targetBrightness * 100).toInt().coerceIn(1, 100)
                                showVerticalHud(isVolume = false, percent = percent)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longPressSpeedRunnable)
                        mainHandler.removeCallbacks(hideHudRunnable)
                        dualSliderCard?.visibility = View.GONE
                        speedHudLayout?.visibility = View.GONE
                        seekHudLayout?.visibility = View.GONE
                        verticalHudCard?.visibility = View.GONE
                        seekTimelineHudCard?.visibility = View.GONE

                        val now = System.currentTimeMillis()
                        val dt = now - touchDownTimeMs
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)

                        // IMAGE SWIPING OR SINGLE TAP TOGGLE CAROUSEL / CONTROLS
                        if (!isVideo) {
                            if (absDx > 80 && absDx > absDy) {
                                if (dx < 0) {
                                    playNextImageInFolder()
                                } else {
                                    playPreviousImageInFolder()
                                }
                            } else if (dt < 250 && absDx < 20 && absDy < 20) {
                                val currentVis = imageCarouselScrollView?.visibility ?: View.GONE
                                val nextVis = if (currentVis == View.VISIBLE) View.GONE else View.VISIBLE
                                imageCarouselScrollView?.visibility = nextVis
                                topControlBar?.visibility = nextVis
                                if (nextVis == View.VISIBLE) {
                                    populateImageCarousel()
                                }
                            }
                        }

                        val wasSeeking = (gestureState == GestureState.SEEKING)
                        val isTap = (isVideo && isFullScreen && dt < 280 && absDx < 25 && absDy < 25 && gestureState == GestureState.NONE)

                        // SINGLE TAP / DOUBLE TAP HANDLING FOR FULLSCREEN VIDEO
                        if (isTap) {
                            if (now - lastTapTimeMs < 350L && Math.abs(event.rawX - lastTapX) < 120 && Math.abs(event.rawY - lastTapY) < 120) {
                                // DOUBLE TAP: Left = rewind, Right = fast forward
                                singleTapToggleRunnable?.let { mainHandler.removeCallbacks(it) }
                                singleTapToggleRunnable = null
                                lastTapTimeMs = 0L

                                val isRight = event.rawX > (screenWidth / 2f)
                                val stepSec = me.zhanghai.android.files.file.VideoPreviewPositionManager.getSeekStepSeconds()
                                val stepMs = stepSec * 1000
                                if (isRight) {
                                    seekRelative(stepMs)
                                } else {
                                    seekRelative(-stepMs)
                                }
                            } else {
                                // First tap: schedule controls toggle
                                lastTapTimeMs = now
                                lastTapX = event.rawX
                                lastTapY = event.rawY
                                singleTapToggleRunnable?.let { mainHandler.removeCallbacks(it) }
                                val toggleTask = Runnable {
                                    setControlsVisible(!areControlsVisible)
                                }
                                singleTapToggleRunnable = toggleTask
                                mainHandler.postDelayed(toggleTask, 280L)
                            }
                        }

                        resetPlaybackSpeed()
                        onTouchUp(wasSeeking)
                        gestureState = GestureState.NONE
                    }
                }
                return true
            }
        })
    }

    fun startDrag() {
        if (isUserDragging) return
        isUserDragging = true
        val pos = videoView?.currentPosition ?: 0
        dragStartSeekPositionMs = if (pos > 0) pos else currentSeekPositionMs
    }

    fun show(anchorView: View, file: FileItem, playlist: List<FileItem> = emptyList()) {
        dismiss()

        isFullScreen = false
        isUserDragging = false
        isActivelyMovingFinger = false
        isPausedByUser = false
        isControlsLocked = false
        areControlsVisible = true
        currentSpeed = 1.0f
        gestureState = GestureState.NONE
        aspectRatioMode = AspectRatioMode.FIT
        originalFileName = file.name
        currentFilePathString = file.path.toString()
        currentPath = file.path
        currentPlaylist = playlist
        isVideo = file.mimeType.isVideo

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#B0000000"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = false
            setOnApplyWindowInsetsListener { _, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsets.CONSUMED
                } else {
                    @Suppress("DEPRECATION")
                    insets.consumeSystemWindowInsets()
                }
            }
            setOnTouchListener { _, _ -> true }
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newW = right - left
                val newH = bottom - top
                val oldW = oldRight - oldLeft
                val oldH = oldBottom - oldTop
                if (newW > 0 && newH > 0 && Math.abs(newW - oldW) > 100) {
                    mediaPlayerRef?.let { mp ->
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            adjustContainerAspectRatio(mp.videoWidth, mp.videoHeight)
                        }
                    }
                }
            }
        }
        rootView = root

        // Main preview container card
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(0, 0, 0, 0)
            elevation = 16f
            fitsSystemWindows = false
        }
        containerCard = container

        val cardLayoutParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.65).toInt()
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(container, cardLayoutParams)

        // Window Title Header with Close Button
        val headerLayout = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(headerLayout)

        titleText = TextView(context).apply {
            text = file.name
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(24, 16, 80, 12)
        }
        headerLayout.addView(titleText)

        // Floating Close Button (✕)
        closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(20, 12, 20, 12)
            setBackgroundColor(Color.parseColor("#333333"))
            elevation = 10f
            setOnClickListener { dismiss() }
        }
        val closeParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            marginEnd = 16
        }
        headerLayout.addView(closeButton, closeParams)

        // Media Frame
        val mediaFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = false
        }
        container.addView(mediaFrame)

        // TOP CONTROL BAR
        topControlBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#44000000"))
            setPadding(10.toPx(), 2.toPx(), 10.toPx(), 2.toPx())
            visibility = View.GONE
            elevation = 60f
        }
        val topBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            38.toPx()
        ).apply {
            gravity = Gravity.TOP
        }
        root.addView(topControlBar, topBarParams)

        topBackButton = ImageView(context).apply {
            setImageResource(R.drawable.close_icon_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            setOnClickListener { dismiss() }
        }
        topControlBar?.addView(topBackButton)

        topTitleText = TextView(context).apply {
            text = file.name
            setTextColor(Color.WHITE)
            textSize = 13.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(10.toPx(), 0, 10.toPx(), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topControlBar?.addView(topTitleText)

        topAspectButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_aspect_ratio_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            setOnClickListener { cycleAspectRatio() }
        }
        topControlBar?.addView(topAspectButton)

        topThumbnailButton = ImageView(context).apply {
            setImageResource(R.drawable.camera_icon_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            visibility = if (isVideo) View.VISIBLE else View.GONE
            setOnClickListener { captureCurrentFrameAsThumbnail() }
        }
        topControlBar?.addView(topThumbnailButton)

        topSpeedButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_speed_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            visibility = if (isVideo) View.VISIBLE else View.GONE
            setOnClickListener { showSpeedMenu(it) }
        }
        topControlBar?.addView(topSpeedButton)

        topSpeedBadgeText = TextView(context).apply {
            currentSpeed = 1.0f
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 12f
            visibility = if (isVideo) View.VISIBLE else View.GONE
            setPadding(0, 0, 8.toPx(), 0)
            setOnClickListener { showSpeedMenu(topSpeedButton ?: it) }
        }
        topControlBar?.addView(topSpeedBadgeText)

        topMoreButton = ImageView(context).apply {
            setImageResource(R.drawable.more_vertical_icon_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            setOnClickListener { showMoreOptionsMenu(it) }
        }
        topControlBar?.addView(topMoreButton)

        // BOTTOM CONTROL BAR & MEDIA KEYS (ONLY CREATED & VISIBLE FOR VIDEO)
        bottomControlBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#44000000"))
            setPadding(12.toPx(), 4.toPx(), 12.toPx(), 6.toPx())
            visibility = View.GONE
            elevation = 60f
        }
        val bottomBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(bottomControlBar, bottomBarParams)

        // Time Info Row
        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }
        bottomControlBar?.addView(timeRow)

        fullTimeText = TextView(context).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeRow.addView(fullTimeText)

        fullAspectBadge = TextView(context).apply {
            text = "[FIT]"
            setTextColor(Color.parseColor("#DDDDDD"))
            textSize = 10f
        }
        timeRow.addView(fullAspectBadge)

        // Scrubber Seek Bar
        fullSeekBar = SeekBar(context).apply {
            max = 1000
            progress = 0
            setPadding(0, 0, 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && videoDurationMs > 0) {
                        val targetMs = ((progress / 1000f) * videoDurationMs).toInt().coerceIn(0, videoDurationMs)
                        currentSeekPositionMs = targetMs
                        val currentSec = (targetMs / 1000).coerceAtLeast(0)
                        val totalSec = (videoDurationMs / 1000).coerceAtLeast(0)
                        val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
                        val totalStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
                        val speedText = if (currentSpeed > 1.05f) {
                            String.format(java.util.Locale.US, "  |  ⚡ %.1fx Speed", currentSpeed)
                        } else {
                            ""
                        }
                        timeText?.text = "$currentStr / $totalStr$speedText"
                        fullTimeText?.text = "$currentStr / $totalStr$speedText"
                        seekTimelineText?.text = "$currentStr / $totalStr"
                        seekBarProgress?.progress = progress
                        seekTimelineProgressBar?.progress = progress

                        val now = System.currentTimeMillis()
                        if (now - lastSeekTimestampMs > 50L) {
                            lastSeekTimestampMs = now
                            performSeek(targetMs)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isTouchOnSeekBar = true
                    isUserDragging = true
                    mainHandler.removeCallbacks(hideControlsRunnable)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isTouchOnSeekBar = false
                    isUserDragging = false
                    performSeek(currentSeekPositionMs)
                    updateSeekDisplay(currentSeekPositionMs)
                    if (areControlsVisible && !isControlsLocked) {
                        mainHandler.postDelayed(hideControlsRunnable, 4000)
                    }
                }
            })
        }
        bottomControlBar?.addView(fullSeekBar)

        // Media Keys Bar
        val mediaKeysRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 4.toPx(), 0, 2.toPx())
        }
        bottomControlBar?.addView(mediaKeysRow)

        previousVideoButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_skip_previous_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener { playPreviousVideoInFolder() }
        }
        mediaKeysRow.addView(previousVideoButton)

        rewindButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_replay_10_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener { seekRelative(-getSeekStepMs()) }
        }
        mediaKeysRow.addView(rewindButton)

        playPauseButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_pause_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(76.toPx(), 76.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener { togglePlayPause() }
        }
        mediaKeysRow.addView(playPauseButton)

        forwardButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_forward_10_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener { seekRelative(getSeekStepMs()) }
        }
        mediaKeysRow.addView(forwardButton)

        nextVideoButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_skip_next_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener { playNextVideoInFolder() }
        }
        mediaKeysRow.addView(nextVideoButton)

        repeatButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_repeat_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(64.toPx(), 64.toPx()).apply {
                marginStart = 4.toPx()
                marginEnd = 4.toPx()
            }
            setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
            setOnClickListener {
                repeatMode = when (repeatMode) {
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                    RepeatMode.OFF -> RepeatMode.ALL
                }
                updateRepeatButtonUI()
            }
        }
        mediaKeysRow.addView(repeatButton)

        // IMAGE CAROUSEL STRIP AT BOTTOM (Appears on Image Tap)
        imageCarouselScrollView = HorizontalScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(8.toPx(), 8.toPx(), 8.toPx(), 8.toPx())
            visibility = View.GONE
            elevation = 75f
            fitsSystemWindows = false
        }
        val carouselParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            80.toPx()
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(imageCarouselScrollView, carouselParams)

        imageCarouselLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        imageCarouselScrollView?.addView(imageCarouselLayout)

        // Minimalist Speed Horizontal HUD Overlay Card
        dualSliderCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(16, 12, 16, 12)
            visibility = View.GONE
            elevation = 80f
        }
        val dualParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.45).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (context.resources.displayMetrics.density * 54).toInt()
        }
        root.addView(dualSliderCard, dualParams)

        speedHudLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        dualSliderCard?.addView(speedHudLayout)

        speedProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            max = 1000
            progress = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
        }
        speedHudLayout?.addView(speedProgressBar)

        speedHudValueText = TextView(context).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(12, 0, 0, 0)
        }
        speedHudLayout?.addView(speedHudValueText)

        seekHudLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        dualSliderCard?.addView(seekHudLayout)

        seekLabelText = TextView(context).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        seekHudLayout?.addView(seekLabelText)

        seekProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            max = 1000
            progress = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
        }
        seekHudLayout?.addView(seekProgressBar)

        // VERTICAL VOLUME & BRIGHTNESS HUD CARD
        verticalHudCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 16.toPx())
            visibility = View.GONE
            elevation = 90f
        }
        val vertParams = FrameLayout.LayoutParams(
            52.toPx(),
            220.toPx()
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = 36.toPx()
        }
        root.addView(verticalHudCard, vertParams)

        // 1. Top Number Text
        verticalHudValueText = TextView(context).apply {
            text = "0"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 12.toPx())
        }
        verticalHudCard?.addView(verticalHudValueText)

        // 2. Middle Vertical Track Container
        verticalHudTrackContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                4.toPx(),
                0,
                1f
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(Color.parseColor("#555555"))
        }
        verticalHudCard?.addView(verticalHudTrackContainer)

        // Vertical Fill View
        verticalHudFillView = View(context).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        verticalHudTrackContainer?.addView(verticalHudFillView)

        // 3. Bottom Icon View
        verticalHudIconView = ImageView(context).apply {
            setImageResource(R.drawable.ic_volume_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                26.toPx(),
                26.toPx()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 12.toPx()
            }
        }
        verticalHudCard?.addView(verticalHudIconView)

        // COMPACT SINGLE HORIZONTAL TIMELINE HUD
        seekTimelineHudCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(12.toPx(), 4.toPx(), 12.toPx(), 4.toPx())
            visibility = View.GONE
            elevation = 20f
        }
        val timelineParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 4.toPx()
            marginStart = 12.toPx()
            marginEnd = 12.toPx()
        }
        root.addView(seekTimelineHudCard, timelineParams)

        seekTimelineText = TextView(context).apply {
            text = "00:00 / 00:00"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 10.toPx(), 0)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        seekTimelineHudCard?.addView(seekTimelineText)

        seekTimelineProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            max = 1000
            progress = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#66555555"))
            }
        }
        seekTimelineHudCard?.addView(seekTimelineProgressBar)

        seekTimelineSpeedBadge = TextView(context).apply {
            text = "⚡ 1.0x (1F)"
            setTextColor(Color.parseColor("#7C85FC"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(10.toPx(), 0, 0, 0)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            visibility = View.GONE
        }
        seekTimelineHudCard?.addView(seekTimelineSpeedBadge)

        // DOUBLE TAP ANIMATION OVERLAYS (Left & Right)
        val doubleTapCardShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#99111111"))
        }

        leftDoubleTapCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = doubleTapCardShape
            setPadding(18.toPx(), 18.toPx(), 18.toPx(), 18.toPx())
            visibility = View.GONE
            elevation = 95f
        }
        val leftDoubleTapParams = FrameLayout.LayoutParams(
            100.toPx(),
            100.toPx()
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = 48.toPx()
        }
        root.addView(leftDoubleTapCard, leftDoubleTapParams)

        val leftIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_replay_10_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(36.toPx(), 36.toPx())
        }
        leftDoubleTapCard?.addView(leftIcon)

        leftDoubleTapText = TextView(context).apply {
            text = "10s"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        leftDoubleTapCard?.addView(leftDoubleTapText)

        rightDoubleTapCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = doubleTapCardShape
            setPadding(18.toPx(), 18.toPx(), 18.toPx(), 18.toPx())
            visibility = View.GONE
            elevation = 95f
        }
        val rightDoubleTapParams = FrameLayout.LayoutParams(
            100.toPx(),
            100.toPx()
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = 48.toPx()
        }
        root.addView(rightDoubleTapCard, rightDoubleTapParams)

        val rightIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_forward_10_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(36.toPx(), 36.toPx())
        }
        rightDoubleTapCard?.addView(rightIcon)

        rightDoubleTapText = TextView(context).apply {
            text = "10s"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rightDoubleTapCard?.addView(rightDoubleTapText)

        // Loading Spinner
        progressView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        mediaFrame.addView(progressView)

        // Seek Bar Container
        seekContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
            visibility = View.GONE
        }
        container.addView(seekContainer)

        timeText = TextView(context).apply {
            setTextColor(Color.parseColor("#DDDDDD"))
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        seekContainer?.addView(timeText)

        seekBarProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            max = 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
        }
        seekContainer?.addView(seekBarProgress)

        if (isVideo) {
            val video = android.widget.VideoView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
                fitsSystemWindows = false
            }
            mediaFrame.addView(video)
            videoView = video

            video.setOnPreparedListener { mp ->
                mediaPlayerRef = mp
                mp.isLooping = false

                try {
                    mp.setOnSeekCompleteListener { mediaPlayer ->
                        try {
                            if (!isPausedByUser) {
                                mediaPlayer.start()
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                mp.setOnCompletionListener {
                    val currentPos = videoView?.currentPosition ?: 0
                    val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
                    val isNearEnd = (dur > 3000 && currentPos >= dur - 2500) || (dur in 1..3000 && currentPos > 0)
                    if (!isNearEnd) {
                        // Premature completion triggered by a timestamp discontinuity or demuxer packet stall!
                        // Do NOT jump to start or skip video! Try nudging forward slightly to recover stream.
                        try {
                            if (currentPos > 0) {
                                performSeek(currentPos + 500)
                            }
                        } catch (_: Exception) {}
                        return@setOnCompletionListener
                    }
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            performSeek(0)
                            videoView?.start()
                        }
                        RepeatMode.ALL -> {
                            if (!playNextVideoInFolder()) {
                                performSeek(0)
                                videoView?.start()
                            }
                        }
                        RepeatMode.OFF -> {
                            isPausedByUser = true
                            playPauseButton?.setImageResource(R.drawable.ic_play_white_24dp)
                        }
                    }
                }

                progressView?.visibility = View.GONE
                videoDurationMs = mp.duration
                if (videoDurationMs <= 0) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            MediaMetadataRetriever().use { mmr ->
                                mmr.setDataSource(file.path)
                                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                if (!durStr.isNullOrEmpty()) {
                                    val parsed = durStr.toIntOrNull() ?: 0
                                    if (parsed > 0) {
                                        mainHandler.post {
                                            videoDurationMs = parsed
                                            updateSeekDisplay(videoView?.currentPosition ?: 0)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                val savedPos = me.zhanghai.android.files.file.VideoPreviewPositionManager.getPosition(currentFilePathString)
                val initialPos = if (videoDurationMs > 0 && savedPos in 1 until videoDurationMs) savedPos else 0

                dragStartSeekPositionMs = initialPos
                currentSeekPositionMs = initialPos
                if (!isFullScreen) {
                    seekContainer?.visibility = View.VISIBLE
                }

                if (initialPos > 0) {
                    try {
                        performSeek(initialPos)
                    } catch (_: Exception) {}
                }
                updateSeekDisplay(initialPos)
                adjustContainerAspectRatio(mp.videoWidth, mp.videoHeight)
                if (!isPausedByUser) {
                    try {
                        video.start()
                    } catch (_: Exception) {}
                }
                mainHandler.post(updateProgressRunnable)
            }

            video.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    progressView?.visibility = View.GONE
                }
                false
            }

            video.setOnErrorListener { _, what, extra ->
                progressView?.visibility = View.GONE
                android.util.Log.w("QuickPreviewPopup", "MediaPlayer error: what=$what, extra=$extra")
                val pos = currentSeekPositionMs
                if (pos > 0) {
                    try {
                        performSeek(pos + 1000)
                        return@setOnErrorListener true
                    } catch (_: Exception) {}
                }
                true
            }

            try {
                val fileObj = try { file.path.toFile() } catch (_: Exception) { null }
                if (fileObj != null && fileObj.exists() && fileObj.canRead()) {
                    video.setVideoPath(fileObj.absolutePath)
                } else {
                    val contentUri = file.path.fileProviderUri
                    video.setVideoURI(contentUri)
                }
            } catch (e: Exception) {
                progressView?.visibility = View.GONE
            }
        } else if (file.mimeType.isImage) {
            val img = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
                scaleType = ImageView.ScaleType.FIT_CENTER
                fitsSystemWindows = false
            }
            mediaFrame.addView(img)
            imageView = img

            try {
                img.load(file.path to file.attributes) {
                    listener(
                        onSuccess = { _, result ->
                            progressView?.visibility = View.GONE
                            val d = result.drawable
                            adjustContainerAspectRatio(d.intrinsicWidth, d.intrinsicHeight)
                        },
                        onError = { _, _ ->
                            progressView?.visibility = View.GONE
                        }
                    )
                }
            } catch (e: Exception) {
                progressView?.visibility = View.GONE
            }
        }

        popupWindow = PopupWindow(
            root,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            true
        ).apply {
            isTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            showAtLocation(anchorView, Gravity.CENTER, 0, 0)
        }
        setupGestureControls()
    }

    private fun Int.toPx(): Int = (this * context.resources.displayMetrics.density).toInt()

    private fun getAveragePointerX(event: MotionEvent): Float {
        var sum = 0f
        val count = event.pointerCount
        if (count == 0) return event.rawX
        for (i in 0 until count) {
            sum += event.getX(i)
        }
        val offsetX = event.rawX - event.x
        return (sum / count) + offsetX
    }

    private fun getRemainingPointerX(event: MotionEvent): Float {
        val actionIndex = event.actionIndex
        var sum = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i != actionIndex) {
                sum += event.getX(i)
                count++
            }
        }
        if (count == 0) return event.rawX
        val offsetX = event.rawX - event.x
        return (sum / count) + offsetX
    }

    fun onDragDeltaRaw(rawX: Float, pointerCount: Int = 1) {
        if (!isVideo || videoView == null) return
        val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
        if (dur <= 0) return

        mainHandler.removeCallbacks(resetActiveMovingRunnable)
        if (!isActivelyMovingFinger || lastActivePointerCount != pointerCount) {
            dragStartFingerX = rawX
            val pos = videoView?.currentPosition ?: 0
            dragStartSeekPositionMs = if (currentSeekPositionMs > 0) currentSeekPositionMs else pos
            isActivelyMovingFinger = true
            lastActivePointerCount = pointerCount
        }
        isUserDragging = true

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val gestureSpeed = if (pointerCount >= 2) {
            me.zhanghai.android.files.file.VideoPreviewPositionManager.getTwoFingerGestureSeekSpeed()
        } else {
            me.zhanghai.android.files.file.VideoPreviewPositionManager.getGestureSeekSpeed()
        }
        val dxFromSegment = rawX - dragStartFingerX
        val deltaMs = ((dxFromSegment / screenWidth) * dur * gestureSpeed).toInt()
        val targetMs = min(max(0, dragStartSeekPositionMs + deltaMs), dur)

        currentSeekPositionMs = targetMs
        updateSeekDisplay(targetMs, gestureSpeed, pointerCount)

        val now = System.currentTimeMillis()
        if (now - lastSeekTimestampMs > 50L) {
            lastSeekTimestampMs = now
            performSeek(targetMs)
        }

        if (isFullScreen) {
            showSeekHud(targetMs, dur)
        }

        mainHandler.postDelayed(resetActiveMovingRunnable, 150)
    }

    fun onDragDelta(deltaPx: Float) {
        val rootX = rootView?.width?.toFloat()?.div(2f) ?: 500f
        onDragDeltaRaw(rootX + deltaPx)
    }

    fun onTouchUp(wasSeeking: Boolean = false) {
        mainHandler.removeCallbacks(resetActiveMovingRunnable)
        mainHandler.removeCallbacks(hideHudRunnable)
        dualSliderCard?.visibility = View.GONE
        speedHudLayout?.visibility = View.GONE
        seekHudLayout?.visibility = View.GONE
        verticalHudCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE
        seekTimelineSpeedBadge?.visibility = View.GONE

        val didSeek = isUserDragging || isActivelyMovingFinger || wasSeeking
        isActivelyMovingFinger = false
        isUserDragging = false
        isTouchOnSeekBar = false
        resetPlaybackSpeed()

        if (isVideo) {
            if (didSeek) {
                performSeek(currentSeekPositionMs)
            }
            val pos = videoView?.currentPosition ?: currentSeekPositionMs
            if (pos > 0 && !didSeek) {
                currentSeekPositionMs = pos
            }
            dragStartSeekPositionMs = currentSeekPositionMs
            updateSeekDisplay(currentSeekPositionMs)
        }
    }

    private fun updateSeekDisplay(
        positionMs: Int,
        activeGestureSpeed: Float? = null,
        activePointerCount: Int = 1
    ) {
        if (!isVideo) return
        val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
        if (dur <= 0) return
        val currentSec = (positionMs / 1000).coerceAtLeast(0)
        val totalSec = (dur / 1000).coerceAtLeast(0)

        val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
        val totalStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

        val showIndicator = me.zhanghai.android.files.file.VideoPreviewPositionManager.getShowGestureSpeedIndicator()

        val speedText = if (isUserDragging && showIndicator && activeGestureSpeed != null) {
            val fingersLabel = if (activePointerCount >= 2) "2-Fingers" else "1-Finger"
            String.format(java.util.Locale.US, "  |  ⚡ %.1fx Speed (%s)", activeGestureSpeed, fingersLabel)
        } else if (currentSpeed > 1.05f) {
            String.format(java.util.Locale.US, "  |  ⚡ %.1fx Speed", currentSpeed)
        } else {
            ""
        }

        timeText?.text = "$currentStr / $totalStr$speedText"
        fullTimeText?.text = "$currentStr / $totalStr$speedText"

        if (isUserDragging && showIndicator && activeGestureSpeed != null) {
            val isTwoFingers = activePointerCount >= 2
            val badgeColor = if (isTwoFingers) "#4DEEEA" else "#7C85FC"
            val badgeText = String.format(
                java.util.Locale.US,
                "⚡ %.1fx (%s)",
                activeGestureSpeed,
                if (isTwoFingers) "2-Fingers" else "1-Finger"
            )
            seekTimelineSpeedBadge?.text = badgeText
            seekTimelineSpeedBadge?.setTextColor(Color.parseColor(badgeColor))
            seekTimelineSpeedBadge?.visibility = View.VISIBLE
        } else {
            seekTimelineSpeedBadge?.visibility = View.GONE
        }
        seekTimelineText?.text = "$currentStr / $totalStr"

        val progress = ((positionMs.toFloat() / dur) * 1000).toInt().coerceIn(0, 1000)
        seekBarProgress?.progress = progress
        if (!isTouchOnSeekBar) {
            fullSeekBar?.progress = progress
        }
        seekTimelineProgressBar?.progress = progress
        if (videoView?.isPlaying == true) {
            playPauseButton?.setImageResource(R.drawable.ic_pause_white_24dp)
        } else if (isPausedByUser) {
            playPauseButton?.setImageResource(R.drawable.ic_play_white_24dp)
        }
    }

    fun dismiss() {
        resetPlaybackSpeed()
        if (currentFilePathString.isNotEmpty() && isVideo) {
            val pos = videoView?.currentPosition ?: currentSeekPositionMs
            if (pos > 0) {
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setPosition(currentFilePathString, pos)
            }
        }
        context.findActivity()?.window?.let { window ->
            originalStatusBarColor?.let { color ->
                window.statusBarColor = color
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        originalStatusBarColor = null
        isFullScreen = false
        isUserDragging = false
        isActivelyMovingFinger = false
        isUserDragging = false
        isPausedByUser = false
        isControlsLocked = false
        areControlsVisible = true
        gestureState = GestureState.NONE
        singleTapToggleRunnable?.let { mainHandler.removeCallbacks(it) }
        singleTapToggleRunnable = null
        lastTapTimeMs = 0L
        mainHandler.removeCallbacks(updateProgressRunnable)
        mainHandler.removeCallbacks(hideHudRunnable)
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.removeCallbacks(resetActiveMovingRunnable)
        try {
            videoView?.stopPlayback()
        } catch (_: Exception) {}
        videoView = null
        mediaPlayerRef = null
        imageView = null
        dualSliderCard = null
        speedHudLayout = null
        speedProgressBar = null
        speedHudValueText = null
        seekHudLayout = null
        seekLabelText = null
        seekProgressBar = null
        verticalHudCard = null
        verticalHudValueText = null
        verticalHudTrackContainer = null
        verticalHudFillView = null
        verticalHudIconView = null
        seekTimelineHudCard = null
        seekTimelineText = null
        seekTimelineProgressBar = null
        seekTimelineSpeedBadge = null
        topControlBar = null
        topTitleText = null
        topBackButton = null
        topAspectButton = null
        topThumbnailButton = null
        topSpeedButton = null
        topSpeedBadgeText = null
        topMoreButton = null
        bottomControlBar = null
        fullTimeText = null
        fullAspectBadge = null
        fullSeekBar = null
        playPauseButton = null
        rewindButton = null
        forwardButton = null
        previousVideoButton = null
        nextVideoButton = null
        repeatButton = null
        imageCarouselScrollView = null
        imageCarouselLayout = null
        closeButton = null
        containerCard = null
        rootView = null
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true
}
