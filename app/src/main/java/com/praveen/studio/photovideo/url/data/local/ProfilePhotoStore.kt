package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The profile picture the user can choose in Settings. It stays on the phone only (a small JPEG in the private app
 * folder); it is never uploaded.
 */
object ProfilePhotoStore {
    private const val FILE_NAME = "profile_photo.jpg"
    private const val MAX_SIDE = 512

    private val _version = MutableStateFlow(0)

    /** Changes whenever the picture was set or removed, so screens reload it. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun hasPhoto(context: Context): Boolean = file(context).isFile

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val f = file(context)
            if (f.isFile) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (_: Throwable) {
            null
        }
    }

    /** Reads the picture the user chose, makes it small and saves it. Returns false when it could not be read. */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.applicationContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext false

            val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            file(context).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            _version.value = _version.value + 1
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun remove(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
        _version.value = _version.value + 1
    }
}
