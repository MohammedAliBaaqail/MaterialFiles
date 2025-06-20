package me.zhanghai.android.files.ui.videothumbnails

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.VideoThumbnail
import me.zhanghai.android.files.databinding.ThumbnailSlideshowViewBinding
import me.zhanghai.android.files.util.dp
import java.io.File

class ThumbnailSlideshowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), LifecycleObserver {

    private val binding: ThumbnailSlideshowViewBinding
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var thumbnails: List<VideoThumbnail> = emptyList()
    var isRunning: Boolean = false
        private set
    
    private var currentRunnable: Runnable? = null

    init {
        binding = ThumbnailSlideshowViewBinding.inflate(LayoutInflater.from(context), this)
        addView(binding.root)
    }

    fun setThumbnails(thumbnails: List<VideoThumbnail>) {
        this.thumbnails = thumbnails.sortedBy { it.displayOrder }
        currentIndex = thumbnails.indexOfFirst { it.isDefault }.takeIf { it != -1 } ?: 0
        updateThumbnail()
        
        // Only start slideshow if we have more than one thumbnail
        if (thumbnails.size > 1) {
            startSlideshow()
        } else {
            stopSlideshow()
        }
    }

    private fun updateThumbnail() {
        if (thumbnails.isEmpty()) {
            binding.thumbnailImageView.setImageResource(R.drawable.ic_video_24dp)
            return
        }

        val thumbnail = thumbnails[currentIndex]
        
        Glide.with(this)
            .load(File(thumbnail.thumbnailPath))
            .diskCacheStrategy(DiskCacheStrategy.NONE) // Don't cache to avoid stale images
            .skipMemoryCache(true) // Don't cache in memory
            .centerCrop()
            .into(binding.thumbnailImageView)
        
        // Schedule next thumbnail change
        scheduleNextThumbnail(thumbnail.displayIntervalMs)
    }

    private fun scheduleNextThumbnail(delayMs: Long) {
        currentRunnable?.let { handler.removeCallbacks(it) }
        
        if (thumbnails.size <= 1) return
        
        val runnable = Runnable {
            currentIndex = (currentIndex + 1) % thumbnails.size
            updateThumbnail()
        }
        
        currentRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    fun startSlideshow() {
        if (isRunning || thumbnails.size <= 1) return
        
        isRunning = true
        currentRunnable?.let { handler.removeCallbacks(it) }
        updateThumbnail()
    }

    fun stopSlideshow() {
        isRunning = false
        currentRunnable?.let { handler.removeCallbacks(it) }
        currentRunnable = null
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        if (thumbnails.size > 1) {
            startSlideshow()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        stopSlideshow()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopSlideshow()
    }
}
