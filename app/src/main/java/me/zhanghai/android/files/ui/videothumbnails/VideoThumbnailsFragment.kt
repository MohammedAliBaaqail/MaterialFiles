package me.zhanghai.android.files.ui.videothumbnails

import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.databinding.FragmentVideoThumbnailsBinding
import me.zhanghai.android.files.databinding.DialogThumbnailIntervalBinding
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.VideoThumbnail
import me.zhanghai.android.files.util.*
import java.io.File
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

class VideoThumbnailsFragment : Fragment() {
    private var _binding: FragmentVideoThumbnailsBinding? = null
    private val binding get() = _binding!!

    private val args: VideoThumbnailsFragmentArgs by navArgs()
    
    private val viewModel: VideoThumbnailsViewModel by viewModels {
        VideoThumbnailsViewModel.Factory(
            requireContext().applicationContext as Application,
            args.videoPath
        )
    }
    
    private val lifecycleScope = viewLifecycleOwner.lifecycleScope
    
    private val adapter by lazy { VideoThumbnailsAdapter(viewModel) }
    private var intervalDialog: Dialog? = null

    private val pickThumbnail = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onThumbnailPicked(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentVideoThumbnailsBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        viewModel.setVideoPath(args.videoPath)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        binding.thumbnailsRecyclerView.apply {
            adapter = this@VideoThumbnailsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }

        // Add swipe to delete
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                
                val items = adapter.currentList.toMutableList()
                val item = items.removeAt(fromPos)
                items.add(toPos, item)
                
                // Update display order
                items.forEachIndexed { index, thumbnail ->
                    if (thumbnail.displayOrder != index) {
                        adapter.notifyItemChanged(index)
                    }
                }
                
                viewModel.updateThumbnailsOrder(items)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val thumbnail = adapter.currentList[position]
                
                // Show delete confirmation
                viewModel.showDeleteConfirmDialog(thumbnail)
                
                // Reset the view
                adapter.notifyItemChanged(position)
            }
        }).attachToRecyclerView(binding.thumbnailsRecyclerView)
    }

    private fun setupClickListeners() {
        binding.addThumbnailFab.setOnClickListener {
            // Open video to pick a frame
            openVideoForThumbnailSelection()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.thumbnails.observe(viewLifecycleOwner) { thumbnails ->
                adapter.submitList(thumbnails)
                binding.emptyView.isVisible = thumbnails.isEmpty()
                binding.thumbnailsRecyclerView.isVisible = thumbnails.isNotEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.isVisible = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
                message?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    viewModel.clearErrorMessage()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showDeleteConfirmDialog.observe(viewLifecycleOwner) { thumbnail ->
                thumbnail?.let { showDeleteConfirmation(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.showIntervalDialog.observe(viewLifecycleOwner) { thumbnail ->
                thumbnail?.let { showIntervalDialog(it) }
            }
        }
    }

    private fun openVideoForThumbnailSelection() {
        val videoFile = File(args.videoPath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(videoFile), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("pick_thumbnail", true)
            putExtra("return-data", false)
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(videoFile))
        }
        
        // Verify that the intent will resolve to an activity
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to a simple toast if no video player is available
            Toast.makeText(
                requireContext(), 
                "No video player app found", 
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun onThumbnailPicked(thumbnailUri: Uri) {
        // Get the timestamp from the intent
        val timestampMs = activity?.intent?.getLongExtra("thumbnail_timestamp", 0L) ?: 0L
        viewModel.addThumbnail(thumbnailUri, timestampMs)
    }

    private fun showDeleteConfirmation(thumbnail: VideoThumbnail) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_thumbnail_title)
            .setMessage(R.string.delete_thumbnail_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteThumbnail(thumbnail)
                viewModel.dismissDeleteConfirmDialog()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                viewModel.dismissDeleteConfirmDialog()
            }
            .setOnDismissListener {
                viewModel.dismissDeleteConfirmDialog()
            }
            .show()
    }

    private fun showIntervalDialog(thumbnail: VideoThumbnail) {
        val binding = DialogThumbnailIntervalBinding.inflate(layoutInflater)
        
        // Set current interval (convert ms to seconds)
        val currentInterval = thumbnail.displayIntervalMs / 1000f
        binding.intervalSlider.value = currentInterval
        binding.intervalEditText.setText(String.format("%.1f", currentInterval))
        
        // Update slider when text changes
        binding.intervalEditText.doAfterTextChanged { text ->
            val value = text.toString().toFloatOrNull() ?: return@doAfterTextChanged
            if (value in 0.5f..10f) {
                binding.intervalSlider.value = value
            }
        }
        
        // Update text when slider changes
        binding.intervalSlider.addOnChangeListener { _, value, _ ->
            binding.intervalEditText.setText(String.format("%.1f", value))
        }
        
        intervalDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_display_interval)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intervalSeconds = binding.intervalSlider.value
                val intervalMs = (intervalSeconds * 1000).toLong()
                viewModel.updateThumbnailInterval(thumbnail, intervalMs)
                viewModel.dismissIntervalDialog()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.dismissIntervalDialog()
            }
            .setOnDismissListener {
                viewModel.dismissIntervalDialog()
                intervalDialog = null
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        intervalDialog?.dismiss()
        _binding = null
    }
}
