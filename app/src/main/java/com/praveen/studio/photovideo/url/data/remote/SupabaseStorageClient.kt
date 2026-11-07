package com.praveen.studio.photovideo.url.data.remote

import android.util.Log
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class SupabaseStorageClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SupabaseStorageClient"

        /** Error messages that start with this text mean "file is bigger than the Supabase limit". */
        const val TOO_LARGE_PREFIX = "File too large"
        private const val TOO_LARGE_MESSAGE =
            "$TOO_LARGE_PREFIX for your Supabase storage limit (Free plan: 50 MB per file). " +
                "Raise \"Global file size limit\" in Supabase > Storage > Settings (Pro plan), or upload a smaller file."
    }

    suspend fun testConnection(config: SupabaseConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalArgumentException("Supabase URL or Anon Key is missing."))
        }

        try {
            val url = "${config.normalizedUrl}/storage/v1/bucket"
            val request = Request.Builder()
                .url(url)
                .header("apikey", config.anonKey.trim())
                .header("Authorization", "Bearer ${config.anonKey.trim()}")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            when (response.code) {
                200 -> {
                    // Check if bucket is present or verify access
                    if (body.contains("\"name\":\"${config.bucketName}\"") || body.contains("\"id\":\"${config.bucketName}\"")) {
                        Result.success("Connected successfully! Bucket '${config.bucketName}' found.")
                    } else {
                        Result.success("Connected successfully to Supabase! (Bucket list accessible)")
                    }
                }
                401 -> Result.failure(IllegalStateException("Invalid Anon Key (401 Unauthorized). Check your API keys."))
                403 -> Result.failure(IllegalStateException("Access forbidden (403). Check your Supabase storage policies."))
                404 -> Result.failure(IllegalStateException("Storage API endpoint not found (404). Verify your Project URL."))
                else -> Result.failure(IllegalStateException("Supabase returned HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection test notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Uploads a file to Supabase Storage.
     *
     * [openStream] is called every time the body is written, so the file is streamed from disk
     * and never fully loaded into memory. If [totalBytes] is unknown (<= 0) the file is read into
     * memory as a fallback.
     */
    suspend fun uploadFile(
        config: SupabaseConfig,
        openStream: () -> InputStream?,
        cleanFileName: String,
        folderPath: String,
        mimeType: String,
        totalBytes: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long, speedKBps: Double) -> Unit
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }
        if (config.isPlaceholder) {
            return@withContext Result.failure(IllegalStateException("Default starter configuration detected. Cloud project credentials required for direct network upload."))
        }

        try {
            val storagePath = "$folderPath/$cleanFileName"
            val uploadUrl = "${config.uploadEndpoint}/$storagePath"

            val mediaType = mimeType.ifBlank { "application/octet-stream" }.toMediaTypeOrNull()
            val baseBody: RequestBody = if (totalBytes > 0) {
                StreamingRequestBody(openStream, mediaType, totalBytes)
            } else {
                val bytes = (openStream() ?: throw IOException("Unable to open file for upload"))
                    .use { it.readBytes() }
                bytes.toRequestBody(mediaType)
            }
            val countingBody = CountingRequestBody(baseBody, onProgress)

            val request = Request.Builder()
                .url(uploadUrl)
                .header("apikey", config.anonKey.trim())
                .header("Authorization", "Bearer ${config.anonKey.trim()}")
                .header("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                .header("x-upsert", "true")
                .post(countingBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val publicUrl = "${config.publicUrlEndpoint}/$storagePath"
                Result.success(
                    UploadResult(
                        storagePath = storagePath,
                        bucket = config.bucketName,
                        publicUrl = publicUrl
                    )
                )
            } else {
                val tooLarge = response.code == 413 ||
                    responseBody.contains("Payload too large", ignoreCase = true) ||
                    responseBody.contains("exceeded the maximum allowed size", ignoreCase = true)

                val errorMsg = when {
                    tooLarge -> TOO_LARGE_MESSAGE
                    response.code == 400 -> "Bad Request: $responseBody"
                    response.code == 401 -> "Unauthorized. Invalid Anon Key or missing token."
                    response.code == 403 -> "Forbidden. Check Storage RLS policies for bucket '${config.bucketName}'."
                    response.code == 404 -> "Bucket '${config.bucketName}' not found. Please create it in Supabase dashboard."
                    else -> "Upload error (${response.code}): $responseBody"
                }
                Log.w(TAG, "Supabase HTTP response: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.w(TAG, "File upload request notice: ${e.message}")
            Result.failure(e)
        }
    }

    data class UploadResult(
        val storagePath: String,
        val bucket: String,
        val publicUrl: String
    )
}
