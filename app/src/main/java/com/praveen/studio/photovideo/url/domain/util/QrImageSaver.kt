package com.praveen.studio.photovideo.url.domain.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Saves the QR picture into Pictures/PhotoVideoURL (it shows up in the gallery). */
object QrImageSaver {
    private const val FOLDER = "PhotoVideoURL"

    /** Android 9 and older need the storage permission to write into the public Pictures folder. */
    fun needsWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    fun fileNameFor(link: String): String {
        val id = link.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .take(40)
        return "QR_${id.ifEmpty { System.currentTimeMillis().toString() }}.png"
    }

    fun save(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(context, bitmap, fileName)
            else saveToPicturesFolder(context, bitmap, fileName)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IOException("Could not create the picture"))
        return try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the picture")
            out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.success(uri)
        } catch (t: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Throwable) {
            }
            Result.failure(t)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPicturesFolder(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!folder.exists() && !folder.mkdirs()) return Result.failure(IOException("Could not create the folder"))
        var file = File(folder, fileName)
        var counter = 1
        while (file.exists()) {
            file = File(folder, fileName.removeSuffix(".png") + "_" + counter++ + ".png")
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return Result.success(Uri.fromFile(file))
    }
}
