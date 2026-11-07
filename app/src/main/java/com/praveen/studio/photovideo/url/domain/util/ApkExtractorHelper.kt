package com.praveen.studio.photovideo.url.domain.util

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.praveen.studio.photovideo.url.data.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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

                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    null
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
                        icon = icon
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

    /**
     * Extracts the installed app APK and saves it into the device's public Downloads directory,
     * as well as preparing a cache Uri for immediate Supabase upload.
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

            val sanitizedAppName = appInfo.appName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(24)
            val cleanVersion = appInfo.versionName.replace(Regex("[^a-zA-Z0-9_\\-\\.]"), "_").take(12)
            val fileName = "${sanitizedAppName}_v${cleanVersion}.apk"

            // 1. Save to device's public Downloads directory
            var savedInDownloads = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { outStream ->
                        FileInputStream(srcFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(downloadUri, values, null, null)
                    savedInDownloads = true
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                FileInputStream(srcFile).use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                savedInDownloads = true
            }

            // 2. Save a staged copy in cacheDir for guaranteed upload stream reading
            val cacheDir = File(context.cacheDir, "extracted_apks")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val stagedFile = File(cacheDir, fileName)
            FileInputStream(srcFile).use { inStream ->
                FileOutputStream(stagedFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            val uploadUri = Uri.fromFile(stagedFile)
            val desc = if (savedInDownloads) "Saved to Downloads folder ($fileName)" else "Extracted $fileName"

            Result.success(
                ExtractedApkResult(
                    uploadUri = uploadUri,
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
}
