package com.praveen.studio.photovideo.url.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
