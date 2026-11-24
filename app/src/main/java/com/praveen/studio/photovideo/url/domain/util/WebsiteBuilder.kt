package com.praveen.studio.photovideo.url.domain.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/** One file of a website that is ready to be uploaded: [path] is relative to the site folder ("css/app.css"). */
data class WebsiteFile(val path: String, val size: Long, val mime: String)

/**
 * A website that is ready to be uploaded: its files are in [dir] (the phone's cache), [entry] is the main page
 * (index.html), [skipped] lists the files that were left out (unsupported type or file name).
 */
data class WebsiteDraft(
    val name: String,
    val dir: File,
    val files: List<WebsiteFile>,
    val entry: String,
    val skipped: List<String>
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    fun fileOf(file: WebsiteFile): File = File(dir, file.path)
}

/**
 * Turns what the user gives (one HTML file, a ZIP with many files, or the files written in the Terminal) into a
 * [WebsiteDraft]. Only static website files are accepted (pages, styles, scripts, images, fonts, audio, video, data);
 * file names are limited to letters, numbers, "." "_" "-" so every file has a safe address. The ZIP is opened here, in
 * the background, because the website serves every file from its own address (it cannot open a ZIP on the server).
 */
object WebsiteBuilder {
    const val MAX_FILES = 300
    const val MAX_FILE_BYTES = 25L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 60L * 1024 * 1024
    private const val MAX_HTML_BYTES = 5L * 1024 * 1024
    private const val ROOT_FOLDER = "web_draft"

    private val TYPES = mapOf(
        "html" to "text/html", "htm" to "text/html", "css" to "text/css",
        "js" to "application/javascript", "mjs" to "application/javascript", "json" to "application/json",
        "webmanifest" to "application/manifest+json", "map" to "application/json",
        "txt" to "text/plain", "xml" to "application/xml", "csv" to "text/csv", "md" to "text/plain",
        "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "avif" to "image/avif", "ico" to "image/x-icon", "bmp" to "image/bmp",
        "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf", "eot" to "application/vnd.ms-fontobject",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
        "mp4" to "video/mp4", "webm" to "video/webm", "pdf" to "application/pdf"
    )

    private val SEGMENT = Regex("^[A-Za-z0-9._-]{1,100}$")

    /** Content type for a file name, or null when this type is not allowed on a website. */
    fun mimeOf(path: String): String? = TYPES[path.substringAfterLast('.', "").lowercase(Locale.US)]

    /** A safe relative path ("css/app.css") or null when the name is not allowed. */
    fun safePath(raw: String): String? {
        val p = raw.replace('\\', '/').trim().removePrefix("./").trim('/')
        if (p.isEmpty() || p.length > 250) return null
        val segs = p.split('/')
        if (segs.any { it == ".." || it == "." || it.startsWith(".") || !SEGMENT.matches(it) }) return null
        return p
    }

    private fun isJunk(raw: String): Boolean {
        val p = raw.replace('\\', '/')
        return p.startsWith("__MACOSX/") || p.contains("/__MACOSX/") || p.substringAfterLast('/') == ".DS_Store" ||
            p.substringAfterLast('/') == "Thumbs.db"
    }

    fun clean(context: Context) {
        try {
            File(context.cacheDir, ROOT_FOLDER).deleteRecursively()
        } catch (_: Throwable) {
        }
    }

    private fun newDir(context: Context): File {
        clean(context)
        return File(context.cacheDir, "$ROOT_FOLDER/${System.currentTimeMillis()}").also { it.mkdirs() }
    }

