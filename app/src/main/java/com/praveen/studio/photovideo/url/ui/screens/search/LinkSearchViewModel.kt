package com.praveen.studio.photovideo.url.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.remote.FoundItem
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
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
    /** The website itself: shown with the banner of Photo Video URL. */
    data class Website(val url: String, val title: String, val text: String) : SearchUiState
    /** A scanned QR code that has no photo, video or file: its text, and its link ready to open (null = not a link). */
    data class ScannedLink(val text: String, val url: String?) : SearchUiState
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

    fun search(raw: String, fromScan: Boolean = false) {
        val query = raw.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            preferencesManager.addRecentSearch(query)
            _state.value = when (val result = resolver.resolve(query)) {
                is LinkResult.Found -> SearchUiState.Found(result.items)
                // from the QR scanner: when nothing can be downloaded, the link itself is given directly
                is LinkResult.NotSupported ->
                    if (fromScan) SearchUiState.ScannedLink(query, result.openUrl ?: asWebUrl(query))
                    else SearchUiState.Message(result.message, result.openUrl)
                is LinkResult.Failed ->
                    if (fromScan) SearchUiState.ScannedLink(query, asWebUrl(query))
                    else SearchUiState.Error(result.message)
                is LinkResult.Website -> SearchUiState.Website(result.url, result.title, result.text)
                is LinkResult.OpenViewer -> {
                    IncomingIntentManager.openViewer(result.shortId)
                    SearchUiState.Idle
                }
            }
        }
    }

    /** The text as a link that a browser can open, or null when it is no link (spaces, other schemes, plain words). */
    private fun asWebUrl(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val lower = t.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> t
            lower.contains("://") -> null
            t.contains('.') && !t.startsWith(".") && !t.endsWith(".") -> "https://$t"
            else -> null
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
