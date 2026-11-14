package com.praveen.studio.photovideo.url.data.remote

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.ViewerContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

enum class FoundKind { IMAGE, VIDEO, AUDIO, DOCUMENT, FILE }

/** One photo / video / document / file that can be shown and downloaded. */
data class FoundItem(
    val fileName: String,
    /** Address of the file itself (used for the preview). */
    val url: String,
    /** Address used for the download. */
    val downloadUrl: String,
    val mime: String,
    val kind: FoundKind,
    val sizeBytes: Long?,
    val source: String
)

sealed interface LinkResult {
    data class Found(val items: List<FoundItem>) : LinkResult
    data class NotSupported(val message: String, val openUrl: String?) : LinkResult
    data class Failed(val message: String) : LinkResult
}

/**
 * Turns what the user typed into something that can be shown and downloaded:
 *  - a Photo Video URL link (https://ivu.gt.tc/image_xxx or just the id): looked up like the website does;
 *  - a direct link to a photo, video, audio or file;
 *  - a normal web page that itself publishes a photo / video address in its standard page tags
 *    (og:image, og:video, <video src>).
 * Video and social platforms are not handled: saving from them is against their rules.
 */
class LinkResolver(
    private val firebase: FirebaseDatabaseManager = FirebaseDatabaseManager(),
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val TAG = "LinkResolver"
        private const val MAX_HTML_BYTES = 600_000L
        private const val MAX_CANDIDATES = 6
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9; Mobile) PhotoVideoURL/2.4.1"

        const val NOT_SUPPORTED_MESSAGE =
            "Saving photos or videos from this site is not supported here (YouTube, Instagram, Facebook, X/Twitter, " +
                "Snapchat, TikTok and similar services do not allow it). Please use the official app or website " +
                "of that service."

        /** Hosts (and their sub domains) that are never downloaded from. */
        private val NOT_SUPPORTED_HOSTS = listOf(
            "youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
            "instagram.com", "cdninstagram.com",
            "facebook.com", "fb.com", "fb.watch", "fbcdn.net",
            "twitter.com", "x.com", "t.co", "twimg.com",
            "snapchat.com", "sc-cdn.net",
            "tiktok.com", "tiktokcdn.com"
        )

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        fun isNotSupportedHost(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return NOT_SUPPORTED_HOSTS.any { h == it || h.endsWith(".$it") }
        }
    }

    suspend fun resolve(input: String): LinkResult = withContext(Dispatchers.IO) {
        try {
            resolveInternal(input.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "resolve notice: ${t.message}")
            LinkResult.Failed("Could not read this link. Check the link and your internet connection.")
        }
    }

    private suspend fun resolveInternal(text: String): LinkResult {
        if (text.isEmpty()) return LinkResult.Failed("Paste a link first.")

        // 1. Photo Video URL link or id
        extractShortId(text)?.let { return resolveAppLink(it) }

        // 2. any other link
        val url = normalizeUrl(text) ?: return LinkResult.Failed("This does not look like a link.")
        if (isNotSupportedHost(url.host)) return LinkResult.NotSupported(NOT_SUPPORTED_MESSAGE, url.toString())
        return resolveWebLink(url)
    }

    // ---------------------------------------------------------------------------------------------
    // Photo Video URL links
    // ---------------------------------------------------------------------------------------------

    private fun extractShortId(text: String): String? {
        LinkRules.parseShortId(text)?.let { return it }
        val url = normalizeUrl(text) ?: return null
        val host = url.host.lowercase()
        return if (host == "ivu.gt.tc" || host == "www.ivu.gt.tc") LinkRules.parseShortId(url.encodedPath) else null
    }

    private suspend fun resolveAppLink(shortId: String): LinkResult {
        val content = LinkRules.resolve(shortId) { id -> firebase.lookupPublicLink(id) }
        val source = "Photo Video URL"
        return when (content) {
            is ViewerContent.Image ->
                found(content.name, content.url, content.url, content.mime.ifEmpty { "image/*" }, FoundKind.IMAGE, source)
            is ViewerContent.Video ->
                found(content.name, content.url, content.url, content.mime.ifEmpty { "video/mp4" }, FoundKind.VIDEO, source)
            is ViewerContent.Pdf ->
                found(content.name, content.url, content.downloadUrl ?: content.url, "application/pdf", FoundKind.DOCUMENT, source)
            is ViewerContent.Download -> found(
                content.name,
                content.downloadUrl,
                content.downloadUrl,
                content.mime.ifEmpty { "application/octet-stream" },
                if (content.type == "document") FoundKind.DOCUMENT else FoundKind.FILE,
                source
            )
            ViewerContent.Invalid -> LinkResult.Failed("This is not a valid Photo Video URL link.")
            ViewerContent.NotFound -> LinkResult.Failed("This link was not found. It may have been deleted.")
            ViewerContent.Disabled -> LinkResult.Failed("This link has been disabled.")
            ViewerContent.Unsupported -> LinkResult.Failed("This file type is not supported.")
            ViewerContent.Unavailable ->
                LinkResult.Failed("The server cannot be reached right now. Check your internet and try again.")
            else -> LinkResult.Failed("This link cannot be opened.")
        }
    }

    private fun found(
        name: String,
        url: String,
        downloadUrl: String,
        mime: String,
        kind: FoundKind,
        source: String
    ): LinkResult {
        val size = url.toHttpUrlOrNull()?.let { u ->
            try {
                fetch(u, readHtml = false).length
            } catch (_: Throwable) {
                null
            }
        }
        return LinkResult.Found(listOf(FoundItem(name, url, downloadUrl, mime, kind, size, source)))
    }

    // ---------------------------------------------------------------------------------------------
    // Other links
    // ---------------------------------------------------------------------------------------------

    private fun normalizeUrl(text: String): HttpUrl? {
        if (text.any { it.isWhitespace() }) return null
        val withScheme = if (text.contains("://")) text else "https://$text"
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (!parsed.host.contains('.')) return null
        // Android blocks plain http traffic, so a secure address is used
        return if (parsed.scheme == "http") parsed.newBuilder().scheme("https").build() else parsed
    }

    private class Fetched(
        val finalUrl: HttpUrl,
        val code: Int,
        val contentType: String,
        val length: Long?,
        val headerFileName: String?,
        val html: String?
    )

    private fun fetch(url: HttpUrl, readHtml: Boolean): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
            val length = response.header("Content-Length")?.toLongOrNull()
                ?: response.body?.contentLength()?.takeIf { it >= 0 }
            var html: String? = null
            if (readHtml && response.isSuccessful &&
                (contentType == "text/html" || contentType == "application/xhtml+xml")
            ) {
                html = response.peekBody(MAX_HTML_BYTES).string()
            }
            return Fetched(
                finalUrl = response.request.url,
                code = response.code,
                contentType = contentType,
                length = length,
                headerFileName = fileNameFromDisposition(response.header("Content-Disposition")),
                html = html
            )
        }
    }

    private fun resolveWebLink(url: HttpUrl): LinkResult {
        val first = fetch(url, readHtml = true)
        if (first.code !in 200..299) {
            return LinkResult.Failed("The site answered with an error (${first.code}).")
        }
        if (isNotSupportedHost(first.finalUrl.host)) {
            return LinkResult.NotSupported(NOT_SUPPORTED_MESSAGE, first.finalUrl.toString())
        }

        // a direct link to a file
        val directKind = kindOf(first.contentType, first.finalUrl.encodedPath)
        if (directKind != null) {
            return LinkResult.Found(listOf(buildItem(first, directKind, null)))
        }

        // a page that publishes its photo / video in the standard page tags
        val html = first.html
        if (html != null) {
            val items = itemsFromPage(first.finalUrl, html)
            if (items.isNotEmpty()) return LinkResult.Found(items)
            return LinkResult.NotSupported(
                "No downloadable photo or video was found on this page. Only direct links and pages that publish " +
                    "their file address can be used.",
                url.toString()
            )
        }
        return LinkResult.Failed("This link does not contain a photo, video or file that can be downloaded.")
    }

    private fun itemsFromPage(base: HttpUrl, html: String): List<FoundItem> {
        val title = metaContent(html, "og:title") ?: metaContent(html, "twitter:title") ?: titleTag(html)

        val videoCandidates = listOfNotNull(
            metaContent(html, "og:video:secure_url"),
            metaContent(html, "og:video:url"),
            metaContent(html, "og:video"),
            metaContent(html, "twitter:player:stream")
        ) + mediaTagSources(html)
        val imageCandidates = listOfNotNull(
            metaContent(html, "og:image:secure_url"),
            metaContent(html, "og:image"),
            metaContent(html, "twitter:image"),
            metaContent(html, "twitter:image:src")
        )

        val result = mutableListOf<FoundItem>()
        val seen = HashSet<String>()
        for (candidate in (videoCandidates + imageCandidates).distinct().take(MAX_CANDIDATES)) {
            val absolute = base.resolve(htmlDecode(candidate)) ?: continue
            if (isNotSupportedHost(absolute.host) || !seen.add(absolute.toString())) continue
            val fetched = try {
                fetch(absolute, readHtml = false)
            } catch (_: Throwable) {
                continue
            }
            if (fetched.code !in 200..299) continue
            val kind = kindOf(fetched.contentType, fetched.finalUrl.encodedPath) ?: continue
            // from a page only real photos / videos / audio count (not scripts, embeds or other pages)
            if (kind == FoundKind.FILE || kind == FoundKind.DOCUMENT) continue
            result += buildItem(fetched, kind, title)
        }
        return result
    }

    private fun kindOf(contentType: String, path: String): FoundKind? {
        var type = contentType
        if (type.isEmpty() || type == "application/octet-stream" || type == "binary/octet-stream") {
            val byExtension = LinkRules.mimeFromExtension(path)
            if (byExtension.isNotEmpty()) type = byExtension
        }
        return when {
            type.startsWith("image/") -> FoundKind.IMAGE
            type.startsWith("video/") -> FoundKind.VIDEO
            type.startsWith("audio/") -> FoundKind.AUDIO
            type == "text/html" || type == "application/xhtml+xml" || type.isEmpty() -> null
            type == "application/pdf" || type == "application/msword" || type.startsWith("application/vnd.") ->
                FoundKind.DOCUMENT
            type.startsWith("application/") || type.startsWith("text/") -> FoundKind.FILE
            else -> null
        }
    }

    private fun buildItem(fetched: Fetched, kind: FoundKind, titleHint: String?): FoundItem {
        var mime = fetched.contentType
        if (mime.isEmpty() || mime == "application/octet-stream") {
            mime = LinkRules.mimeFromExtension(fetched.finalUrl.encodedPath).ifEmpty { mime }
        }
        val fileName = fileNameFor(fetched, mime, titleHint)
        val address = fetched.finalUrl.toString()
        return FoundItem(
            fileName = fileName,
            url = address,
            downloadUrl = address,
            mime = mime.ifEmpty { "application/octet-stream" },
            kind = kind,
            sizeBytes = fetched.length,
            source = fetched.finalUrl.host
        )
    }

    private fun fileNameFor(fetched: Fetched, mime: String, titleHint: String?): String {
        val fromPath = Uri.decode(fetched.finalUrl.pathSegments.lastOrNull().orEmpty())
        var name = fetched.headerFileName?.takeIf { it.isNotBlank() }
            ?: fromPath.takeIf { it.isNotBlank() }
            ?: titleHint
            ?: "download"
        name = LinkRules.safeFilename(name, "download")
        val extension = name.substringAfterLast('.', "")
        if (extension.isEmpty() || extension.length > 5) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { name += ".$it" }
        }
        return name
    }

    // ---------------------------------------------------------------------------------------------
    // Small HTML helpers
    // ---------------------------------------------------------------------------------------------

    private fun metaContent(html: String, key: String): String? {
        val tag = Regex(
            "<meta\\b[^>]*?(?:property|name)\\s*=\\s*[\"']" + Regex.escape(key) + "[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        ).find(html)?.value ?: return null
        return Regex("content\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun mediaTagSources(html: String): List<String> =
        Regex("<(?:video|source)\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.toList()

    private fun titleTag(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { htmlDecode(it).trim() }?.takeIf { it.isNotBlank() }

    private fun htmlDecode(text: String): String =
        text.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&#x2F;", "/").replace("&lt;", "<").replace("&gt;", ">")

    private fun fileNameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val match = Regex("filename\\*?\\s*=\\s*(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)
        return match?.groupValues?.get(1)?.trim()?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
    }
}
