package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import android.util.Log
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.SecureStorageManager
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.remote.PublicAccess
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UploadRepository(
    private val context: Context,
    private val secureStorageManager: SecureStorageManager = SecureStorageManager(context),
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val supabaseClient: SupabaseStorageClient = SupabaseStorageClient(),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager()
) {
    companion object {
        const val PUBLIC_DOMAIN = "https://ivu.gt.tc"
        private const val TAG = "UploadRepository"
        private const val TRIAL_FOLDER = "trial"
    }

    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    init {
        // Ensure default Firebase 'app/backend' and 'app/about' structure exists
        CoroutineScope(Dispatchers.IO).launch {
            firebaseDbManager.initializeAppMetadataIfNeeded()
        }
    }

    /**
     * The user's OWN Supabase (saved in Settings). Used for every normal upload
     * (photos, videos, documents, files, APKs). Returns null until the user finishes setup.
     */
    fun getUserSupabaseConfig(): SupabaseConfig? {
        val local = secureStorageManager.getConfig()
        return if (local.isConfigured && !local.isPlaceholder) local else null
    }

    /**
     * The developer's Supabase stored manually in Firebase `app/backend`.
     * Used ONLY for the 3 free trial gallery photos. Never used for normal uploads.
     */
    suspend fun getTrialSupabaseConfig(): SupabaseConfig? {
        val backend = firebaseDbManager.fetchAppBackendConfig()
        return if (backend != null && backend.isConfigured && !backend.isPlaceholder) backend else null
    }

    /** True only when the user has set up their own Supabase. */
    fun isSupabaseConfigured(): Boolean = getUserSupabaseConfig() != null

    suspend fun getUserDetails(): UserDetails {
        return firebaseDbManager.fetchUserDetails(installationId)
    }

    fun observeUserDetails(): Flow<UserDetails> {
        return firebaseDbManager.observeUserDetails(installationId)
    }

    fun uploadItem(item: SelectedMediaItem, itemIndex: Int = 1, totalItems: Int = 1): Flow<UploadStatus> = channelFlow {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
        val now = Date()
        val uploadDateStr = dateFormat.format(now)
        val uploadTimeStr = timeFormat.format(now)
        val startTime = System.currentTimeMillis()

        send(UploadStatus.Preparing(item.name))

        // 0. Pick the right Supabase:
        //    - free trial photo  -> developer's Supabase (Firebase app/backend), max 3 per installation
        //    - everything else   -> the user's own Supabase from Settings
        val activeConfig: SupabaseConfig = if (item.isTrial) {
            if (item.category != MediaCategory.IMAGES) {
                send(UploadStatus.Failed(item.name, "The free trial is for photos only. Set up your own Supabase to upload other files.", retryable = false))
                return@channelFlow
            }
            val details = firebaseDbManager.fetchUserDetailsOrNull(installationId)
            if (details == null) {
                send(UploadStatus.Failed(item.name, "Could not check your free trial. Check your internet connection and try again."))
                return@channelFlow
            }
            if (!details.hasFreeUploadsLeft) {
                send(UploadStatus.Failed(item.name, "Your 3 free photos are used. Set up your own Supabase to keep uploading.", retryable = false))
                return@channelFlow
            }
            val trialConfig = getTrialSupabaseConfig()
            if (trialConfig == null) {
                send(UploadStatus.Failed(item.name, "The free trial is not available right now. Set up your own Supabase in Settings.", retryable = false))
                return@channelFlow
            }
            trialConfig
        } else {
            val userConfig = getUserSupabaseConfig()
            if (userConfig == null) {
                val errorMsg = "Supabase is not set up. Open Settings and add your Supabase project URL and anon key."
                Log.w(TAG, errorMsg)
                send(UploadStatus.Failed(item.name, errorMsg, retryable = false))
                return@channelFlow
            }
            userConfig
        }

        // 1. Generate unique short ID with collision check
        val shortId = try {
            firebaseDbManager.generateUniqueShortId(item.category)
        } catch (e: Exception) {
            "${item.category.shortIdPrefix}${UUID.randomUUID().toString().take(7)}"
        }

        val cleanName = sanitizeFileName(item.name, shortId)
        val folderPath = if (item.isTrial) "$TRIAL_FOLDER/${item.category.folderPath}" else item.category.folderPath
        val storagePath = "$folderPath/$cleanName"

        // 2. Make sure the file can be opened (it is streamed again during the upload)
        val openStream: () -> InputStream? = { context.contentResolver.openInputStream(item.uri) }
        val probe: InputStream? = try {
            openStream()
        } catch (e: Exception) {
            send(UploadStatus.Failed(item.name, "Unable to read file: ${e.localizedMessage ?: "File inaccessible"}"))
            return@channelFlow
        }
        if (probe == null) {
            send(UploadStatus.Failed(item.name, "Failed to open input stream for selected file"))
            return@channelFlow
        }
        probe.close()

        // 3. Real network upload to Supabase Storage with live byte progress
        val uploadResult = supabaseClient.uploadFile(
            config = activeConfig,
            openStream = openStream,
            cleanFileName = cleanName,
            folderPath = folderPath,
            mimeType = item.mimeType,
            totalBytes = item.sizeBytes
        ) { bytesWritten, totalBytes, speedKBps ->
            val total = if (totalBytes > 0) totalBytes else item.sizeBytes
            val progress = if (total > 0) (bytesWritten.toFloat() / total).coerceIn(0f, 1f) else 0f
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            trySend(
                UploadStatus.Uploading(
                    fileName = item.name,
                    bytesUploaded = bytesWritten,
                    totalBytes = total,
                    progress = progress,
                    speedKBps = speedKBps,
                    elapsedSeconds = elapsed,
                    currentItemIndex = itemIndex,
                    totalItems = totalItems
                )
            )
        }

        if (!uploadResult.isSuccess) {
            val err = uploadResult.exceptionOrNull()?.message ?: "Supabase upload failed"
            // A "too large" error can never succeed on retry
            val retryable = !err.startsWith(SupabaseStorageClient.TOO_LARGE_PREFIX)
            send(UploadStatus.Failed(item.name, err, retryable = retryable))
            return@channelFlow
        }

        val uploadData = uploadResult.getOrThrow()
        val resolvedSupabaseUrl = uploadData.publicUrl
        val resolvedBucket = uploadData.bucket

        // 3b. A private bucket accepts uploads but the public URL (and so the website) cannot show the file.
        //     Tell the user now instead of handing out a link that never opens.
        if (!item.isTrial) {
            val access = supabaseClient.checkPublicAccess(resolvedSupabaseUrl)
            if (access == PublicAccess.NOT_PUBLIC) {
                send(
                    UploadStatus.Failed(
                        item.name,
                        "The file was uploaded, but bucket '$resolvedBucket' is not public, so the link would not open " +
                            "on the website. In Supabase open Storage > your bucket > Edit, turn ON \"Public bucket\" " +
                            "and upload again.",
                        retryable = false
                    )
                )
                return@channelFlow
            }
        }

        val elapsedDuration = System.currentTimeMillis() - startTime
        send(UploadStatus.RegisteringMetadata(item.name, shortId))

        // 4. Register in Firebase Realtime Database: user/{installationId}/{images|videos|...}/{shortId}
        val publicUrl = "$PUBLIC_DOMAIN/$shortId"
        val record = UploadRecord(
            shortId = shortId,
            ownerInstallationId = installationId,
            fileName = item.name,
            fileType = item.category.title,
            type = item.category.name.lowercase().removeSuffix("s"),
            mimeType = item.mimeType,
            fileSize = item.sizeBytes,
            storageBucket = resolvedBucket,
            storagePath = storagePath,
            originalSupabaseUrl = resolvedSupabaseUrl,
            supabaseUrl = resolvedSupabaseUrl,
            publicUrl = publicUrl,
            shortUrl = publicUrl,
            uploadDate = uploadDateStr,
            uploadTime = uploadTimeStr,
            createdAt = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            uploadDuration = elapsedDuration,
            status = "completed",
            isDemo = false,
            isFreeTier = item.isTrial
        )

        // The short link only works once public_links/{shortId} is saved, so a failure here is a real
        // failure (a few quick retries first for flaky connections).
        var dbResult: Result<Unit> = Result.failure(IllegalStateException("Link was not saved"))
        for (attempt in 1..3) {
            dbResult = firebaseDbManager.registerUpload(
                installationId = installationId,
                record = record,
                category = item.category,
                isFreeTier = item.isTrial
            )
            if (dbResult.isSuccess) break
            Log.w(TAG, "Firebase register attempt $attempt failed: ${dbResult.exceptionOrNull()?.message}")
            if (attempt < 3) delay(attempt * 1000L)
        }

        if (dbResult.isFailure) {
            send(
                UploadStatus.Failed(
                    item.name,
                    "The file was uploaded but its short link could not be saved " +
                        "(${dbResult.exceptionOrNull()?.message ?: "unknown error"}). " +
                        "Check your internet connection and try again.",
                    retryable = true
                )
            )
            return@channelFlow
        }

        // 5. Save to local cache for instant offline & restart availability
        preferencesManager.addUploadRecord(record)
        send(UploadStatus.Success(record, totalItems))
    }.flowOn(Dispatchers.IO)

    private fun sanitizeFileName(originalName: String, shortId: String): String {
        // Only a plain alphanumeric tail counts as extension ("Meeting 10.30 AM" has none). Everything else
        // would end up as spaces/odd characters in the public URL, which the website refuses to open.
        val rawExtension = originalName.substringAfterLast('.', "")
        val hasExtension = rawExtension.matches(Regex("[A-Za-z0-9]{1,10}"))
        val namePart = if (hasExtension) originalName.substringBeforeLast('.') else originalName
        val base = namePart
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(30)
            .ifBlank { "file" }
        return if (hasExtension) "${shortId}_${base}.${rawExtension}" else "${shortId}_${base}"
    }
}