    /**
     * What the user picked: the CONTENT decides, not the name. A ZIP (also one that was renamed to "index.html")
     * is opened as a site; a text / HTML file becomes the main page; anything else is refused.
     */
    fun fromUri(context: Context, uri: Uri, displayName: String, mimeType: String): Result<WebsiteDraft> {
        val fallback = displayName.substringBeforeLast('.').ifBlank { "My website" }
        val lower = displayName.lowercase(Locale.US)
        val head = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = ByteArray(4)
                if (stream.read(bytes) == 4) bytes else null
            }
        } catch (_: Exception) {
            null
        }
        val isZip = head != null && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte())
        return when {
            isZip -> fromZip(context, uri, fallback)
            lower.endsWith(".html") || lower.endsWith(".htm") || mimeType == "text/html" || mimeType == "text/plain" ->
                fromHtml(context, uri, fallback)
            else -> Result.failure(IllegalStateException("Choose an index.html file or a ZIP file."))
        }
    }

    /** One HTML file (index.html or any name): it becomes the main page of the site. */
    fun fromHtml(context: Context, uri: Uri, fallbackName: String): Result<WebsiteDraft> = try {
        val dir = newDir(context)
        val target = File(dir, "index.html")
        val opened = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("The file cannot be opened.")
        val size = opened.use { copyLimited(it, target, MAX_HTML_BYTES) }
        // a real page is text: a file with binary bytes (a picture, an APK ...) would show as garbage
        val looksBinary = target.inputStream().use { input ->
            val buffer = ByteArray(4096)
            val n = input.read(buffer)
            n > 0 && (0 until n).any { buffer[it] == 0.toByte() }
        }
        if (looksBinary) throw IllegalStateException("This file is not an HTML text file. Choose index.html or a ZIP.")
        val files = listOf(WebsiteFile("index.html", size, "text/html"))
        Result.success(WebsiteDraft(titleOf(target) ?: fallbackName, dir, files, "index.html", emptyList()))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** A ZIP with the whole site (index.html at the top, or inside one folder). */
    fun fromZip(context: Context, uri: Uri, fallbackName: String): Result<WebsiteDraft> = try {
        val dir = newDir(context)
        val found = ArrayList<WebsiteFile>()
        val skipped = ArrayList<String>()
        var total = 0L
        val opened = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("The file cannot be opened.")
        opened.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !isJunk(name)) {
                        val safe = safePath(name)
                        val mime = safe?.let { mimeOf(it) }
                        if (safe == null || mime == null) {
                            skipped += name.substringAfterLast('/').ifEmpty { name }
                        } else {
                            if (found.size >= MAX_FILES) throw IllegalStateException("Too many files (the limit is $MAX_FILES).")
                            val target = File(dir, safe)
                            target.parentFile?.mkdirs()
                            val size = copyLimited(zip, target, MAX_FILE_BYTES)
                            total += size
                            if (total > MAX_TOTAL_BYTES) {
                                throw IllegalStateException("The website is bigger than ${MAX_TOTAL_BYTES / (1024 * 1024)} MB.")
                            }
                            found += WebsiteFile(safe, size, mime)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        finish(dir, found, skipped, fallbackName)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** The files written in the Terminal (name -> text). */
    fun fromTexts(context: Context, name: String, texts: Map<String, String>): Result<WebsiteDraft> = try {
        val dir = newDir(context)
        val found = ArrayList<WebsiteFile>()
        var total = 0L
        for ((rawName, text) in texts) {
            val safe = safePath(rawName) ?: throw IllegalStateException("\"$rawName\" is not a valid file name.")
            val mime = mimeOf(safe) ?: throw IllegalStateException("\"$rawName\": this file type is not allowed.")
            val bytes = text.toByteArray(Charsets.UTF_8)
            total += bytes.size
            if (total > MAX_TOTAL_BYTES) throw IllegalStateException("The website is too big.")
            val target = File(dir, safe)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            found += WebsiteFile(safe, bytes.size.toLong(), mime)
        }
        finish(dir, found, emptyList(), name.ifBlank { "My website" })
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Finds index.html (also inside a single top folder), and builds the draft. */
    private fun finish(dir: File, found: List<WebsiteFile>, skipped: List<String>, fallbackName: String): Result<WebsiteDraft> {
        if (found.isEmpty()) {
            return Result.failure(IllegalStateException("No website files were found."))
        }
        var files = found
        fun indexAt(list: List<WebsiteFile>): String? =
            list.firstOrNull { it.path.equals("index.html", true) }?.path
                ?: list.firstOrNull { it.path.equals("index.htm", true) }?.path

        var entry = indexAt(files)
        if (entry == null) {
            // everything inside one folder ("mysite/index.html"): that folder becomes the top
            val top = files.map { it.path.substringBefore('/', "") }.distinct()
            if (top.size == 1 && top[0].isNotEmpty()) {
                val prefix = top[0] + "/"
                val moved = files.map { WebsiteFile(it.path.removePrefix(prefix), it.size, it.mime) }
                if (indexAt(moved) != null) {
                    files.forEach { f ->
                        val from = File(dir, f.path)
                        val to = File(dir, f.path.removePrefix(prefix))
                        to.parentFile?.mkdirs()
                        from.renameTo(to)
                    }
                    files = moved
                    entry = indexAt(files)
                }
            }
        }
        if (entry == null) {
            return Result.failure(IllegalStateException("index.html was not found. Put index.html at the top of your website files."))
        }
        val title = titleOf(File(dir, entry))
        return Result.success(WebsiteDraft(title ?: fallbackName, dir, files, entry, skipped))
    }

    /** The page title of an HTML file (the text of <title>), or null. */
    private fun titleOf(file: File): String? = try {
        val head = file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var filled = 0
            while (filled < buffer.size) {
                val read = input.read(buffer, filled, buffer.size - filled)
                if (read < 0) break
                filled += read
            }
            String(buffer, 0, filled, Charsets.UTF_8)
        }
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(head)
            ?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.take(60)?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    private fun copyLimited(input: InputStream, target: File, limit: Long): Long {
        var written = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                written += read
                if (written > limit) {
                    throw IllegalStateException("A file is bigger than ${limit / (1024 * 1024)} MB.")
                }
                out.write(buffer, 0, read)
            }
        }
        return written
    }
}
