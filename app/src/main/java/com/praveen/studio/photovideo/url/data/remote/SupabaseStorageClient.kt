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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of checking whether a bucket serves its files on public URLs. */
enum class PublicAccess { PUBLIC, NOT_PUBLIC, UNKNOWN }

/** One line of the Supabase setup check (connection, public bucket, upload / read / delete permission). */
enum class CheckState { OK, FAIL, UNKNOWN }

data class SetupCheckItem(val title: String, val state: CheckState, val detail: String)

/** Real size of a bucket as listed by Supabase ("Scan storage"). */
data class BucketUsage(
    val totalBytes: Long,
    val fileCount: Int,
    /** Bytes per top-level folder (images, videos, documents, files, apks, trial, ...). */
    val bytesByFolder: Map<String, Long>,
    /** True when the scan stopped at the file limit, so the real size can be bigger. */
    val truncated: Boolean
)

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
        /** Error messages that start with this text mean "this Supabase project has no free storage left". */
        const val STORAGE_FULL_PREFIX = "Storage full"
        private const val STORAGE_FULL_MESSAGE =
            "$STORAGE_FULL_PREFIX: this Supabase project has no free space left."
        private const val CHECK_FOLDER = "_ivu_check"
        private const val SCAN_PAGE_SIZE = 100
        private const val MAX_SCAN_FILES = 20_000

        const val DELETE_POLICY_HINT =
            "Supabase did not allow deleting the file. Open Settings > Supabase > Set up Supabase, run the " +
                "script shown there again in the Supabase SQL Editor, then try again."
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
     * Deletes one file from Supabase Storage. Needs a "delete" policy for the anon role on storage.objects
     * (see README). Returns success when the file is gone (also when it was already missing).
     *
     * [verifyUrl] is the public URL of the file: Supabase answers "not found" both for a missing file and for a
     * missing delete policy, so a file that is still reachable after a "not found" is reported as a policy problem.
     */
    suspend fun deleteObject(
        config: SupabaseConfig,
        bucket: String,
        path: String,
        verifyUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase URL or Anon Key is missing."))
        }
        try {
            fun enc(part: String) = URLEncoder.encode(part, "UTF-8").replace("+", "%20")
            val encodedPath = path.split('/').joinToString("/") { enc(it) }
            val url = "${config.normalizedUrl}/storage/v1/object/${enc(bucket.trim())}/$encodedPath"
            val key = config.anonKey.trim()
            val request = Request.Builder()
                .url(url)
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).await()
            val code = response.code
            val body = response.use { it.body?.string().orEmpty() }

            when {
                code in 200..299 -> Result.success(Unit)
                code == 404 -> {
                    val stillThere = !verifyUrl.isNullOrBlank() && isReachable(verifyUrl)
                    if (stillThere) Result.failure(IllegalStateException(DELETE_POLICY_HINT))
                    else Result.success(Unit)
                }
                code == 401 || code == 403 ||
                    body.contains("row-level security", ignoreCase = true) ->
                    Result.failure(IllegalStateException(DELETE_POLICY_HINT))
                else -> Result.failure(IllegalStateException("Supabase delete error ($code): ${body.take(160)}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Delete request notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Deletes EVERY file below [prefix] (a published website is a folder "web/<id>/" with many files).
     * Returns how many files were removed. Uses the same Storage policies as [deleteObject].
     */
    suspend fun deleteFolder(config: SupabaseConfig, bucket: String, prefix: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase URL or Anon Key is missing."))
        }
        try {
            fun enc(part: String) = URLEncoder.encode(part, "UTF-8").replace("+", "%20")
            val key = config.anonKey.trim()
            val bucketEnc = enc(bucket.trim())
            val json = "application/json".toMediaTypeOrNull()
            val paths = ArrayList<String>()

            suspend fun walk(folder: String, depth: Int) {
                if (depth > 6 || paths.size > 3000) return
                var offset = 0
                while (true) {
                    val body = JSONObject().put("prefix", folder).put("limit", 1000).put("offset", offset).toString()
                    val request = Request.Builder()
                        .url("${config.normalizedUrl}/storage/v1/object/list/$bucketEnc")
                        .header("apikey", key)
                        .header("Authorization", "Bearer $key")
                        .post(body.toRequestBody(json))
                        .build()
                    val response = okHttpClient.newCall(request).await()
                    val code = response.code
                    val text = response.use { it.body?.string().orEmpty() }
                    if (code == 401 || code == 403) throw IllegalStateException(DELETE_POLICY_HINT)
                    if (code !in 200..299) throw IllegalStateException("Supabase list error ($code): ${text.take(120)}")
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val name = item.optString("name")
                        if (name.isEmpty()) continue
                        val full = "$folder/$name"
                        if (item.isNull("id")) walk(full, depth + 1) else paths += full
                    }
                    if (array.length() < 1000) break
                    offset += 1000
                }
            }

            walk(prefix.trim('/'), 0)
            if (paths.isEmpty()) return@withContext Result.success(0)

            for (chunk in paths.chunked(100)) {
                val list = JSONArray()
                chunk.forEach { list.put(it) }
                val body = JSONObject().put("prefixes", list).toString()
                val request = Request.Builder()
                    .url("${config.normalizedUrl}/storage/v1/object/$bucketEnc")
                    .header("apikey", key)
                    .header("Authorization", "Bearer $key")
                    .delete(body.toRequestBody(json))
                    .build()
                val response = okHttpClient.newCall(request).await()
                val code = response.code
                val text = response.use { it.body?.string().orEmpty() }
                if (code == 401 || code == 403 || text.contains("row-level security", ignoreCase = true)) {
                    throw IllegalStateException(DELETE_POLICY_HINT)
                }
                if (code !in 200..299) throw IllegalStateException("Supabase delete error ($code): ${text.take(120)}")
            }
            Result.success(paths.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Folder delete notice: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun isReachable(url: String): Boolean {
        return try {
            val request = Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
            okHttpClient.newCall(request).await().use { it.code in 200..299 }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
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
                    isStorageFull(responseCode, responseBody) -> STORAGE_FULL_MESSAGE
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

    /** Supabase answers differently for a full project; these are the signs that mean "no space left". */
    private fun isStorageFull(code: Int, body: String): Boolean {
        if (code == 402 || code == 507) return true
        val text = body.lowercase()
        return text.contains("quota") ||
            text.contains("insufficient storage") ||
            text.contains("storage limit") ||
            text.contains("no space left") ||
            text.contains("exceeded the storage")
    }

    /** Sends a request and returns (HTTP code, body). The code is -1 when there was no answer at all. */
    private suspend fun rawCall(request: Request): Pair<Int, String> {
        return try {
            okHttpClient.newCall(request).await().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            -1 to (e.message ?: "no answer")
        }
    }

    /**
     * Full setup check of one Supabase project: connection + key, public bucket, upload permission, read permission
     * and delete permission. The upload / read / delete checks use one tiny file (_ivu_check/probe_*.txt) that is
     * deleted again at once.
     */
    suspend fun runSetupCheck(config: SupabaseConfig): List<SetupCheckItem> = withContext(Dispatchers.IO) {
        val items = ArrayList<SetupCheckItem>()
        if (!config.isConfigured) {
            items += SetupCheckItem("Project URL and key", CheckState.FAIL, "Enter the Project URL and the anon key first.")
            return@withContext items
        }
        val key = config.anonKey.trim()
        val bucket = config.bucketName.trim()

        // 1. connection + key
        val ping = rawCall(
            Request.Builder()
                .url("${config.normalizedUrl}/storage/v1/bucket")
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .get()
                .build()
        )
        val connectionDetail = when (ping.first) {
            200 -> null
            401 -> "Invalid anon key (401). Copy the anon public key again."
            403 -> "Access forbidden (403). Check the key."
            404 -> "Storage API not found (404). Check the Project URL."
            -1 -> "No answer. Check the internet connection and the Project URL."
            else -> "Supabase returned HTTP ${ping.first}."
        }
        if (connectionDetail != null) {
            items += SetupCheckItem("Connection and key", CheckState.FAIL, connectionDetail)
            val skipped = "Needs a working connection first."
            items += SetupCheckItem("Public bucket", CheckState.UNKNOWN, skipped)
            items += SetupCheckItem("Upload permission (INSERT)", CheckState.UNKNOWN, skipped)
            items += SetupCheckItem("Read permission (SELECT)", CheckState.UNKNOWN, skipped)
            items += SetupCheckItem("Delete permission (DELETE)", CheckState.UNKNOWN, skipped)
            return@withContext items
        }
        val keyNote = if (config.isServiceRoleKey) " Warning: this is a service_role key, use the public anon key." else ""
        items += SetupCheckItem("Connection and key", CheckState.OK, "Connected to ${config.normalizedUrl}.$keyNote")

        // 2. public bucket
        when (checkPublicAccess("${config.publicUrlEndpoint}/__ivu_probe__")) {
            PublicAccess.PUBLIC -> items += SetupCheckItem("Public bucket", CheckState.OK, "Bucket '$bucket' serves public links.")
            PublicAccess.NOT_PUBLIC -> items += SetupCheckItem(
                "Public bucket", CheckState.FAIL,
                "Bucket '$bucket' is missing or not public. Create it / turn ON \"Public bucket\" (the SQL below does it)."
            )
            PublicAccess.UNKNOWN -> items += SetupCheckItem("Public bucket", CheckState.UNKNOWN, "Could not be checked (network).")
        }

        // 3. upload permission (tiny probe file)
        val probeName = "probe_${System.currentTimeMillis()}.txt"
        val probePath = "$CHECK_FOLDER/$probeName"
        val upload = rawCall(
            Request.Builder()
                .url("${config.uploadEndpoint}/$probePath")
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "text/plain")
                .header("x-upsert", "false")
                .post("ok".toRequestBody("text/plain".toMediaTypeOrNull()))
                .build()
        )
        val uploaded = upload.first in 200..299
        if (uploaded) {
            items += SetupCheckItem("Upload permission (INSERT)", CheckState.OK, "Uploads are allowed.")
        } else {
            val reason = when {
                upload.first == 404 -> "Bucket '$bucket' not found. Create it (the SQL below does it)."
                isStorageFull(upload.first, upload.second) -> STORAGE_FULL_MESSAGE
                else -> "Uploads are blocked (HTTP ${upload.first}). Run the INSERT policy from the SQL below."
            }
            items += SetupCheckItem("Upload permission (INSERT)", CheckState.FAIL, reason)
            items += SetupCheckItem("Read permission (SELECT)", CheckState.UNKNOWN, "Needs a working upload first.")
            items += SetupCheckItem("Delete permission (DELETE)", CheckState.UNKNOWN, "Needs a working upload first.")
            return@withContext items
        }

        // 4. read permission: the probe file must show up in the list
        val listBody = JSONObject()
            .put("prefix", CHECK_FOLDER)
            .put("limit", 20)
            .put("offset", 0)
            .toString()
        val listed = rawCall(
            Request.Builder()
                .url("${config.normalizedUrl}/storage/v1/object/list/${SupabaseConfig.encodePathSegment(bucket)}")
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .post(listBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
        )
        val seen = listed.first in 200..299 && listed.second.contains(probeName)
        items += if (seen) {
            SetupCheckItem("Read permission (SELECT)", CheckState.OK, "Files can be listed (needed for Scan storage and delete).")
        } else {
            SetupCheckItem("Read permission (SELECT)", CheckState.FAIL, "Files cannot be listed. Run the SELECT policy from the SQL below.")
        }

        // 5. delete permission (also removes the probe file)
        val deleted = deleteObject(config, bucket, probePath, "${config.publicUrlEndpoint}/$probePath")
        items += if (deleted.isSuccess) {
            SetupCheckItem("Delete permission (DELETE)", CheckState.OK, "Deleting from History also deletes the file here.")
        } else {
            SetupCheckItem("Delete permission (DELETE)", CheckState.FAIL, "Deleting is blocked. Run the DELETE policy from the SQL below.")
        }
        items
    }

    /**
     * Lists the bucket and adds up the size of every file (needs the SELECT permission). Up to [MAX_SCAN_FILES]
     * files are counted; [onProgress] gets the number of files found so far.
     */
    suspend fun scanBucketUsage(
        config: SupabaseConfig,
        onProgress: (filesFound: Int) -> Unit
    ): Result<BucketUsage> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase URL or Anon Key is missing."))
        }
        try {
            val key = config.anonKey.trim()
            val url = "${config.normalizedUrl}/storage/v1/object/list/${SupabaseConfig.encodePathSegment(config.bucketName.trim())}"
            var total = 0L
            var count = 0
            var truncated = false
            val byFolder = LinkedHashMap<String, Long>()
            val pending = ArrayDeque<String>()
            pending.add("")

            while (pending.isNotEmpty() && !truncated) {
                val prefix = pending.removeFirst()
                var offset = 0
                while (true) {
                    val body = JSONObject()
                        .put("prefix", prefix)
                        .put("limit", SCAN_PAGE_SIZE)
                        .put("offset", offset)
                        .put("sortBy", JSONObject().put("column", "name").put("order", "asc"))
                        .toString()
                    val (code, text) = rawCall(
                        Request.Builder()
                            .url(url)
                            .header("apikey", key)
                            .header("Authorization", "Bearer $key")
                            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()
                    )
                    if (code !in 200..299) {
                        val reason = when (code) {
                            -1 -> "No answer from Supabase. Check your internet connection."
                            400, 401, 403 -> "Supabase did not allow listing files. Run the SELECT policy from the setup SQL."
                            404 -> "Bucket '${config.bucketName.trim()}' not found."
                            else -> "Supabase returned HTTP $code."
                        }
                        return@withContext Result.failure(IllegalStateException(reason))
                    }
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val name = obj.optString("name")
                        if (name.isBlank()) continue
                        if (obj.isNull("id")) {
                            // a folder
                            pending.add(if (prefix.isEmpty()) name else "$prefix/$name")
                        } else {
                            val size = obj.optJSONObject("metadata")?.optLong("size", 0L) ?: 0L
                            total += size
                            count++
                            val top = if (prefix.isEmpty()) "(root)" else prefix.substringBefore('/')
                            byFolder[top] = (byFolder[top] ?: 0L) + size
                        }
                    }
                    onProgress(count)
                    if (array.length() < SCAN_PAGE_SIZE) break
                    offset += array.length()
                    if (count >= MAX_SCAN_FILES) {
                        truncated = true
                        break
                    }
                }
            }
            Result.success(BucketUsage(total, count, byFolder, truncated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Scan notice: ${e.message}")
            Result.failure(e)
        }
    }

    data class UploadResult(
        val storagePath: String,
        val bucket: String,
        val publicUrl: String
    )
}
