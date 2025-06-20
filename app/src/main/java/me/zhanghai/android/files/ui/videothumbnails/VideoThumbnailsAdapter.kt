package me.zhanghai.android.files.ui.videothumbnails

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.VideoThumbnail
import me.zhanghai.android.files.util.dp
import me.zhanghai.android.files.util.formatDuration
import java.io.File

class VideoThumbnailsAdapter(
    private val viewModel: VideoThumbnailsViewModel
) : ListAdapter<VideoThumbnail, VideoThumbnailsAdapter.ThumbnailViewHolder>(ThumbnailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_thumbnail, parent, false)
        return ThumbnailViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImage: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val intervalText: TextView = itemView.findViewById(R.id.intervalText)
        private val defaultChip: com.google.android.material.chip.Chip = itemView.findViewById(R.id.defaultChip)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)

        fun bind(thumbnail: VideoThumbnail) {
            // Load thumbnail image
            Glide.with(itemView)
                .load(File(thumbnail.thumbnailPath))
                .apply(
                    RequestOptions()
                        .centerCrop()
                        .transform(RoundedCorners(4.dp))
                )
                .placeholder(R.drawable.ic_video_24dp)
                .into(thumbnailImage)

            // Set timestamp
            timestampText.text = viewModel.formatTimestamp(thumbnail.timestampMs)
            
            // Set interval text
            val intervalSeconds = thumbnail.displayIntervalMs / 1000f
            intervalText.text = itemView.context.getString(R.string.interval_format, intervalSeconds)

            // Set default state
            defaultChip.isChecked = thumbnail.isDefault
            defaultChip.visibility = if (thumbnail.isDefault) View.VISIBLE else View.INVISIBLE

            // Set click listeners
            itemView.setOnClickListener {
                // Toggle default state
                viewModel.setDefaultThumbnail(thumbnail)
            }

            defaultChip.setOnClickListener {
                viewModel.setDefaultThumbnail(thumbnail)
            }

            // Set up menu
            menuButton.setOnClickListener { view ->
                showMenu(view, thumbnail)
            }

            // Enable drag and drop for reordering
            itemView.setOnLongClickListener {
                // Start drag and drop
                true
            }
        }

        private fun showMenu(anchor: View, thumbnail: VideoThumbnail) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menuInflater.inflate(R.menu.video_thumbnail_item, popup.menu)
            
            popup.menu.findItem(R.id.action_set_default).isVisible = !thumbnail.isDefault
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_set_interval -> {
                        viewModel.showIntervalDialog(thumbnail)
                        true
                    }
                    R.id.action_set_default -> {
                        viewModel.setDefaultThumbnail(thumbnail)
                        true
                    }
                    R.id.action_delete -> {
                        viewModel.showDeleteConfirmDialog(thumbnail)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private class ThumbnailDiffCallback : DiffUtil.ItemCallback<VideoThumbnail>() {
        override fun areItemsTheSame(oldItem: VideoThumbnail, newItem: VideoThumbnail): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoThumbnail, newItem: VideoThumbnail): Boolean {
            return oldItem == newItem
        }
    }
}
