package com.praveen.studio.photovideo.url.data.model

import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val sourceDir: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }
}
