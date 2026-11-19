package com.praveen.studio.photovideo.url.ui.screens.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Please set up Supabase": the Home screen, the upload dialogs and the share dialog all send the user to Settings,
 * where the full setup dialog then opens by itself. The request is consumed once.
 */
object SetupRequest {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
