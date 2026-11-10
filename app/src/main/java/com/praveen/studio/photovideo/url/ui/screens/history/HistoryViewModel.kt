package com.praveen.studio.photovideo.url.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.repository.DeleteOutcome
import com.praveen.studio.photovideo.url.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Progress / result of deleting uploads. */
sealed interface DeleteState {
    data object Idle : DeleteState
    data class Deleting(val done: Int, val total: Int) : DeleteState
    data class Finished(val deleted: Int, val failed: Int, val messages: List<String>) : DeleteState
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRepository = HistoryRepository(application)

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Every entry (unfiltered) - needed so "clear" can hide exactly what is listed. */
    private val allHistory: StateFlow<List<UploadRecord>> = historyRepository.getHistoryFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<UploadRecord>> = combine(
        allHistory,
        _selectedFilter,
        _searchQuery
    ) { history, filter, query ->
        history.filter { record ->
            val matchesFilter = when (filter) {
                "All" -> true
                "Images" -> record.fileType.equals("Images", ignoreCase = true) || record.mimeType.startsWith("image/")
                "Videos" -> record.fileType.equals("Videos", ignoreCase = true) || record.mimeType.startsWith("video/")
                "Documents" -> record.fileType.equals("Documents", ignoreCase = true) || record.mimeType.contains("pdf") || record.mimeType.contains("document")
                "Files" -> record.fileType.equals("Files", ignoreCase = true)
                "APKs" -> record.fileType.equals("APKs", ignoreCase = true) || record.fileName.endsWith(".apk", ignoreCase = true)
                else -> true
            }

            val matchesQuery = query.isBlank() ||
                    record.fileName.contains(query, ignoreCase = true) ||
                    record.shortId.contains(query, ignoreCase = true) ||
                    record.publicUrl.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ---- delete mode: pick single uploads or "Select all", confirm, delete from Supabase + Firebase ----

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    fun startSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = true
    }

    fun cancelSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(shortId: String) {
        _selectedIds.update { if (shortId in it) it - shortId else it + shortId }
    }

    /** Selects everything that is currently listed (respects the search / filter); a second tap clears it. */
    fun toggleSelectAll() {
        val visibleIds = filteredHistory.value.map { it.shortId }.toSet()
        _selectedIds.update { current ->
            if (visibleIds.isNotEmpty() && current.containsAll(visibleIds)) current - visibleIds else current + visibleIds
        }
    }

    fun deleteSelected() {
        if (_deleteState.value is DeleteState.Deleting) return
        val ids = _selectedIds.value
        val records = allHistory.value.filter { it.shortId in ids }
        if (records.isEmpty()) {
            cancelSelection()
            return
        }
        viewModelScope.launch {
            _deleteState.value = DeleteState.Deleting(0, records.size)
            val outcome = try {
                historyRepository.deleteRecords(records) { done, total ->
                    _deleteState.value = DeleteState.Deleting(done, total)
                }
            } catch (t: Throwable) {
                DeleteOutcome(0, records.size, listOf(t.message ?: "Delete failed"))
            }
            _deleteState.value = DeleteState.Finished(outcome.deleted, outcome.failed, outcome.messages)
            cancelSelection()
        }
    }

    fun dismissDeleteResult() {
        _deleteState.value = DeleteState.Idle
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory(allHistory.value.map { it.shortId })
        }
    }
}
