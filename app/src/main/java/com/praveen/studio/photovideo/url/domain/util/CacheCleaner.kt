package com.praveen.studio.photovideo.url.domain.util

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps the app's cache small. Temporary ZIPs, extracted APKs, PDF previews and image previews are
 * only needed while they are used, so they are removed on start and on demand.
 */
object CacheCleaner {
    private const val IMAGE_CACHE_DIR = "image_cache"

    /** Temp folders that are always safe to remove when no upload is running (app start). */
    private val TEMP_DIRS = listOf(ZipHelper.ZIP_CACHE_DIR, "extracted_apks", "viewer")

    fun cleanTempFiles(context: Context) {
        try {
            TEMP_DIRS.forEach { File(context.cacheDir, it).deleteRecursively() }
        } catch (_: Throwable) {
        }
    }

    fun sizeBytes(context: Context): Long {
        var total = dirSize(context.cacheDir)
        context.externalCacheDir?.let { total += dirSize(it) }
        return total
    }

    /** Clears every cached file (image previews included). Returns the freed bytes. */
    suspend fun clearAll(context: Context): Long = withContext(Dispatchers.IO) {
        val before = sizeBytes(context)
        try {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
        } catch (_: Throwable) {
        }
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name != IMAGE_CACHE_DIR) file.deleteRecursively()
            }
            context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Throwable) {
        }
        (before - sizeBytes(context)).coerceAtLeast(0L)
    }

    fun format(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", bytes / 1024.0)
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.add(child) else total += child.length()
            }
        }
        return total
    }
}
