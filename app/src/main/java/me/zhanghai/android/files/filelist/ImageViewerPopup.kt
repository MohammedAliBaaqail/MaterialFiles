/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.provider.common.newInputStream
import java.util.concurrent.Executors

class ImageViewerPopup(private val context: Context) {
    private var popupWindow: PopupWindow? = null
    private var rootView: FrameLayout? = null
    private var containerCard: LinearLayout? = null
    private var imageView: ImageView? = null
    private var progressView: ProgressBar? = null
    private var titleText: TextView? = null
    private var closeButton: TextView? = null
    private var originalFileName: String = ""
    private var originalStatusBarColor: Int? = null

    private var topControlBar: LinearLayout? = null
    private var topTitleText: TextView? = null
    private var topBackButton: ImageView? = null
    private var topRotateButton: ImageView? = null

    private var imageCarouselScrollView: HorizontalScrollView? = null
    private var imageCarouselLayout: LinearLayout? = null

    private var currentFilePathString: String = ""
    private var currentPlaylist: List<FileItem> = emptyList()
    private var imageScaleFactor = 1.0f
    private var imageRotationDegree = 0f
    private var currentAspectRatio = 1.0f
    private var areControlsVisible = true

    // Single-threaded serial dispatcher for VeraCrypt / FS decoding to prevent cluster contention & lag
    private val imageDecodeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var imageLoadJob: Job? = null
    private var carouselLoadJob: Job? = null

    var isFullScreen = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

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

    private fun getExtraHeightPx(): Int {
        val density = context.resources.displayMetrics.density
        return (72 * density).toInt()
    }

    private fun adjustContainerAspectRatio(contentWidth: Int, contentHeight: Int) {
        if (contentWidth <= 0 || contentHeight <= 0) return
        currentAspectRatio = contentWidth.toFloat() / contentHeight.toFloat()

        if (isFullScreen) {
            val (screenW, screenH) = getPhysicalScreenSize()
            var targetW = screenW
            var targetH = (targetW / currentAspectRatio).toInt()
            if (targetH > screenH) {
                targetH = screenH
                targetW = (targetH * currentAspectRatio).toInt()
            }
            imageView?.layoutParams = FrameLayout.LayoutParams(targetW, targetH).apply {
                gravity = Gravity.CENTER
            }
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

    private fun resetImageZoom() {
        imageScaleFactor = 1.0f
        imageRotationDegree = 0f
        imageView?.scaleX = 1.0f
        imageView?.scaleY = 1.0f
        imageView?.rotation = 0f
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

    private fun playImageFile(file: FileItem) {
        currentFilePathString = file.path.toString()
        originalFileName = file.name
        topTitleText?.text = file.name
        titleText?.text = file.name

        resetImageZoom()
        progressView?.visibility = View.VISIBLE
        imageLoadJob?.cancel()

        val (screenW, screenH) = getPhysicalScreenSize()

        imageLoadJob = CoroutineScope(imageDecodeDispatcher).launch {
            val bitmap = try {
                file.path.newInputStream().use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    val srcW = options.outWidth
                    val srcH = options.outHeight

                    var sampleSize = 1
                    if (srcW > screenW || srcH > screenH) {
                        val halfW = srcW / 2
                        val halfH = srcH / 2
                        while ((halfW / sampleSize) >= screenW && (halfH / sampleSize) >= screenH) {
                            sampleSize *= 2
                        }
                    }

                    file.path.newInputStream().use { secondStream ->
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeStream(secondStream, null, decodeOpts)
                    }
                }
            } catch (_: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                progressView?.visibility = View.GONE
                imageView?.setImageDrawable(null)
                if (bitmap != null) {
                    imageView?.setImageBitmap(bitmap)
                    adjustContainerAspectRatio(bitmap.width, bitmap.height)
                } else {
                    imageView?.load(file.path to file.attributes) {
                        listener(
                            onSuccess = { _, result ->
                                val d = result.drawable
                                adjustContainerAspectRatio(d.intrinsicWidth, d.intrinsicHeight)
                            }
                        )
                    }
                }
                updateImageCarouselSelection()
            }
        }
    }

    private fun populateImageCarousel() {
        imageCarouselLayout?.removeAllViews()
        carouselLoadJob?.cancel()
        val imageFiles = currentPlaylist.filter { it.mimeType.isImage }
        if (imageFiles.isEmpty()) return

        val viewMap = mutableMapOf<String, ImageView>()

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
                setOnClickListener {
                    playImageFile(imgFile)
                }
            }
            imageCarouselLayout?.addView(thumbView)
            viewMap[imgFile.path.toString()] = thumbView
        }

        carouselLoadJob = CoroutineScope(imageDecodeDispatcher).launch {
            for (imgFile in imageFiles) {
                if (!isActive) break
                val thumbBitmap = try {
                    imgFile.path.newInputStream().use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 8
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    val targetView = viewMap[imgFile.path.toString()]
                    if (targetView != null && thumbBitmap != null) {
                        targetView.setImageBitmap(thumbBitmap)
                    }
                }
            }
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
        closeButton?.visibility = View.GONE

        topControlBar?.visibility = View.VISIBLE
        topTitleText?.text = originalFileName

        val currentFileItem = currentPlaylist.firstOrNull { it.path.toString() == currentFilePathString }
        if (currentFileItem != null) {
            playImageFile(currentFileItem)
        }
    }

    fun show(anchorView: View, file: FileItem, playlist: List<FileItem> = emptyList()) {
        dismiss()

        isFullScreen = false
        areControlsVisible = true
        originalFileName = file.name
        currentFilePathString = file.path.toString()
        currentPlaylist = playlist

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
        }
        rootView = root

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

        topRotateButton = ImageView(context).apply {
            setImageResource(R.drawable.reset_icon_white_24dp)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(34.toPx(), 34.toPx())
            setPadding(7.toPx(), 7.toPx(), 7.toPx(), 7.toPx())
            setOnClickListener {
                imageRotationDegree = (imageRotationDegree + 90f) % 360f
                imageView?.rotation = imageRotationDegree
            }
        }
        topControlBar?.addView(topRotateButton)

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

        progressView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        mediaFrame.addView(progressView)

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

        playImageFile(file)

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

    private fun setupGestureControls() {
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (imageView != null) {
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

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                scaleDetector.onTouchEvent(event)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        touchDownTimeMs = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dt = System.currentTimeMillis() - touchDownTimeMs
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val absDx = Math.abs(dx)
                        val absDy = Math.abs(dy)

                        if (absDx > 80 && absDx > absDy && imageScaleFactor <= 1.05f) {
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
                }
                return true
            }
        })
    }

    fun dismiss() {
        imageLoadJob?.cancel()
        carouselLoadJob?.cancel()
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
        imageView = null
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
