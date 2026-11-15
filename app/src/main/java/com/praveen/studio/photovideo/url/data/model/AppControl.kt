package com.praveen.studio.photovideo.url.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote switches that the developer sets in Firebase under `app/backend` (no app update needed):
 *  - demoEnabled / demoImageLimit : free demo photos on / off and how many
 *  - maintenance (+ maintenanceMessage) : the app shows "under maintenance" and does not open
 *  - newUpdate + latestVersion + updateLink (+ forceUpdate, updateMessage) : "new update available" dialog
 * Safe defaults are used when a field is missing or the phone is offline.
 */
data class AppControl(
    val demoEnabled: Boolean = true,
    val demoImageLimit: Long = 3L,
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
    val newUpdate: Boolean = false,
    val latestVersion: String = "",
    val updateLink: String = "",
    val forceUpdate: Boolean = false,
    val updateMessage: String = ""
) {
    /** Free demo photos a user may upload (0 when the demo is switched off). */
    val effectiveDemoLimit: Long get() = if (demoEnabled) demoImageLimit.coerceAtLeast(0L) else 0L
}

object AppControlStore {
    private val _state = MutableStateFlow(AppControl())
    val state: StateFlow<AppControl> = _state.asStateFlow()
    val current: AppControl get() = _state.value

    fun update(control: AppControl) {
        _state.value = control
    }
}
