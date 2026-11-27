package com.praveen.studio.photovideo.url.ui.screens.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Opens / closes the "Premium plans" page from anywhere in the app (Settings, locked features, limit messages). */
object PremiumNav {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun show() {
        _open.value = true
    }

    fun close() {
        _open.value = false
    }
}
