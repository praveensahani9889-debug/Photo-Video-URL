package com.praveen.studio.photovideo.url.domain.util

/**
 * Supabase Free plan allows max 50 MB per file (Pro and above can raise it).
 * Files bigger than this trigger the "ZIP & Upload" prompt.
 */
object UploadLimits {
    const val SUPABASE_FREE_MAX_BYTES: Long = 50L * 1024 * 1024

    fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
