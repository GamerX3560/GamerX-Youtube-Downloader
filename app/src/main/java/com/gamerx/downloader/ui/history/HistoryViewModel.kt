package com.gamerx.downloader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamerx.downloader.data.db.entity.HistoryEntity
import com.gamerx.downloader.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val searchQuery: String = "",
    val showClearConfirm: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val historyItems: StateFlow<List<HistoryEntity>> = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getHistory()
            else repository.searchHistory(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun showClearConfirm() {
        _uiState.value = _uiState.value.copy(showClearConfirm = true)
    }

    fun dismissClearConfirm() {
        _uiState.value = _uiState.value.copy(showClearConfirm = false)
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.value = _uiState.value.copy(showClearConfirm = false)
        }
    }
}
