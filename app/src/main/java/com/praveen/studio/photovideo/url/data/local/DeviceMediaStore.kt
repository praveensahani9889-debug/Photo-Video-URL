package com.praveen.studio.photovideo.url.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One photo / video / document / file found on the device. */
data class DeviceFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val dateSeconds: Long,
    val durationMs: Long = 0L
)

/**
 * Which storage permission each kind of file needs, and how to ask for it.
 *  - Android 9-12 : photos / videos / files -> "Storage" permission (READ_EXTERNAL_STORAGE)
 *  - Android 13+  : photos -> READ_MEDIA_IMAGES, videos -> READ_MEDIA_VIDEO
 *  - Android 11+  : documents / archives / APKs are no media files, they need "All files access"
 */
object StorageAccess {
    private val FILE_CATEGORIES = setOf(MediaCategory.DOCUMENTS, MediaCategory.FILES, MediaCategory.APKS)

    fun needsAllFilesAccess(category: MediaCategory): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && category in FILE_CATEGORIES

    fun runtimePermissions(category: MediaCategory): Array<String> {
        if (needsAllFilesAccess(category)) return emptyArray()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (category) {
                MediaCategory.IMAGES -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                MediaCategory.VIDEOS -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                else -> emptyArray()
            }
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasAccess(context: Context, category: MediaCategory): Boolean {
        if (needsAllFilesAccess(category)) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        }
        return runtimePermissions(category).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Settings page where the user switches "All files access" on for this app. */
    fun allFilesAccessIntent(context: Context): Intent {
        val appUri = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri)
        } else {
            appDetailsIntent(context)
        }
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

/** Reads the list of photos, videos, documents and files from the device (MediaStore). */
object DeviceMediaStore {
    private const val TAG = "DeviceMediaStore"
    private const val MAX_ITEMS = 4000

    private val DOCUMENT_EXTENSIONS = MediaCategory.DOCUMENTS.allowedExtensions + listOf("odt", "ods", "odp")
    private val FILE_EXTENSIONS = MediaCategory.FILES.allowedExtensions + listOf("tgz", "bz2", "xz", "obb", "img", "dmg")

    suspend fun load(context: Context, category: MediaCategory, allFileTypes: Boolean = false): List<DeviceFile> =
        withContext(Dispatchers.IO) {
            try {
                when (category) {
                    MediaCategory.IMAGES -> queryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
                    MediaCategory.VIDEOS -> queryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                    MediaCategory.DOCUMENTS -> queryFiles(context, DOCUMENT_EXTENSIONS, false)
                    MediaCategory.APKS -> queryFiles(context, MediaCategory.APKS.allowedExtensions, false)
                    MediaCategory.FILES -> queryFiles(context, FILE_EXTENSIONS, allFileTypes)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not read ${category.name}: ${t.message}")
                emptyList()
            }
        }

    private fun queryMedia(context: Context, baseUri: Uri, isVideo: Boolean): List<DeviceFile> {
        val projection = if (isVideo) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Video.VideoColumns.DURATION
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED
            )
        }
        val result = ArrayList<DeviceFile>()
        context.contentResolver.query(
            baseUri,
            projection,
            "${MediaStore.MediaColumns.SIZE} > 0",
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            while (c.moveToNext() && result.size < MAX_ITEMS) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "file_$id"
                result.add(
                    DeviceFile(
                        id = id,
                        uri = ContentUris.withAppendedId(baseUri, id),
                        name = name,
                        sizeBytes = c.getLong(sizeCol),
                        mimeType = c.getString(mimeCol)?.takeIf { it.isNotBlank() }
                            ?: LinkRules.mimeFromExtension(name).ifEmpty { if (isVideo) "video/*" else "image/*" },
                        dateSeconds = c.getLong(dateCol),
                        durationMs = if (durCol >= 0) c.getLong(durCol) else 0L
                    )
                )
            }
        }
        return result
    }

    /** Documents, archives and APKs: MediaStore "Files" table, filtered by file name ending. */
    private fun queryFiles(context: Context, extensions: List<String>, everyNonMediaFile: Boolean): List<DeviceFile> {
        val baseUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val args = ArrayList<String>()
        val selection = if (everyNonMediaFile) {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE} " +
                "AND ${MediaStore.MediaColumns.SIZE} > 0 " +
                "AND ${MediaStore.MediaColumns.DISPLAY_NAME} NOT LIKE '.%'"
        } else {
            val likes = extensions.joinToString(" OR ") { ext ->
                args.add("%.$ext")
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            }
            "${MediaStore.MediaColumns.SIZE} > 0 AND ($likes)"
        }

        val result = ArrayList<DeviceFile>()
        context.contentResolver.query(
            baseUri,
            projection,
            selection,
            if (args.isEmpty()) null else args.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext() && result.size < MAX_ITEMS) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                result.add(
                    DeviceFile(
                        id = id,
                        uri = ContentUris.withAppendedId(baseUri, id),
                        name = name,
                        sizeBytes = c.getLong(sizeCol),
                        mimeType = c.getString(mimeCol)?.takeIf { it.isNotBlank() }
                            ?: LinkRules.mimeFromExtension(name).ifEmpty { "application/octet-stream" },
                        dateSeconds = c.getLong(dateCol)
                    )
                )
            }
        }
        return result
    }
}

/** Small square previews for the picker. Kept in memory only (nothing is written to the disk cache). */
object DeviceThumbnails {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(context: Context, file: DeviceFile, isVideo: Boolean, sizePx: Int): Bitmap? {
        val key = (if (isVideo) "v" else "i") + file.id
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                create(context, file, isVideo, sizePx)
            } catch (_: Throwable) {
                null
            }
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun create(context: Context, file: DeviceFile, isVideo: Boolean, sizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.loadThumbnail(file.uri, Size(sizePx, sizePx), null)
            } catch (_: Throwable) {
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val thumb = if (isVideo) {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        resolver, file.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        resolver, file.id, MediaStore.Images.Thumbnails.MINI_KIND, null
                    )
                }
                if (thumb != null) return toSquare(thumb, sizePx)
            } catch (_: Throwable) {
            }
        }
        return if (isVideo) videoFrame(context, file.uri, sizePx) else decodeSampled(context, file.uri, sizePx)
    }

    private fun decodeSampled(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return decoded?.let { toSquare(it, sizePx) }
    }

    private fun videoFrame(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)?.let { toSquare(it, sizePx) }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun toSquare(src: Bitmap, px: Int): Bitmap {
        val side = minOf(src.width, src.height)
        if (side <= 0) return src
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = if (src.width == src.height) src else Bitmap.createBitmap(src, x, y, side, side)
        return if (side > px) Bitmap.createScaledBitmap(cropped, px, px, true) else cropped
    }
}
