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
    private var topSpeedButton: ImageView? = null
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

    private var currentFilePathString: String = ""
    private var currentPlaylist: List<FileItem> = emptyList()
    private var repeatMode: RepeatMode = RepeatMode.ALL
    private var isVideo = false
    private var videoDurationMs = 0
    private var dragStartFingerX = 0f
    private var dragStartSeekPositionMs = 0
    private var currentSeekPositionMs = 0
    private var isUserDragging = false
    private var isActivelyMovingFinger = false
    private var isPausedByUser = false
    private var currentAspectRatio = 1.0f
    private var currentSpeed = 1.0f
    private var gestureState = GestureState.NONE
    private var lastSeekTimestampMs = 0L
    private var aspectRatioMode = AspectRatioMode.FIT
    private var isControlsLocked = false
    private var areControlsVisible = true
    private var isAudioMuted = false
    private var videoRotationDegree = 0f

    var isFullScreen = false
        private set

    // Legacy property alias for compatibility
    val isLocked: Boolean
        get() = isFullScreen

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
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            videoView?.let { v ->
                if (!isActivelyMovingFinger) {
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

    private fun showSpeedHud(speed: Float) {
        if (!isFullScreen || !isVideo) return
        mainHandler.removeCallbacks(hideHudRunnable)
        verticalHudCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE
        dualSliderCard?.visibility = View.VISIBLE
        speedHudLayout?.visibility = View.VISIBLE
        seekHudLayout?.visibility = View.GONE

        val progressVal = ((speed - 1.0f) / 3.0f * 300).toInt().coerceIn(0, 300)
        speedProgressBar?.progress = progressVal
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
        topSpeedButton?.visibility = if (isVideo) View.VISIBLE else View.GONE

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

    private fun seekRelative(offsetMs: Int) {
        videoView?.let { v ->
            val dur = if (videoDurationMs > 0) videoDurationMs else v.duration
            val targetMs = (v.currentPosition + offsetMs).coerceIn(0, dur)
            v.seekTo(targetMs)
            currentSeekPositionMs = targetMs
            updateSeekDisplay(targetMs)
        }
    }

    private fun playFile(file: FileItem) {
        currentFilePathString = file.path.toString()
        originalFileName = file.name
        topTitleText?.text = file.name
        titleText?.text = file.name

        try {
            val contentUri = file.path.fileProviderUri
            progressView?.visibility = View.VISIBLE
            videoView?.setVideoURI(contentUri)
        } catch (e: Exception) {
            e.printStackTrace()
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
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
        for (s in speeds) {
            popup.menu.add("${s}x")
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
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Unmute Audio", "Mute Audio" -> {
                    isAudioMuted = !isAudioMuted
                    mediaPlayerRef?.setVolume(if (isAudioMuted) 0f else 1f, if (isAudioMuted) 0f else 1f)
                }
                "Rotate 90°" -> {
                    videoRotationDegree = (videoRotationDegree + 90f) % 360f
                    videoView?.rotation = videoRotationDegree
                    imageView?.rotation = videoRotationDegree
                }
                "Repeat Mode" -> {
                    repeatMode = when (repeatMode) {
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                        RepeatMode.OFF -> RepeatMode.ALL
                    }
                    updateRepeatButtonUI()
                }
            }
            true
        }
        popup.show()
    }

    fun applySavedSpeed() {
        if (!isVideo || mediaPlayerRef == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val savedSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
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
        val targetSpeed = (savedSpeed + (dxPx / 150f)).coerceIn(1.0f, 4.0f)

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
                            mainHandler.postDelayed(longPressSpeedRunnable, 180)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
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
                            val targetSpeed = (initialHoldSpeed + (dx / 150f)).coerceIn(1.0f, 4.0f)
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
                            onDragDeltaRaw(event.rawX)
                            return true
                        }

                        // INITIAL GESTURE SELECTION
                        if (gestureState == GestureState.NONE && (absDx > 20 || absDy > 20)) {
                            val isPressAndHold = holdDurationMs >= 180L

                            if (dy < -100f && !isFullScreen) {
                                openFullScreen()
                            } else if (isFullScreen && isPressAndHold && isVideo) {
                                gestureState = GestureState.SPEED_LOCK
                                initialHoldSpeed = me.zhanghai.android.files.file.VideoPreviewPositionManager.getLastSpeed()
                                applySavedSpeed()
                            } else if (absDx > absDy && isVideo) {
                                gestureState = GestureState.SEEKING
                                startDrag()
                                onDragDeltaRaw(event.rawX)
                            } else if (!isPressAndHold) {
                                // FAST SWIPE ONLY: Adjust Volume or Brightness
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

                        val dt = System.currentTimeMillis() - touchDownTimeMs
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

                        // SINGLE TAP TO TOGGLE VIDEO CONTROLS
                        if (isVideo && isFullScreen && dt < 200 && absDx < 15 && absDy < 15 && gestureState == GestureState.NONE) {
                            setControlsVisible(!areControlsVisible)
                        }

                        resetPlaybackSpeed()
                        onTouchUp()
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

        topSpeedButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_speed_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            visibility = if (isVideo) View.VISIBLE else View.GONE
            setOnClickListener { showSpeedMenu(it) }
        }
        topControlBar?.addView(topSpeedButton)

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
                        val targetMs = ((progress / 1000f) * videoDurationMs).toInt()
                        currentSeekPositionMs = targetMs
                        updateSeekDisplay(targetMs)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserDragging = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserDragging = false
                    videoView?.seekTo(currentSeekPositionMs)
                    if (!isPausedByUser) {
                        videoView?.start()
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
            setOnClickListener { seekRelative(-10000) }
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
            setOnClickListener { seekRelative(10000) }
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            max = 300
            progress = 50
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
        }
        speedHudLayout?.addView(speedProgressBar)

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
                    when (repeatMode) {
                        RepeatMode.ONE -> {
                            videoView?.seekTo(0)
                            videoView?.start()
                        }
                        RepeatMode.ALL -> {
                            if (!playNextVideoInFolder()) {
                                videoView?.seekTo(0)
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
                val savedPos = me.zhanghai.android.files.file.VideoPreviewPositionManager.getPosition(currentFilePathString)
                val initialPos = if (savedPos in 1 until mp.duration) savedPos else 0

                dragStartSeekPositionMs = initialPos
                currentSeekPositionMs = initialPos
                if (!isFullScreen) {
                    seekContainer?.visibility = View.VISIBLE
                }

                if (initialPos > 0) {
                    video.seekTo(initialPos)
                }
                updateSeekDisplay(initialPos)
                adjustContainerAspectRatio(mp.videoWidth, mp.videoHeight)
                if (!isPausedByUser) {
                    video.start()
                }
                mainHandler.post(updateProgressRunnable)
            }

            video.setOnErrorListener { _, _, _ ->
                progressView?.visibility = View.GONE
                true
            }

            try {
                val contentUri = file.path.fileProviderUri
                video.setVideoURI(contentUri)
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

    fun onDragDeltaRaw(rawX: Float) {
        if (!isVideo || videoView == null) return
        val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
        if (dur <= 0) return

        mainHandler.removeCallbacks(resetActiveMovingRunnable)
        if (!isActivelyMovingFinger) {
            dragStartFingerX = rawX
            val pos = videoView?.currentPosition ?: 0
            dragStartSeekPositionMs = if (pos > 0) pos else currentSeekPositionMs
            isActivelyMovingFinger = true
        }
        isUserDragging = true

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val dxFromSegment = rawX - dragStartFingerX
        val deltaMs = ((dxFromSegment / screenWidth) * dur).toInt()
        val targetMs = min(max(0, dragStartSeekPositionMs + deltaMs), dur)

        currentSeekPositionMs = targetMs
        updateSeekDisplay(targetMs)

        val now = System.currentTimeMillis()
        if (now - lastSeekTimestampMs > 80L) {
            lastSeekTimestampMs = now
            try {
                videoView?.seekTo(targetMs)
                if (!isPausedByUser) {
                    videoView?.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun onTouchUp() {
        mainHandler.removeCallbacks(resetActiveMovingRunnable)
        mainHandler.removeCallbacks(hideHudRunnable)
        dualSliderCard?.visibility = View.GONE
        speedHudLayout?.visibility = View.GONE
        seekHudLayout?.visibility = View.GONE
        verticalHudCard?.visibility = View.GONE
        seekTimelineHudCard?.visibility = View.GONE

        isActivelyMovingFinger = false
        resetPlaybackSpeed()

        if (isUserDragging && gestureState == GestureState.SEEKING && isVideo) {
            try {
                if (videoView != null) {
                    videoView?.seekTo(currentSeekPositionMs)
                    if (!isPausedByUser) {
                        videoView?.start()
                    }
                }
            } catch (_: Exception) {}
        }
        isUserDragging = false

        if (isVideo) {
            val pos = videoView?.currentPosition ?: currentSeekPositionMs
            currentSeekPositionMs = if (pos > 0) pos else currentSeekPositionMs
            dragStartSeekPositionMs = currentSeekPositionMs
            updateSeekDisplay(currentSeekPositionMs)
        }
    }

    private fun updateSeekDisplay(positionMs: Int) {
        if (!isVideo) return
        val dur = if (videoDurationMs > 0) videoDurationMs else (videoView?.duration ?: 0)
        if (dur <= 0) return
        val currentSec = (positionMs / 1000).coerceAtLeast(0)
        val totalSec = (dur / 1000).coerceAtLeast(0)

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

        val progress = ((positionMs.toFloat() / dur) * 1000).toInt()
        seekBarProgress?.progress = progress
        fullSeekBar?.progress = progress
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
        isPausedByUser = false
        isControlsLocked = false
        areControlsVisible = true
        gestureState = GestureState.NONE
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
        topControlBar = null
        topTitleText = null
        topBackButton = null
        topAspectButton = null
        topSpeedButton = null
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
