package com.praveen.studio.photovideo.url.data.model

data class UploadRecord(
    var shortId: String = "",
    var ownerInstallationId: String = "",
    var fileName: String = "",
    var fileType: String = "",
    var type: String = "",
    var mimeType: String = "",
    var fileSize: Long = 0L,
    var storageBucket: String = "",
    var storagePath: String = "",
    var originalSupabaseUrl: String = "",
    var supabaseUrl: String = "",
    var publicUrl: String = "",
    var shortUrl: String = "",
    var uploadDate: String = "",
    var uploadTime: String = "",
    var createdAt: Long = 0L,
    var timestamp: Long = 0L,
    var uploadDuration: Long = 0L,
    var status: String = "completed",
    var isDemo: Boolean = false,
    var isFreeTier: Boolean = false
) {
    val effectiveSupabaseUrl: String
        get() = supabaseUrl.ifEmpty { originalSupabaseUrl }

    val effectiveShortUrl: String
        get() = shortUrl.ifEmpty { publicUrl }

    val effectiveType: String
        get() = type.ifEmpty { fileType }

    val formattedSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$fileSize B"
            }
        }
}
