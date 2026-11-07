package com.praveen.studio.photovideo.url.data.model

data class SupabaseConfig(
    val projectUrl: String = "",
    val anonKey: String = "",
    val bucketName: String = "media"
) {
    val cleanUrl: String
        get() {
            val trimmed = projectUrl.trim()
            return when {
                trimmed.isBlank() -> ""
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed.trimEnd('/')
                else -> "https://${trimmed.trimEnd('/')}"
            }
        }

    val isConfigured: Boolean
        get() = cleanUrl.isNotBlank() &&
                anonKey.trim().isNotBlank() &&
                bucketName.trim().isNotBlank() &&
                cleanUrl.startsWith("http")

    val isPlaceholder: Boolean
        get() = projectUrl.contains("photo-video-url.supabase.co") ||
                projectUrl.contains("example") ||
                projectUrl.contains("placeholder") ||
                anonKey.contains("sample_anon_key") ||
                anonKey.contains("example")

    val normalizedUrl: String
        get() = cleanUrl

    val publicUrlEndpoint: String
        get() = "$normalizedUrl/storage/v1/object/public/${bucketName.trim()}"

    val uploadEndpoint: String
        get() = "$normalizedUrl/storage/v1/object/${bucketName.trim()}"
}
