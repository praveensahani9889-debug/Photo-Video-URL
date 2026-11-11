package com.praveen.studio.photovideo.url.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.remote.FoundItem
import com.praveen.studio.photovideo.url.data.remote.LinkResolver
import com.praveen.studio.photovideo.url.data.remote.LinkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Found(val items: List<FoundItem>) : SearchUiState
    /** A friendly note (not supported / nothing to download), optionally with a link to open in the browser. */
    data class Message(val text: String, val openUrl: String?) : SearchUiState
    data class Error(val text: String) : SearchUiState
}

class LinkSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val resolver = LinkResolver()

    /** Recent searches live on this phone only. */
    val recentSearches: StateFlow<List<String>> = preferencesManager.recentSearchesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            preferencesManager.addRecentSearch(query)
            _state.value = when (val result = resolver.resolve(query)) {
                is LinkResult.Found -> SearchUiState.Found(result.items)
                is LinkResult.NotSupported -> SearchUiState.Message(result.message, result.openUrl)
                is LinkResult.Failed -> SearchUiState.Error(result.message)
            }
        }
    }

    fun clearResult() {
        searchJob?.cancel()
        _state.value = SearchUiState.Idle
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { preferencesManager.removeRecentSearch(query) }
    }

    fun clearRecents() {
        viewModelScope.launch { preferencesManager.clearRecentSearches() }
    }
}
