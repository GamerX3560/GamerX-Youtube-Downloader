package com.gamerx.downloader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.repository.DownloadRepository
import com.gamerx.downloader.download.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val selectedTab: Int = 0,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val activeDownloads: StateFlow<List<DownloadEntity>> = repository.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedDownloads: StateFlow<List<DownloadEntity>> = repository.getQueuedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = repository.getCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cancelledDownloads: StateFlow<List<DownloadEntity>> = repository.getCancelledDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val erroredDownloads: StateFlow<List<DownloadEntity>> = repository.getErroredDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun cancelDownload(id: Long) {
        viewModelScope.launch {
            workManager.cancelAllWorkByTag("download_$id")
            repository.markCancelled(id)
        }
    }

    fun retryDownload(id: Long) {
        viewModelScope.launch {
            repository.updateStatus(id, DownloadStatus.Queued)
            val workRequest = DownloadWorker.createWorkRequest(id)
            workManager.enqueue(workRequest)
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch {
            workManager.cancelAllWorkByTag("download_$id")
            repository.deleteById(id)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.deleteByStatus(DownloadStatus.Completed)
        }
    }

    fun clearCancelled() {
        viewModelScope.launch {
            repository.deleteByStatus(DownloadStatus.Cancelled)
        }
    }

    fun clearErrored() {
        viewModelScope.launch {
            repository.deleteByStatus(DownloadStatus.Error)
        }
    }
}
