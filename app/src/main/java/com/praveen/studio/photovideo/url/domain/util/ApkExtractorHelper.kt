package com.praveen.studio.photovideo.url.domain.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.praveen.studio.photovideo.url.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExtractedApkResult(
    val uploadUri: Uri,
    val savedFileName: String,
    val fileSize: Long,
    val downloadLocationDescription: String
)

object ApkExtractorHelper {
    private const val TAG = "ApkExtractorHelper"

    /**
     * Retrieves installed apps that are worth sending: apps the user installed plus updated
     * system apps (Chrome, YouTube...). Pure built-in system apps are hidden.
     */
    suspend fun getInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getInstalledPackages(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get installed packages: ${e.message}")
            emptyList()
        }

        val appList = mutableListOf<InstalledAppInfo>()
        for (pkg in packages) {
            try {
                val appInfo = pkg.applicationInfo ?: continue
                val sourceDir = appInfo.sourceDir ?: continue
                val file = File(sourceDir)
                if (!file.exists() || !file.canRead() || file.length() <= 0) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                // Hide built-in Android apps (Settings, Calculator, Clock...). They are useless to send.
                // Keep apps the user installed, and updated system apps such as Chrome or YouTube.
                if (isSystem && !isUpdatedSystem) continue
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }

                val versionName = pkg.versionName ?: "1.0"

                appList.add(
                    InstalledAppInfo(
                        appName = appName,
                        packageName = pkg.packageName,
                        versionName = versionName,
                        sourceDir = sourceDir,
                        sizeBytes = file.length(),
                        isSystemApp = false,
                        // icons are loaded lazily by the list (decoding every icon here made the dialog slow)
                        icon = null,
                        splitSourceDirs = appInfo.splitSourceDirs?.toList().orEmpty()
                    )
                )
            } catch (_: Exception) {
                // Skip inaccessible or corrupted packages
            }
        }

        appList.sortedWith(
            compareBy<InstalledAppInfo> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * Prepares the installed app for upload (staged copy in the app cache) and, when the device allows it,
     * also saves a copy into the public Downloads folder.
     *
     * Apps installed from an App Bundle consist of several APKs (base + split_*.apk). The base APK alone
     * cannot be installed, so those apps are exported as one ".apks" archive containing every part.
     */
    suspend fun extractApkToDownloads(
        context: Context,
        appInfo: InstalledAppInfo
    ): Result<ExtractedApkResult> = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(appInfo.sourceDir)
            if (!srcFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Source APK not found at ${appInfo.sourceDir}"))
            }
            val splitFiles = appInfo.splitSourceDirs.map { File(it) }.filter { it.exists() && it.canRead() }
            val isBundle = splitFiles.isNotEmpty()

            val sanitizedAppName = appInfo.appName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(24)
            val cleanVersion = appInfo.versionName.replace(Regex("[^a-zA-Z0-9_\\-\\.]"), "_").take(12)
            val fileName = "${sanitizedAppName}_v${cleanVersion}." + if (isBundle) "apks" else "apk"
            val mimeType = if (isBundle) "application/zip" else APK_MIME

            // 1. Staged copy in cacheDir: this is the file that gets uploaded
            val cacheDir = File(context.cacheDir, "extracted_apks")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val stagedFile = File(cacheDir, fileName)
            if (isBundle) {
                writeBundle(srcFile, splitFiles, stagedFile)
            } else {
                FileInputStream(srcFile).use { inStream ->
                    FileOutputStream(stagedFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }

            // 2. Extra copy in the public Downloads folder. Only a convenience: if the device does not allow it
            //    (Android 9 without storage permission, full storage...) the upload still works.
            val savedInDownloads = try {
                copyToDownloads(context, stagedFile, fileName, mimeType)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not copy to Downloads: ${t.message}")
                false
            }

            val parts = if (isBundle) " (base + ${splitFiles.size} split APKs)" else ""
            val desc = if (savedInDownloads) {
                "Saved to Downloads folder: $fileName$parts"
            } else {
                "Prepared $fileName$parts for upload"
            }

            Result.success(
                ExtractedApkResult(
                    uploadUri = Uri.fromFile(stagedFile),
                    savedFileName = fileName,
                    fileSize = stagedFile.length(),
                    downloadLocationDescription = desc
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun writeBundle(base: File, splits: List<File>, target: File) {
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION) // APKs are already compressed
            addZipEntry(zip, "base.apk", base)
            splits.forEach { addZipEntry(zip, it.name, it) }
        }
    }

    private fun addZipEntry(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Returns true when the copy was written, false when the device does not allow writing to Downloads. */
    private fun copyToDownloads(context: Context, source: File, fileName: String, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                resolver.openOutputStream(downloadUri)?.use { outStream ->
                    FileInputStream(source).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: throw IOException("Could not open the Downloads output stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(downloadUri, values, null, null)
                return true
            } catch (t: Throwable) {
                try {
                    resolver.delete(downloadUri, null, null)
                } catch (_: Throwable) {
                }
                throw t
            }
        }

        // Android 9 and below: the public Downloads folder needs WRITE_EXTERNAL_STORAGE, which this app does
        // not ask for. Without it, skip the extra copy instead of failing the whole extraction.
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destFile = File(downloadsDir, fileName)
        FileInputStream(source).use { inStream ->
            FileOutputStream(destFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return true
    }
}
