package com.praveen.studio.photovideo.url.data.model

import java.net.URI
import java.net.URLEncoder

data class SupabaseConfig(
    val projectUrl: String = "",
    val anonKey: String = "",
    val bucketName: String = "media"
) {
    /**
     * Normalised project URL.
     *
     * For *.supabase.co projects only "https://<host>" is kept. This way values such as
     * "http://ref.supabase.co", "REF.supabase.co/" or "https://ref.supabase.co/rest/v1/" all produce
     * links that the website accepts (it only serves https://<ref>.supabase.co/storage/v1/object/public/...).
     */
    val cleanUrl: String
        get() {
            val trimmed = projectUrl.trim()
            if (trimmed.isBlank()) return ""
            val withScheme =
                if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed
                else "https://$trimmed"
            val host = try {
                URI(withScheme).host?.lowercase()
            } catch (_: Exception) {
                null
            }
            return if (host != null && host.endsWith(".supabase.co")) {
                "https://$host"
            } else {
                withScheme.trimEnd('/')
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

    private val encodedBucket: String
        get() = encodePathSegment(bucketName.trim())

    val publicUrlEndpoint: String
        get() = "$normalizedUrl/storage/v1/object/public/$encodedBucket"

    val uploadEndpoint: String
        get() = "$normalizedUrl/storage/v1/object/$encodedBucket"

    /**
     * The website only opens files hosted on https://<project>.supabase.co.
     * Custom domains / self-hosted Supabase work for uploading but the website will not show them.
     */
    val isWebCompatible: Boolean
        get() {
            if (!cleanUrl.startsWith("https://")) return false
            return try {
                URI(cleanUrl).host?.endsWith(".supabase.co") == true
            } catch (_: Exception) {
                false
            }
        }

    /** Role claim stored inside the key ("anon", "service_role", ...). Empty when it cannot be read. */
    val keyRole: String
        get() = try {
            val parts = anonKey.trim().split(".")
            if (parts.size < 2) {
                ""
            } else {
                val json = String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1).orEmpty()
            }
        } catch (_: Exception) {
            ""
        }

    val isServiceRoleKey: Boolean
        get() = keyRole == "service_role"

    companion object {
        fun encodePathSegment(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
