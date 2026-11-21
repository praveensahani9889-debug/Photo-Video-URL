package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import android.util.Log
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.RecoveryStore
import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SelectedMediaItem
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UploadStatus
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.remote.HistoryRemoteStore
import com.praveen.studio.photovideo.url.data.remote.IdentityManager
import com.praveen.studio.photovideo.url.data.remote.PublicAccess
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import com.praveen.studio.photovideo.url.data.remote.UserDetailsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UploadRepository(
    private val context: Context,
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
        // Counters / free-trial numbers saved on the phone show at once; Firebase is only asked when they are stale.
        // (The default app/backend + app/about nodes are checked together with the app switches at app start.)
        UserDetailsStore.load(context, installationId)
        SupabaseAccountManager.load(context)
    }

    /**
     * The user's OWN Supabase (saved in Settings; the active one when there are several). Used for every normal
     * upload (photos, videos, documents, files, APKs). Returns null until the user finishes setup.
     */
    fun getUserSupabaseConfig(): SupabaseConfig? = SupabaseAccountManager.activeConfig(context)

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

    /** Counters and free-trial numbers. Firebase is asked only when the saved copy is stale. */
    suspend fun getUserDetails(): UserDetails {
        refreshUserDetails()
        return UserDetailsStore.state.value
    }

    /** The saved copy, updated after every upload (no live listener on Firebase any more). */
    fun observeUserDetails(): Flow<UserDetails> = UserDetailsStore.state

    suspend fun refreshUserDetails(force: Boolean = false) {
        UserDetailsStore.refresh(context, installationId, firebaseDbManager, force)
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
        var activeAccount: SupabaseAccount? = null
        var activeConfig: SupabaseConfig = if (item.isTrial) {
            if (item.category != MediaCategory.IMAGES) {
                send(UploadStatus.Failed(item.name, "The free trial is for photos only. Set up your own Supabase to upload other files.", retryable = false))
                return@channelFlow
            }
            if (!AppControlStore.current.demoEnabled) {
                send(UploadStatus.Failed(item.name, "The free demo is switched off right now. Set up your own Supabase in Settings to upload.", retryable = false))
                return@channelFlow
            }
            val details = firebaseDbManager.fetchUserDetailsOrNull(installationId)
            if (details == null) {
                send(UploadStatus.Failed(item.name, "Could not check your free trial. Check your internet connection and try again."))
                return@channelFlow
            }
            if (!details.hasFreeUploadsLeft) {
                send(UploadStatus.Failed(item.name, "Your ${details.freeTierMax} free photos are used. Set up your own Supabase to keep uploading.", retryable = false))
                return@channelFlow
            }
            // Free photos are also limited per network, so a phone cannot get new demo photos by resetting itself
            // (the developer can switch this limit off, or change it, in Firebase app/backend: ipLimitEnabled / ipFreeLimit)
            val ipLimit = AppControlStore.current.effectiveIpLimit
            val ipKey = if (ipLimit != null) IdentityManager.getIpKey() else null
            val ipUsed = if (ipKey != null) firebaseDbManager.fetchIpFreeUsed(ipKey) else null
            if (ipLimit != null && ipUsed != null && ipUsed >= ipLimit) {
                send(UploadStatus.Failed(item.name, "The free trial of this network is used up. Set up your own Supabase to keep uploading.", retryable = false))
                return@channelFlow
            }
            val trialConfig = getTrialSupabaseConfig()
            if (trialConfig == null) {
                send(UploadStatus.Failed(item.name, "The free trial is not available right now. Set up your own Supabase in Settings.", retryable = false))
                return@channelFlow
            }
            trialConfig
        } else {
            // the active project; when it is full the next project with room is taken (and the user is told)
            val picked = SupabaseAccountManager.pickForUpload(context, item.sizeBytes)
            if (picked == null) {
                val errorMsg = if (SupabaseAccountManager.hasUsableAccount(context)) {
                    "All your Supabase storages are full. Add another Supabase in Settings or delete some uploads."
                } else {
                    "Supabase is not set up. Open Settings and add your Supabase project URL and anon key."
                }
                Log.w(TAG, errorMsg)
                send(UploadStatus.Failed(item.name, errorMsg, retryable = false))
                return@channelFlow
            }
            activeAccount = picked
            picked.config
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
        suspend fun uploadTo(config: SupabaseConfig) = supabaseClient.uploadFile(
            config = config,
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

        var uploadResult = uploadTo(activeConfig)

        // When an upload fails, the same file is tried again on its own: one quick retry on the same Supabase, then on
        // the other saved Supabase projects one after the other. "Storage full" also marks the project as full and
        // tells the user. Only when every project failed, the error is shown.
        val triedIds = HashSet<String>()
        activeAccount?.let { triedIds.add(it.id) }
        val failures = ArrayList<String>()
        var retriedSame = false
        while (uploadResult.isFailure && !item.isTrial) {
            val message = uploadResult.exceptionOrNull()?.message.orEmpty()
            // a file that is too big fails everywhere in the same way
            if (message.startsWith(SupabaseStorageClient.TOO_LARGE_PREFIX)) break
            val current = activeAccount ?: break
            val full = message.startsWith(SupabaseStorageClient.STORAGE_FULL_PREFIX)

            if (!full && !retriedSame) {
                // maybe only the connection hiccuped: once more on the same project
                retriedSame = true
                delay(1500L)
                send(UploadStatus.Preparing(item.name))
                uploadResult = uploadTo(activeConfig)
                continue
            }

            failures.add("${current.displayName}: ${message.ifBlank { "upload failed" }}")
            val others = SupabaseAccountManager.list(context).filter { it.isUsable && !it.full && it.id !in triedIds }
            val next: SupabaseAccount? = if (full) {
                SupabaseAccountManager.markFullAndPickNext(context, current.id, item.sizeBytes)
                    ?.takeIf { it.id !in triedIds }
                    ?: SupabaseAccountManager.list(context).firstOrNull { it.isUsable && !it.full && it.id !in triedIds }
            } else {
                others.firstOrNull()
            }
            if (next == null) break
            triedIds.add(next.id)
            activeAccount = next
            activeConfig = next.config
            send(UploadStatus.Preparing(item.name))
            uploadResult = uploadTo(activeConfig)
        }

        if (!uploadResult.isSuccess) {
            val err = uploadResult.exceptionOrNull()?.message ?: "Supabase upload failed"
            // A "too large" error can never succeed on retry
            val retryable = !err.startsWith(SupabaseStorageClient.TOO_LARGE_PREFIX)
            val allFull = !item.isTrial && err.startsWith(SupabaseStorageClient.STORAGE_FULL_PREFIX) &&
                SupabaseAccountManager.list(context).none { it.isUsable && !it.full }
            val finalMessage = when {
                allFull -> "All your Supabase storages are full. Add another Supabase in Settings or delete some uploads."
                failures.size > 1 -> "The upload failed on all ${failures.size} Supabase projects. " +
                    failures.joinToString("  |  ") + ". Check the keys and your internet in Settings."
                else -> err
            }
            send(UploadStatus.Failed(item.name, finalMessage, retryable = retryable && !allFull))
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
            // Rules problems do not go away by retrying
            if (FirebaseDatabaseManager.isPermissionDenied(dbResult.exceptionOrNull())) break
            if (attempt < 3) delay(attempt * 1000L)
        }

        if (dbResult.isFailure) {
            val blockedByRules = FirebaseDatabaseManager.isPermissionDenied(dbResult.exceptionOrNull())
            send(
                UploadStatus.Failed(
                    item.name,
                    if (blockedByRules) {
                        "The file is in Supabase, but Firebase refused to save the short link. " +
                            FirebaseDatabaseManager.PERMISSION_DENIED_HINT
                    } else {
                        "The file was uploaded but its short link could not be saved " +
                            "(${dbResult.exceptionOrNull()?.message ?: "unknown error"}). " +
                            "Check your internet connection and try again."
                    },
                    retryable = true
                )
            )
            return@channelFlow
        }

        // Count the free photo for this network too (best effort)
        if (item.isTrial && AppControlStore.current.ipLimitEnabled) {
            try {
                IdentityManager.getIpKey()?.let { firebaseDbManager.incrementIpFreeUsed(it) }
            } catch (e: java.util.concurrent.CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }

        // 5. Save to local cache for instant offline & restart availability
        preferencesManager.addUploadRecord(record)
        // History and counters are updated here, so no screen has to ask Firebase again
        HistoryRemoteStore.upsert(record)
        // encrypted safe copy of the upload list on the phone (quietly skipped when the storage is not allowed)
        RecoveryStore.addUpload(record)
        UserDetailsStore.onUploadSucceeded(context, installationId, item.category, item.isTrial)
        activeAccount?.let { SupabaseAccountManager.recordUpload(context, it.id, item.sizeBytes) }
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
