package com.praveen.studio.photovideo.url.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.domain.repository.UploadRepository
import kotlinx.coroutines.flow.last

class MediaUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val uploadRepository = UploadRepository(appContext)

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "file"
        val categoryName = inputData.getString(KEY_CATEGORY) ?: MediaCategory.FILES.name
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val sizeBytes = inputData.getLong(KEY_SIZE_BYTES, 0L)

        val category = try {
            MediaCategory.valueOf(categoryName)
        } catch (_: Exception) {
            MediaCategory.FILES
        }

        val item = SelectedMediaItem(
            uri = Uri.parse(uriString),
            name = fileName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            category = category
        )

        return try {
            val finalStatus = uploadRepository.uploadItem(item).last()
            when (finalStatus) {
                is UploadStatus.Success -> {
                    Result.success(
                        workDataOf(
                            KEY_RESULT_SHORT_ID to finalStatus.record.shortId,
                            KEY_RESULT_PUBLIC_URL to finalStatus.record.publicUrl
                        )
                    )
                }
                is UploadStatus.Failed -> {
                    if (finalStatus.retryable && runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure(workDataOf(KEY_RESULT_ERROR to finalStatus.error))
                    }
                }
                else -> Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_URI = "key_uri"
        const val KEY_FILE_NAME = "key_file_name"
        const val KEY_CATEGORY = "key_category"
        const val KEY_MIME_TYPE = "key_mime_type"
        const val KEY_SIZE_BYTES = "key_size_bytes"
        const val KEY_RESULT_SHORT_ID = "key_result_short_id"
        const val KEY_RESULT_PUBLIC_URL = "key_result_public_url"
        const val KEY_RESULT_ERROR = "key_result_error"
    }
}
