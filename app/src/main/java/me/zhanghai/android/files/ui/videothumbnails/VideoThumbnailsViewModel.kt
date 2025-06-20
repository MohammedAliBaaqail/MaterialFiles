package me.zhanghai.android.files.ui.videothumbnails

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.database.AppDatabase
import me.zhanghai.android.files.database.VideoThumbnail
import me.zhanghai.android.files.repository.VideoThumbnailRepository
import me.zhanghai.android.files.util.*
import java.io.File

class VideoThumbnailsViewModel(
    application: Application,
    private val videoPath: String
) : AndroidViewModel(application) {
    private val repository = VideoThumbnailRepository(
        AppDatabase.getDatabase(application).videoThumbnailDao(),
        application
    )

    private val _videoPath = MutableStateFlow<String?>(videoPath)
    private val _thumbnails = MutableLiveData<List<VideoThumbnail>>()
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableLiveData<String?>()
    private val _showAddDialog = MutableLiveData<Boolean>()
    private val _showDeleteConfirmDialog = MutableLiveData<VideoThumbnail?>()
    private val _showIntervalDialog = MutableLiveData<VideoThumbnail?>()

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val application: Application,
        private val videoPath: String
    ) : ViewModelProvider.AndroidViewModelFactory(application) {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoThumbnailsViewModel::class.java)) {
                return VideoThumbnailsViewModel(application, videoPath) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    val thumbnails: LiveData<List<VideoThumbnail>> = _thumbnails
    val isLoading: StateFlow<Boolean> = _isLoading
    val errorMessage: LiveData<String?> = _errorMessage
    val showAddDialog: LiveData<Boolean> = _showAddDialog
    val showDeleteConfirmDialog: LiveData<VideoThumbnail?> = _showDeleteConfirmDialog
    val showIntervalDialog: LiveData<VideoThumbnail?> = _showIntervalDialog

    init {
        viewModelScope.launch {
            _videoPath.collect { currentPath ->
                currentPath?.let { path ->
                    try {
                        repository.getThumbnailsForVideo(path).collect { thumbnails ->
                            _thumbnails.postValue(thumbnails.sortedBy { it.displayOrder })
                        }
                    } catch (e: Exception) {
                        _errorMessage.postValue(e.message ?: "Error loading thumbnails")
                    }
                }
            }
        }
    }

    fun setVideoPath(videoPath: String) {
        _videoPath.value = videoPath
    }

    fun addThumbnail(thumbnailUri: Uri, timestampMs: Long) {
        val videoPath = _videoPath.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = repository.addThumbnail(
                    videoPath = videoPath,
                    thumbnailUri = thumbnailUri,
                    timestampMs = timestampMs,
                    isDefault = _thumbnails.value.isNullOrEmpty()
                ) != null
                if (!success) {
                    _errorMessage.value = getApplication<Application>().getString(R.string.failed_to_add_thumbnail)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: getApplication<Application>().getString(R.string.unknown_error)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDefaultThumbnail(thumbnail: VideoThumbnail) {
        viewModelScope.launch {
            repository.setDefaultThumbnail(thumbnail)
        }
    }

    fun updateThumbnailInterval(thumbnail: VideoThumbnail, intervalMs: Long) {
        viewModelScope.launch {
            repository.updateThumbnailInterval(thumbnail, intervalMs)
        }
    }

    fun deleteThumbnail(thumbnail: VideoThumbnail) {
        viewModelScope.launch {
            repository.deleteThumbnail(thumbnail)
        }
    }

    fun updateThumbnailsOrder(thumbnails: List<VideoThumbnail>) {
        viewModelScope.launch {
            repository.updateThumbnailsOrder(thumbnails)
        }
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun showDeleteConfirmDialog(thumbnail: VideoThumbnail) {
        _showDeleteConfirmDialog.value = thumbnail
    }

    fun dismissDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = null
    }

    fun showIntervalDialog(thumbnail: VideoThumbnail) {
        _showIntervalDialog.value = thumbnail
    }

    fun dismissIntervalDialog() {
        _showIntervalDialog.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun formatTimestamp(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes % 60,
            seconds % 60
        )
    }
}
