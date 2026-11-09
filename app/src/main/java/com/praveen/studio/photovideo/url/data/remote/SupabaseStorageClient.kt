package com.praveen.studio.photovideo.url.data.remote

import android.util.Log
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of checking whether a bucket serves its files on public URLs. */
enum class PublicAccess { PUBLIC, NOT_PUBLIC, UNKNOWN }

/**
 * Suspends until the HTTP call finishes and cancels the call (aborting the transfer) when the coroutine
 * is cancelled. A plain execute() would keep uploading after the user left the screen.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    val call = this
    cont.invokeOnCancellation {
        try {
            call.cancel()
        } catch (_: Throwable) {
        }
    }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

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

            val response = okHttpClient.newCall(request).await()
            val code = response.code
            val body = response.use { it.body?.string() ?: "" }

            when (code) {
                200 -> {
                    // The anon key usually cannot list buckets, so also check that files of the bucket are
                    // served on public URLs. Without that the website cannot show the uploaded files.
                    val access = checkPublicAccess("${config.publicUrlEndpoint}/__ivu_probe__")
                    if (access == PublicAccess.NOT_PUBLIC) {
                        return@withContext Result.failure(
                            IllegalStateException(
                                "Bucket '${config.bucketName.trim()}' is not public (or the name is wrong). " +
                                    "In Supabase open Storage > your bucket > Edit and turn ON \"Public bucket\". " +
                                    "Without this the links will not open on the website."
                            )
                        )
                    }

                    val found = body.contains("\"name\":\"${config.bucketName}\"") ||
                        body.contains("\"id\":\"${config.bucketName}\"")
                    val message = StringBuilder(
                        if (found) "Connected successfully! Bucket '${config.bucketName}' found."
                        else "Connected successfully to Supabase!"
                    )
                    if (access == PublicAccess.PUBLIC) {
                        message.append(" Bucket is public.")
                    }
                    if (!config.isWebCompatible) {
                        message.append(
                            "\nNote: the website only opens files from https://<project>.supabase.co projects."
                        )
                    }
                    if (config.isServiceRoleKey) {
                        message.append("\nWarning: this is a service_role key. Use the public anon key instead.")
                    }
                    Result.success(message.toString())
                }
                401 -> Result.failure(IllegalStateException("Invalid Anon Key (401 Unauthorized). Check your API keys."))
                403 -> Result.failure(IllegalStateException("Access forbidden (403). Check your Supabase storage policies."))
                404 -> Result.failure(IllegalStateException("Storage API endpoint not found (404). Verify your Project URL."))
                else -> Result.failure(IllegalStateException("Supabase returned HTTP $code: $body"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Connection test notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Checks whether a URL under the public endpoint is served. Supabase answers "Bucket not found" on public
     * URLs of private buckets; a missing object in a public bucket gives "Object not found" instead.
     * Only that definite answer is reported as [PublicAccess.NOT_PUBLIC] - network problems give UNKNOWN.
     */
    suspend fun checkPublicAccess(url: String): PublicAccess = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            okHttpClient.newCall(request).await().use { response ->
                val code = response.code
                when {
                    code in 200..299 -> PublicAccess.PUBLIC
                    code == 400 || code == 401 || code == 403 || code == 404 -> {
                        val text = try {
                            response.body?.string().orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                        if (text.contains("bucket not found", ignoreCase = true)) PublicAccess.NOT_PUBLIC
                        else PublicAccess.PUBLIC
                    }
                    else -> PublicAccess.UNKNOWN
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Public access check notice: ${e.message}")
            PublicAccess.UNKNOWN
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

            val response = okHttpClient.newCall(request).await()
            val responseCode = response.code
            val responseOk = response.isSuccessful
            val responseBody = response.use { it.body?.string() ?: "" }

            if (responseOk) {
                val publicUrl = "${config.publicUrlEndpoint}/$storagePath"
                Result.success(
                    UploadResult(
                        storagePath = storagePath,
                        bucket = config.bucketName,
                        publicUrl = publicUrl
                    )
                )
            } else {
                val tooLarge = responseCode == 413 ||
                    responseBody.contains("Payload too large", ignoreCase = true) ||
                    responseBody.contains("exceeded the maximum allowed size", ignoreCase = true)

                val errorMsg = when {
                    tooLarge -> TOO_LARGE_MESSAGE
                    responseCode == 400 -> "Bad Request: $responseBody"
                    responseCode == 401 -> "Unauthorized. Invalid Anon Key or missing token."
                    responseCode == 403 -> "Forbidden. Check Storage RLS policies for bucket '${config.bucketName}'."
                    responseCode == 404 -> "Bucket '${config.bucketName}' not found. Please create it in Supabase dashboard."
                    else -> "Upload error ($responseCode): $responseBody"
                }
                Log.w(TAG, "Supabase HTTP response: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
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
