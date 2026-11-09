package com.praveen.studio.photovideo.url.domain.util

import com.praveen.studio.photovideo.url.data.model.PublicLinkLookup
import com.praveen.studio.photovideo.url.data.model.PublicLinkRecord
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** What the in-app viewer must show for a short link. Mirrors the website's responses. */
sealed interface ViewerContent {
    /** 400 - link id is not valid. */
    data object Invalid : ViewerContent

    /** 404 - no such media. */
    data object NotFound : ViewerContent

    /** 410 - link was disabled (active = false). */
    data object Disabled : ViewerContent

    /** 415 - file type cannot be displayed. */
    data object Unsupported : ViewerContent

    /** 503 - database temporarily unreachable. */
    data object Unavailable : ViewerContent

    data class Image(val url: String, val name: String, val mime: String) : ViewerContent
    data class Video(val url: String, val name: String, val mime: String) : ViewerContent
    data class Pdf(val url: String, val name: String, val downloadUrl: String?) : ViewerContent

    /** Documents (non-PDF), generic files and APKs: the website redirects to a download. */
    data class Download(
        val type: String,
        val name: String,
        val downloadUrl: String,
        val mime: String
    ) : ViewerContent
}

/**
 * Kotlin port of the website rules (app/security.php, app/media_handler.php, app/resolver.php),
 * so a link opened inside the app behaves exactly like the same link opened in the browser.
 */
object LinkRules {

    const val SITE_URL = "https://ivu.gt.tc"

    private val ID_REGEX = Regex("^(image|video|document|file|apk)_[A-Za-z0-9_-]{3,64}$")
    private val SUPABASE_HOST = Regex("^[a-z0-9]([a-z0-9-]{1,61})[a-z0-9]\\.supabase\\.co$")
    private val FORBIDDEN_URL_CHARS = Regex("[\\u0000-\\u0020\\u007f\"'<>\\\\`]")
    private val DOT_SEGMENT = Regex("(^|/)(\\.|%2e){2}(/|$)", RegexOption.IGNORE_CASE)
    private val MIME_REGEX = Regex("^[a-z0-9][a-z0-9.+-]{0,60}/[a-z0-9][a-z0-9.+-]{0,100}$")

