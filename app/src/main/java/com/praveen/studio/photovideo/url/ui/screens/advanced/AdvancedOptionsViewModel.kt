package com.praveen.studio.photovideo.url.ui.screens.advanced

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.LinkOptions
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.domain.repository.LinkOptionsRepository
import com.praveen.studio.photovideo.url.domain.repository.NameCheck
import com.praveen.studio.photovideo.url.domain.util.LinkDraft
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the "Advanced options" page that opens after an upload (or from History to change a link). */
class AdvancedOptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LinkOptionsRepository(application)

    private var record: UploadRecord? = null
    private var existing: LinkOptions = LinkOptions()

    private val _draft = MutableStateFlow(LinkDraft())
    val draft: StateFlow<LinkDraft> = _draft.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Result of "Check availability" (null = not checked for the current text). */
    private val _nameCheck = MutableStateFlow<NameCheck?>(null)
    val nameCheck: StateFlow<NameCheck?> = _nameCheck.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** True when a password is already set on the link (the field may stay empty to keep it). */
    val hasExistingPassword: Boolean get() = existing.isPasswordProtected

    val currentExpiresAt: Long get() = existing.expiresAt

    /**
     * Opens the page for [rec]. For a link that was just created everything starts switched off.
     * With [loadExisting] (changing a link from History) the saved options are read once and shown.
     */
    fun start(rec: UploadRecord, loadExisting: Boolean) {
        record = rec
        existing = rec.options
        _error.value = null
        _nameCheck.value = null
        _saving.value = false
        if (!loadExisting) {
            _draft.value = LinkDraft()
            return
        }
        _draft.value = LinkDraft.fromOptions(rec.shortId, existing)
        viewModelScope.launch {
            _loading.value = true
            try {
                existing = repository.loadOptions(rec)
                _draft.value = LinkDraft.fromOptions(rec.shortId, existing)
            } finally {
                _loading.value = false
            }
        }
    }

    fun update(change: (LinkDraft) -> LinkDraft) {
        val before = _draft.value.slug
        _draft.update(change)
        if (_draft.value.slug != before) _nameCheck.value = null
        _error.value = null
    }

    /** One Firebase read, only when the user presses the button. */
    fun checkName() {
        val rec = record ?: return
        val slug = LinkSlugRules.normalize(_draft.value.slug)
        if (_checking.value) return
        if (slug == rec.shortId) {
            _nameCheck.value = NameCheck.Free
            return
        }
        viewModelScope.launch {
            _checking.value = true
            try {
                _nameCheck.value = repository.checkName(slug)
            } finally {
                _checking.value = false
            }
        }
    }

    /** Validates and saves everything; [onDone] gets the changed link. */
    fun apply(onDone: (UploadRecord) -> Unit) {
        val rec = record ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                repository.apply(rec, _draft.value, existing).fold(
                    onSuccess = { onDone(it) },
                    onFailure = { _error.value = it.message ?: "Could not save the options." }
                )
            } finally {
                _saving.value = false
            }
        }
    }
}
