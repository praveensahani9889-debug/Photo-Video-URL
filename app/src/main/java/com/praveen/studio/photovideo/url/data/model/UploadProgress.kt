package com.praveen.studio.photovideo.url.data.model

sealed interface UploadStatus {
    data object Idle : UploadStatus
    data class Preparing(val fileName: String) : UploadStatus
    data class Compressing(val fileName: String, val progress: Float) : UploadStatus
    data class Uploading(
        val fileName: String,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val progress: Float,
        val speedKBps: Double,
        val elapsedSeconds: Long,
        val currentItemIndex: Int = 1,
        val totalItems: Int = 1
    ) : UploadStatus
    data class RegisteringMetadata(val fileName: String, val shortId: String) : UploadStatus
    data class Success(
        val record: UploadRecord,
        val totalCompleted: Int = 1
    ) : UploadStatus
    data class Failed(
        val fileName: String,
        val error: String,
        val retryable: Boolean = true
    ) : UploadStatus
}