    private val MIME_BY_EXTENSION = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "svg" to "image/svg+xml", "avif" to "image/avif",
        "heic" to "image/heic", "ico" to "image/x-icon",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm", "mov" to "video/quicktime",
        "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "3gp" to "video/3gpp", "ogv" to "video/ogg",
        "pdf" to "application/pdf",
        "apk" to "application/vnd.android.package-archive",
        "zip" to "application/zip", "txt" to "text/plain", "csv" to "text/csv", "json" to "application/json",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )

    /** "/image_AbC123/" or "image_AbC123" -> "image_AbC123"; anything else -> null. */
    fun parseShortId(raw: String?): String? {
        if (raw == null) return null
        val s = raw.trim().removePrefix("/").trimEnd('/')
        return if (ID_REGEX.matches(s)) s else null
    }

    /** "image", "video", "document", "file" or "apk". */
    fun typeOf(shortId: String): String = shortId.substringBefore('_')

    /** Same checks as is_allowed_storage_url() in the website. */
    fun isAllowedStorageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty() || url.length > 2048) return false
        if (FORBIDDEN_URL_CHARS.containsMatchIn(url)) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.userInfo != null || uri.rawFragment != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        if (!SUPABASE_HOST.matches(host)) return false
        val path = uri.rawPath ?: return false
        if (!path.startsWith("/storage/v1/object/public/")) return false
        if (DOT_SEGMENT.containsMatchIn(path)) return false
        return true
    }

    fun isValidMime(mime: String?): Boolean =
        !mime.isNullOrEmpty() && MIME_REGEX.matches(mime.lowercase())

    fun mimeFromExtension(fileName: String?): String {
        if (fileName.isNullOrEmpty()) return ""
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[ext].orEmpty()
    }

    fun safeFilename(name: String?, fallback: String = "download"): String {
        var n = (name ?: "").replace(Regex("[\\u0000-\\u001f\\u007f]"), "")
        for (c in charArrayOf('/', '\\', '"', '\'', ':', '*', '?', '<', '>', '|')) {
            n = n.replace(c, '_')
        }
        n = n.replace(Regex("[^\\p{L}\\p{N} ._()\\-]"), "_")
        n = n.trim { it == ' ' || it == '.' }
        if (n.length > 120) n = n.substring(0, 120)
        return n.ifEmpty { fallback }
    }

    private fun rawUrlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun rawUrlDecode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }

    private fun urlPath(url: String): String = try {
        URI(url).rawPath.orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** Same naming rules as record_filename() in the website. */
    fun recordFilename(rec: PublicLinkRecord, type: String): String {
        val source = rec.fileName.ifEmpty { rec.storagePath.ifEmpty { urlPath(rec.url) } }
        val base = source.substringAfterLast('/')
        var name = safeFilename(rawUrlDecode(base))
        if (type == "apk" && !name.endsWith(".apk", ignoreCase = true)) name += ".apk"
        return name
    }

    /** Supabase URL with ?download=<name> (browser saves it under the proper file name). */
    fun downloadUrl(rec: PublicLinkRecord, name: String): String? {
        var base = rec.url.replace(Regex("([?&])download=[^&]*&?"), "\$1")
        base = base.trimEnd('?', '&')
        val joined = base + (if (base.contains('?')) "&" else "?") + "download=" + rawUrlEncode(name)
        return if (isAllowedStorageUrl(joined)) joined else null
    }

    /** Port of handle_media(): decides what the viewer shows for a loaded record. */
    fun contentFor(shortId: String, record: PublicLinkRecord): ViewerContent {
        val type = typeOf(shortId)
        if (!record.active) return ViewerContent.Disabled
        if (record.type != null && record.type != type) return ViewerContent.NotFound
        if (!isAllowedStorageUrl(record.url)) return ViewerContent.NotFound

        val name = recordFilename(record, type)
        var mime = if (isValidMime(record.mime)) record.mime.lowercase() else mimeFromExtension(name)
        if (mime.isEmpty()) mime = mimeFromExtension(urlPath(record.url))
        if (mime == "application/octet-stream") {
            var guess = mimeFromExtension(name)
            if (guess.isEmpty()) guess = mimeFromExtension(urlPath(record.url))
            if (guess.isNotEmpty()) mime = guess
        }

        return when {
            type == "image" ->
                if (mime.isNotEmpty() && !mime.startsWith("image/")) ViewerContent.Unsupported
                else ViewerContent.Image(record.url, name, mime)

            type == "video" ->
                if (mime.isNotEmpty() && !mime.startsWith("video/")) ViewerContent.Unsupported
                else ViewerContent.Video(record.url, name, mime.ifEmpty { "video/mp4" })

            type == "document" && mime == "application/pdf" ->
                ViewerContent.Pdf(record.url, name, downloadUrl(record, name))

            else -> {
                val dl = downloadUrl(record, name) ?: return ViewerContent.NotFound
                ViewerContent.Download(type, name, dl, mime)
            }
        }
    }

    /** Full lookup + decision, as the website does for GET /<id>. */
    suspend fun resolve(
        rawId: String,
        lookup: suspend (String) -> PublicLinkLookup
    ): ViewerContent {
        val id = parseShortId(rawId) ?: return ViewerContent.Invalid
        return when (val result = lookup(id)) {
            is PublicLinkLookup.Found ->
                contentFor(id, result.record)
            PublicLinkLookup.Missing -> ViewerContent.NotFound
            PublicLinkLookup.Error -> ViewerContent.Unavailable
        }
    }
}
