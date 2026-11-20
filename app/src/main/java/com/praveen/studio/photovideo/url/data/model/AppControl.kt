package com.praveen.studio.photovideo.url.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote switches that the developer sets in Firebase under `app/backend` (no app update needed):
 *  - demoEnabled / demoImageLimit : free demo photos on / off and how many
 *  - ipLimitEnabled / ipFreeLimit : the free demo limit per network (same public IP) on / off and how many
 *  - maxSupabaseAccounts : how many Supabase projects one user may add (default 5)
 *  - advancedOptionsEnabled : the "Advanced options" page after an upload on / off
 *  - blockedWords : extra words that cannot be used in a custom link (separated by commas)
 *  - maintenance (+ maintenanceMessage) : the app shows "under maintenance" and does not open
 *  - newUpdate + latestVersion + updateLink (+ forceUpdate, updateMessage) : "new update available" dialog
 * Safe defaults are used when a field is missing or the phone is offline.
 */
data class AppControl(
    val demoEnabled: Boolean = true,
    val demoImageLimit: Long = 3L,
    /** Free demo photos per network (public IP). Off: the network limit is not checked at all. */
    val ipLimitEnabled: Boolean = true,
    val ipFreeLimit: Long = 30L,
    /** How many Supabase projects one user can save in Settings. */
    val maxSupabaseAccounts: Int = 5,
    /** The "Advanced options" page (custom link, expiry, password ...) shown after an upload. */
    val advancedOptionsEnabled: Boolean = true,
    /** Extra words that cannot be used in a custom link, on top of the built-in list. */
    val blockedWords: List<String> = emptyList(),
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

    /** Free demo photos allowed per network, or null when the network limit is switched off. */
    val effectiveIpLimit: Long? get() = if (ipLimitEnabled) ipFreeLimit.coerceAtLeast(0L) else null

    /** Supabase projects one user may save (1..10, whatever is typed in Firebase). */
    val effectiveMaxAccounts: Int get() = maxSupabaseAccounts.coerceIn(1, 10)
}

object AppControlStore {
    private val _state = MutableStateFlow(AppControl())
    val state: StateFlow<AppControl> = _state.asStateFlow()
    val current: AppControl get() = _state.value

    fun update(control: AppControl) {
        _state.value = control
    }
}
