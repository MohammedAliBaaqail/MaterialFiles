/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import coil.load
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.isVideo
import kotlin.math.max
import kotlin.math.min

class QuickPreviewPopup(private val context: Context) {
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
    private var originalFileName: String = ""

    private var currentFilePathString: String = ""
    private var isVideo = false
    private var videoDurationMs = 0
    private var dragStartSeekPositionMs = 0
    private var currentSeekPositionMs = 0
    private var isUserDragging = false
    private var currentAspectRatio = 1.0f
    private var currentSpeed = 1.0f

    var isLocked = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            videoView?.let { v ->
                if (v.isPlaying && !isUserDragging) {
                    currentSeekPositionMs = v.currentPosition
                    updateSeekDisplay(currentSeekPositionMs)
                    if (currentFilePathString.isNotEmpty() && currentSeekPositionMs > 0) {
                        me.zhanghai.android.files.file.VideoPreviewPositionManager.setPosition(
                            currentFilePathString, currentSeekPositionMs
                        )
                    }
                }
            }
            if (popupWindow?.isShowing == true && isVideo) {
                mainHandler.postDelayed(this, 200)
            }
        }
    }

    private fun getExtraHeightPx(): Int {
        val density = context.resources.displayMetrics.density
        // Title header height (~32dp) + Seek container height (~40dp) = 72dp
        return (72 * density).toInt()
    }

    private fun adjustContainerAspectRatio(contentWidth: Int, contentHeight: Int) {
        if (contentWidth <= 0 || contentHeight <= 0) return
        currentAspectRatio = contentWidth.toFloat() / contentHeight.toFloat()
        val displayMetrics = context.resources.displayMetrics
        val extraHeight = getExtraHeightPx()

        val maxW = (displayMetrics.widthPixels * 0.92).toInt()
        val maxH = (displayMetrics.heightPixels * 0.80).toInt()

        // Fit video width edge-to-edge without left/right empty space:
        // H_vid = W_vid / ratio
        // H_card = H_vid + extraHeight
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

    fun updatePlaybackSpeed(dyPx: Float) {
        if (!isVideo || mediaPlayerRef == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val targetSpeed = if (dyPx > 30f) {
            val factor = 1.0f + ((dyPx - 30f) / 100f)
            min(factor, 4.0f)
        } else {
            1.0f
        }

        if (Math.abs(targetSpeed - currentSpeed) > 0.05f) {
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
            updateSeekDisplay(currentSeekPositionMs)
        }
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

    fun lockPreview() {
        if (isLocked) return
        isLocked = true
        popupWindow?.update()
        titleText?.text = "🔒 $originalFileName (Tap to close)"
        titleText?.setTextColor(Color.parseColor("#FFD700"))

        rootView?.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var hasMoved = false
            private var isCornerResize = false
            private var initialCardWidth = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        hasMoved = false
                        isCornerResize = false
                        initialCardWidth = containerCard?.width ?: 0

                        containerCard?.let { card ->
                            val cardLoc = IntArray(2)
                            card.getLocationOnScreen(cardLoc)
                            val cardRight = cardLoc[0] + card.width
                            val cardBottom = cardLoc[1] + card.height
                            // If touch starts within 120px of bottom-right corner
                            if (downX >= cardRight - 120 && downY >= cardBottom - 120) {
                                isCornerResize = true
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                            hasMoved = true
                            if (isCornerResize) {
                                val displayMetrics = context.resources.displayMetrics
                                val extraHeight = getExtraHeightPx()
                                val newW = (initialCardWidth + dx.toInt()).coerceIn(
                                    (displayMetrics.widthPixels * 0.35).toInt(),
                                    (displayMetrics.widthPixels * 0.95).toInt()
                                )
                                val newH = (newW / currentAspectRatio.coerceAtLeast(0.2f)).toInt() + extraHeight
                                containerCard?.layoutParams = FrameLayout.LayoutParams(newW, newH).apply {
                                    gravity = Gravity.CENTER
                                }
                            } else {
                                if (!isUserDragging) {
                                    startDrag()
                                }
                                if (dy > 30f) {
                                    updatePlaybackSpeed(dy)
                                } else {
                                    resetPlaybackSpeed()
                                    onDragDelta(dx)
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        resetPlaybackSpeed()
                        onTouchUp()
                        if (!hasMoved) {
                            dismiss()
                        }
                    }
                }
                return true
            }
        })
    }

    fun startDrag() {
        if (isUserDragging) return
        isUserDragging = true
        dragStartSeekPositionMs = if (videoView != null && videoView!!.isPlaying && videoView!!.currentPosition > 0) {
            videoView!!.currentPosition
        } else {
            currentSeekPositionMs
        }
    }

    fun show(anchorView: View, file: FileItem) {
        dismiss()

        isLocked = false
        isUserDragging = false
        currentSpeed = 1.0f
        originalFileName = file.name
        currentFilePathString = file.path.toString()

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#B0000000"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Shield underlying RecyclerView by absorbing all touch events
            setOnTouchListener { _, _ -> true }
        }
        rootView = root

        // Main preview container card (0 outer padding so media spans edge-to-edge)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(0, 16, 0, 16)
            elevation = 16f
        }
        containerCard = container

        val cardLayoutParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.65).toInt()
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(container, cardLayoutParams)

        // Title Header (Padding inside header text)
        titleText = TextView(context).apply {
            text = file.name
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(24, 0, 24, 12)
        }
        container.addView(titleText)

        // Media Frame (0 padding, full width)
        val mediaFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        container.addView(mediaFrame)

        // Loading Spinner
        progressView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        mediaFrame.addView(progressView)

        // Seek Bar Container (for Videos)
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
        }
        seekContainer?.addView(seekBarProgress)

        isVideo = file.mimeType.isVideo

        if (isVideo) {
            val video = android.widget.VideoView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            }
            mediaFrame.addView(video)
            videoView = video

            video.setOnPreparedListener { mp ->
                mediaPlayerRef = mp
                progressView?.visibility = View.GONE
                videoDurationMs = mp.duration
                val savedPos = me.zhanghai.android.files.file.VideoPreviewPositionManager.getPosition(currentFilePathString)
                val initialPos = if (savedPos in 1 until mp.duration) savedPos else 0

                dragStartSeekPositionMs = initialPos
                currentSeekPositionMs = initialPos
                seekContainer?.visibility = View.VISIBLE

                if (initialPos > 0) {
                    video.seekTo(initialPos)
                }
                updateSeekDisplay(initialPos)
                adjustContainerAspectRatio(mp.videoWidth, mp.videoHeight)
                mp.isLooping = true
                video.start()
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
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
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
    }

    fun onDragDelta(deltaPx: Float) {
        if (!isVideo || videoDurationMs <= 0 || videoView == null) return
        if (!isUserDragging) {
            startDrag()
        }

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val deltaMs = ((deltaPx / screenWidth) * videoDurationMs).toInt()
        val targetMs = min(max(0, dragStartSeekPositionMs + deltaMs), videoDurationMs)

        currentSeekPositionMs = targetMs
        videoView?.seekTo(targetMs)
        updateSeekDisplay(targetMs)
    }

    fun onTouchUp() {
        isUserDragging = false
        resetPlaybackSpeed()
        if (videoView != null && videoView!!.isPlaying && videoView!!.currentPosition > 0) {
            currentSeekPositionMs = videoView!!.currentPosition
        }
        dragStartSeekPositionMs = currentSeekPositionMs
    }

    private fun updateSeekDisplay(positionMs: Int) {
        if (videoDurationMs <= 0) return
        val currentSec = positionMs / 1000
        val totalSec = videoDurationMs / 1000

        val currentStr = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
        val totalStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

        val speedText = if (currentSpeed > 1.05f) {
            String.format(java.util.Locale.US, "  |  ⚡ %.1fx Speed", currentSpeed)
        } else {
            ""
        }

        timeText?.text = "$currentStr / $totalStr$speedText"
        val progress = ((positionMs.toFloat() / videoDurationMs) * 1000).toInt()
        seekBarProgress?.progress = progress
    }

    fun dismiss() {
        resetPlaybackSpeed()
        if (currentFilePathString.isNotEmpty() && isVideo) {
            val pos = videoView?.currentPosition ?: currentSeekPositionMs
            if (pos > 0) {
                me.zhanghai.android.files.file.VideoPreviewPositionManager.setPosition(currentFilePathString, pos)
            }
        }
        isLocked = false
        isUserDragging = false
        mainHandler.removeCallbacks(updateProgressRunnable)
        try {
            videoView?.stopPlayback()
        } catch (_: Exception) {}
        videoView = null
        mediaPlayerRef = null
        imageView = null
        containerCard = null
        rootView = null
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true
}
