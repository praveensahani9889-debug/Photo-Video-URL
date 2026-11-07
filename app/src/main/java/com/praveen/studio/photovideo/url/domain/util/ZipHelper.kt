package com.praveen.studio.photovideo.url.domain.util

import android.content.Context
import android.util.Log
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipResult(
    val file: File,
    val zipName: String,
    val sizeBytes: Long
)

/**
 * Creates a ZIP of a single picked file inside the app cache, so the user can upload a
 * compressed copy without leaving the app.
 */
object ZipHelper {
    private const val TAG = "ZipHelper"
    const val ZIP_CACHE_DIR = "zipped"

    suspend fun zipItem(
        context: Context,
        item: SelectedMediaItem,
        onProgress: (Float) -> Unit
    ): Result<ZipResult> = withContext(Dispatchers.IO) {
        var zipFile: File? = null
        try {
            val dir = File(context.cacheDir, ZIP_CACHE_DIR)
            if (!dir.exists()) dir.mkdirs()

            val baseName = item.name.substringBeforeLast('.', item.name).ifBlank { "file" }
            val safeBase = baseName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "file" }
            val zipName = "$safeBase.zip"
            val target = File(dir, "${System.currentTimeMillis()}_$zipName")
            zipFile = target

            val input = context.contentResolver.openInputStream(item.uri)
                ?: return@withContext Result.failure(IOException("Cannot open the selected file"))

            val total = item.sizeBytes
            var copied = 0L
            var lastReported = -1

            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zos ->
                zos.putNextEntry(ZipEntry(item.name.replace('/', '_')))
                input.use { ins ->
                    val buffer = ByteArray(64 * 1024)
                    var read = ins.read(buffer)
                    while (read >= 0) {
                        zos.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                        read = ins.read(buffer)
                    }
                }
                zos.closeEntry()
            }

            Result.success(ZipResult(file = target, zipName = zipName, sizeBytes = target.length()))
        } catch (e: Exception) {
            Log.w(TAG, "ZIP failed: ${e.message}")
            zipFile?.delete()
            Result.failure(e)
        }
    }
}
