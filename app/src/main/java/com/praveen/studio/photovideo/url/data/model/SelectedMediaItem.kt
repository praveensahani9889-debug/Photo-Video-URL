package com.praveen.studio.photovideo.url.data.model

import android.net.Uri

data class SelectedMediaItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: MediaCategory,
    /**
     * Free trial photo (max 3 per installation). Uploaded to the developer's Supabase that is
     * stored in Firebase `app/backend`. This is the ONLY place that backend config is used.
     */
    val isTrial: Boolean = false,
    /**
     * True when the user already chose "ZIP & Upload" or "Upload as is" for a big file,
     * so the app must not show the "file too large" prompt again for this item.
     */
    val skipSizePrompt: Boolean = false
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}
