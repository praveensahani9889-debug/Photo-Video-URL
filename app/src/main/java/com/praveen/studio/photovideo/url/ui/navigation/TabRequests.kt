package com.praveen.studio.photovideo.url.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "Open this tab of the main screen": set by MainActivity (for example when the Quick Settings tile is tapped).
 * The main screen reads it once and clears it.
 */
object TabRequests {
    val pending = MutableStateFlow<String?>(null)

    /** The Quick Settings tile asks the Search tab to open the QR scanner at once. Cleared by the Search tab. */
    val openScanner = MutableStateFlow(false)
}
