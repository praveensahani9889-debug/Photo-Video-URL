package com.praveen.studio.photovideo.url.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

enum class MediaCategory(
    val title: String,
    val description: String,
    val folderPath: String,
    val shortIdPrefix: String,
    val mimeTypeFilter: String,
    val allowedExtensions: List<String>
) {
    IMAGES(
        title = "Images",
        description = "PNG, JPG, WEBP, GIF, SVG",
        folderPath = "images",
        shortIdPrefix = "image_",
        mimeTypeFilter = "image/*",
        allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp")
    ),
    VIDEOS(
        title = "Videos",
        description = "MP4, MKV, MOV, WEBM",
        folderPath = "videos",
        shortIdPrefix = "video_",
        mimeTypeFilter = "video/*",
        allowedExtensions = listOf("mp4", "mkv", "mov", "webm", "avi", "3gp")
    ),
    DOCUMENTS(
        title = "Documents",
        description = "PDF, DOCX, TXT, XLS, PPT",
        folderPath = "documents",
        shortIdPrefix = "document_",
        mimeTypeFilter = "application/pdf",
        allowedExtensions = listOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv")
    ),
    FILES(
        title = "Files",
        description = "ZIP, TAR, 7Z, ISO, generic",
        folderPath = "files",
        shortIdPrefix = "file_",
        mimeTypeFilter = "*/*",
        allowedExtensions = listOf("zip", "rar", "7z", "tar", "gz", "bin", "iso", "json", "xml")
    ),
    APKS(
        title = "APKs",
        description = "Android package files (.apk)",
        folderPath = "apks",
        shortIdPrefix = "apk_",
        mimeTypeFilter = "application/vnd.android.package-archive",
        allowedExtensions = listOf("apk", "xapk", "apks")
    );

    fun getIcon(): ImageVector = when (this) {
        IMAGES -> Icons.Default.Image
        VIDEOS -> Icons.Default.Videocam
        DOCUMENTS -> Icons.Default.Description
        FILES -> Icons.Default.FolderZip
        APKS -> Icons.Default.Android
    }

    companion object {
        fun fromMimeAndExtension(mimeType: String?, fileName: String?): MediaCategory {
            val lowerMime = mimeType?.lowercase().orEmpty()
            val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()

            return when {
                lowerMime.startsWith("image/") || ext in IMAGES.allowedExtensions -> IMAGES
                lowerMime.startsWith("video/") || ext in VIDEOS.allowedExtensions -> VIDEOS
                lowerMime == "application/vnd.android.package-archive" || ext in APKS.allowedExtensions -> APKS
                ext in DOCUMENTS.allowedExtensions ||
                    lowerMime in listOf("application/pdf", "text/plain", "application/msword") ||
                    lowerMime.contains("officedocument") ||
                    lowerMime.contains("text/") -> DOCUMENTS
                ext in FILES.allowedExtensions ||
                    lowerMime.contains("zip") ||
                    lowerMime.contains("tar") ||
                    lowerMime.contains("archive") ||
                    lowerMime.contains("compressed") -> FILES
                else -> FILES
            }
        }
    }
}
