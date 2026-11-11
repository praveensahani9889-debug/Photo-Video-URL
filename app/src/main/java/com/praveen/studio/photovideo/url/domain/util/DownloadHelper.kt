package com.praveen.studio.photovideo.url.domain.util

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.praveen.studio.photovideo.url.data.remote.FoundItem

/** Saves a file into the public Downloads folder with the system download manager (progress in the notification). */
object DownloadHelper {

    /** Android 9 and older need the storage permission to write into the public Downloads folder. */
    fun needsWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    fun enqueue(context: Context, item: FoundItem): Result<Long> {
        return try {
            val request = DownloadManager.Request(Uri.parse(item.downloadUrl)).apply {
                setTitle(item.fileName)
                setDescription("Photo Video URL")
                if (item.mime.isNotBlank() && item.mime != "image/*") setMimeType(item.mime)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PhotoVideoURL/${item.fileName}")
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            Result.success(manager.enqueue(request))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
