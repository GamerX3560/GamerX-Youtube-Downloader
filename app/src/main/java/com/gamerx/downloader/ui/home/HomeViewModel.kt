package com.gamerx.downloader.ui.home

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo
import com.gamerx.downloader.data.model.PlaylistInfo
import com.gamerx.downloader.data.model.VideoInfo
import com.gamerx.downloader.data.repository.DownloadRepository
import com.gamerx.downloader.download.DownloadWorker
import com.gamerx.downloader.download.YtDlpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class HomeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val error: String? = null,
    val showDownloadSheet: Boolean = false,
    val selectedType: DownloadType = DownloadType.Video,
    val selectedFormat: FormatInfo? = null,
    val selectedContainer: String = "mp4",
    val downloadStarted: Boolean = false,
    // Playlist
    val playlistInfo: PlaylistInfo? = null,
    val showPlaylistSheet: Boolean = false,
    val selectedPlaylistIndices: Set<Int> = emptySet(),
    // Extra commands
    val extraCommands: String = "",
    val showAdvanced: Boolean = false,
    // Scheduling
    // Scheduling
    val scheduledAt: Long = 0L, // 0 = immediate
    // Recents
    val recentDownloads: List<DownloadEntity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val ytDlpManager: YtDlpManager,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCompletedDownloads().collect { list ->
                _uiState.value = _uiState.value.copy(
                    recentDownloads = list.take(3)
                )
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null)
    }

    fun fetchVideoInfo() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a URL")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, error = null, videoInfo = null, playlistInfo = null,
            )

            // First, check if it's a playlist
            val playlistResult = ytDlpManager.getPlaylistInfo(url)
            playlistResult.fold(
                onSuccess = { playlist ->
                    if (playlist != null && playlist.entries.size > 1) {
                        // It's a playlist — show playlist sheet
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            playlistInfo = playlist,
                            showPlaylistSheet = true,
                            selectedPlaylistIndices = playlist.entries.indices.toSet(),
                        )
                        return@launch
                    }
                },
                onFailure = { /* Not a playlist, fall through to single video */ },
            )

            // Single video
            val result = ytDlpManager.getVideoInfo(url)
            result.fold(
                onSuccess = { info ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videoInfo = info,
                        showDownloadSheet = true,
                        selectedFormat = info.videoFormats.firstOrNull(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to fetch video info",
                    )
                },
            )
        }
    }

    fun setDownloadType(type: DownloadType) {
        val info = _uiState.value.videoInfo ?: return
        val defaultFormat = when (type) {
            DownloadType.Audio -> info.audioFormats.firstOrNull()
            DownloadType.Video -> info.videoFormats.firstOrNull()
            DownloadType.Command -> null
        }
        val container = when (type) {
            DownloadType.Audio -> "mp3"
            DownloadType.Video -> "mp4"
            DownloadType.Command -> ""
        }
        _uiState.value = _uiState.value.copy(
            selectedType = type,
            selectedFormat = defaultFormat,
            selectedContainer = container,
        )
    }

    fun setSelectedFormat(format: FormatInfo) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun setContainer(container: String) {
        _uiState.value = _uiState.value.copy(selectedContainer = container)
    }

    fun setExtraCommands(commands: String) {
        _uiState.value = _uiState.value.copy(extraCommands = commands)
    }

    fun toggleAdvanced() {
        _uiState.value = _uiState.value.copy(showAdvanced = !_uiState.value.showAdvanced)
    }

    fun setScheduledAt(timestamp: Long) {
        _uiState.value = _uiState.value.copy(scheduledAt = timestamp)
    }

    fun clearSchedule() {
        _uiState.value = _uiState.value.copy(scheduledAt = 0L)
    }

    fun dismissDownloadSheet() {
        _uiState.value = _uiState.value.copy(showDownloadSheet = false)
    }

    fun dismissPlaylistSheet() {
        _uiState.value = _uiState.value.copy(showPlaylistSheet = false)
    }

    // Playlist selection
    fun togglePlaylistEntry(index: Int) {
        val current = _uiState.value.selectedPlaylistIndices.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _uiState.value = _uiState.value.copy(selectedPlaylistIndices = current)
    }

    fun selectAllPlaylistEntries() {
        val entries = _uiState.value.playlistInfo?.entries ?: return
        _uiState.value = _uiState.value.copy(selectedPlaylistIndices = entries.indices.toSet())
    }

    fun deselectAllPlaylistEntries() {
        _uiState.value = _uiState.value.copy(selectedPlaylistIndices = emptySet())
    }

    fun startDownload() {
        val state = _uiState.value
        val info = state.videoInfo ?: return

        viewModelScope.launch {
            val downloadDir = getDownloadDir(state.selectedType)
            val downloadItem = DownloadEntity(
                url = info.url,
                title = info.title,
                author = info.author,
                thumbnail = info.thumbnail,
                duration = info.duration,
                type = state.selectedType,
                format = state.selectedFormat ?: FormatInfo(),
                container = state.selectedContainer,
                allFormats = info.formats,
                downloadPath = downloadDir,
                website = info.website,
                status = DownloadStatus.Queued,
                extraCommands = state.extraCommands,
                scheduledAt = state.scheduledAt,
            )

            val id = repository.insert(downloadItem)
            enqueueDownload(id, state.scheduledAt)

            _uiState.value = _uiState.value.copy(
                showDownloadSheet = false,
                downloadStarted = true,
                videoInfo = null,
                url = "",
                extraCommands = "",
                scheduledAt = 0L,
                showAdvanced = false,
            )
        }
    }

    fun startBatchDownload() {
        val state = _uiState.value
        val playlist = state.playlistInfo ?: return
        val selectedIndices = state.selectedPlaylistIndices
        if (selectedIndices.isEmpty()) return

        viewModelScope.launch {
            val downloadDir = getDownloadDir(state.selectedType)
            val selectedEntries = playlist.entries.filterIndexed { i, _ -> i in selectedIndices }

            selectedEntries.forEach { entry ->
                val downloadItem = DownloadEntity(
                    url = entry.url,
                    title = entry.title.ifBlank { "Track ${playlist.entries.indexOf(entry) + 1}" },
                    author = entry.author.ifBlank { playlist.author },
                    thumbnail = entry.thumbnail,
                    duration = entry.duration,
                    type = state.selectedType,
                    format = FormatInfo(),
                    container = state.selectedContainer,
                    downloadPath = downloadDir,
                    website = entry.website,
                    status = DownloadStatus.Queued,
                    extraCommands = state.extraCommands,
                )

                val id = repository.insert(downloadItem)
                enqueueDownload(id, 0L)
            }

            _uiState.value = _uiState.value.copy(
                showPlaylistSheet = false,
                downloadStarted = true,
                playlistInfo = null,
                url = "",
                extraCommands = "",
                selectedPlaylistIndices = emptySet(),
            )
        }
    }

    fun consumeDownloadStarted() {
        _uiState.value = _uiState.value.copy(downloadStarted = false)
    }

    fun processSharedUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
        fetchVideoInfo()
    }

    private fun getDownloadDir(type: DownloadType): String {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return when (type) {
            DownloadType.Audio -> File(base, "GamerX_Downloads/Music").absolutePath
            else -> File(base, "GamerX_Downloads/Video").absolutePath
        }
    }

    private fun enqueueDownload(id: Long, scheduledAt: Long) {
        val delay = if (scheduledAt > 0) {
            (scheduledAt - System.currentTimeMillis()).coerceAtLeast(0)
        } else 0L

        val workRequest = DownloadWorker.createWorkRequest(id)
        if (delay > 0) {
            val delayedRequest = DownloadWorker.createWorkRequest(id, delay)
            workManager.enqueue(delayedRequest)
        } else {
            workManager.enqueue(workRequest)
        }
    }
}
