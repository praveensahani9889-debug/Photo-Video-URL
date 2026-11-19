package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.remote.HistoryRemoteStore
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Result of deleting uploads: how many are gone, how many failed and why. */
data class DeleteOutcome(
    val deleted: Int,
    val failed: Int,
    val messages: List<String>
)

class HistoryRepository(
    context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager(),
    private val supabaseClient: SupabaseStorageClient = SupabaseStorageClient()
) {
    private companion object {
        const val LOCAL_CACHE_MAX = 500
    }

    private val appContext: Context = context.applicationContext

    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    val refreshing: StateFlow<Boolean> get() = HistoryRemoteStore.refreshing

    /**
     * Loads the history from Firebase when the copy in memory is older than a few minutes (or [force]).
     * There is no live listener: one request per load, the result is also saved on the phone so the next
     * app start shows the history at once. Never throws.
     */
    suspend fun refresh(force: Boolean = false) {
        try {
            val loaded = HistoryRemoteStore.refresh(installationId, firebaseDbManager, force) ?: return
            // the files this app uploaded are now known completely: update the storage estimate of every Supabase
            SupabaseAccountManager.reconcile(appContext, loaded)
            val demoOnly = preferencesManager.localHistoryFlow.first().filter { it.isDemo }
            preferencesManager.saveAllRecords(loaded.take(LOCAL_CACHE_MAX) + demoOnly)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("HistoryRepository", "History refresh notice: ${t.message}")
        }
    }

    fun getHistoryFlow(): Flow<List<UploadRecord>> {
        // The remote list is the copy kept in memory (empty until loaded), so the local cache shows immediately
        // even when Firebase has not answered yet (offline / slow network).
        val remoteFlow: Flow<List<UploadRecord>> = HistoryRemoteStore.records
        val localFlow = preferencesManager.localHistoryFlow
            .catch { e ->
                Log.w("HistoryRepository", "Local flow error: ${e.message}")
                emit(emptyList())
            }
        val hiddenFlow = preferencesManager.hiddenHistoryIdsFlow
            .catch { e ->
                Log.w("HistoryRepository", "Hidden ids flow error: ${e.message}")
                emit(emptySet())
            }

        return combine(remoteFlow, localFlow, hiddenFlow) { remoteList, localList, hidden ->
            val mergedMap = LinkedHashMap<String, UploadRecord>()

            // Add local first
            for (item in localList) {
                mergedMap[item.shortId] = item
            }

            // Remote items overwrite/supplement local (keeping the details only the local copy has)
            for (item in remoteList) {
                val local = mergedMap[item.shortId]
                mergedMap[item.shortId] = if (local == null) {
                    item
                } else {
                    item.copy(
                        fileName = item.fileName.ifEmpty { local.fileName },
                        mimeType = item.mimeType.ifEmpty { local.mimeType },
                        storageBucket = item.storageBucket.ifEmpty { local.storageBucket },
                        storagePath = item.storagePath.ifEmpty { local.storagePath },
                        uploadDuration = if (item.uploadDuration > 0) item.uploadDuration else local.uploadDuration,
                        isDemo = local.isDemo
                    )
                }
            }

            mergedMap.values
                .filter { it.shortId !in hidden }
                .sortedByDescending { it.createdAt }
        }
    }

    /**
     * Deletes the given uploads completely: the file in Supabase Storage first, then the history entry and the
     * public link in Firebase, then the local copy. An upload whose file could not be removed from Supabase stays
     * in the list (nothing is half deleted) and the reason is returned.
     */
    suspend fun deleteRecords(
        records: List<UploadRecord>,
        onProgress: (done: Int, total: Int) -> Unit
    ): DeleteOutcome {
        var deleted = 0
        var failed = 0
        val messages = LinkedHashSet<String>()
        val removedIds = mutableListOf<String>()
        val accounts = SupabaseAccountManager.list(appContext).filter { it.isUsable }

        records.forEachIndexed { index, record ->
            onProgress(index, records.size)

            // Demo entries only exist on this device
            if (record.isDemo) {
                removedIds += record.shortId
                deleted++
                return@forEachIndexed
            }

            val storageResult = deleteFromStorage(record, accounts)
            if (storageResult.isFailure) {
                failed++
                messages += storageResult.exceptionOrNull()?.message ?: "Could not delete ${record.fileName}"
                return@forEachIndexed
            }

            val firebaseResult = firebaseDbManager.deleteUpload(installationId, record.shortId, record.fileType)
            if (firebaseResult.isFailure) {
                failed++
                messages += firebaseResult.exceptionOrNull()?.message ?: "Could not remove ${record.fileName} from Firebase"
                return@forEachIndexed
            }
            if (firebaseResult.getOrDefault(true) == false) {
                messages += FirebaseDatabaseManager.DELETE_RULES_HINT
            }
            removedIds += record.shortId
            deleted++
        }

        preferencesManager.removeRecords(removedIds)
        // the screen updates at once, Firebase is not asked again
        HistoryRemoteStore.remove(removedIds)
        onProgress(records.size, records.size)
        return DeleteOutcome(deleted, failed, messages.toList())
    }

    private suspend fun deleteFromStorage(record: UploadRecord, accounts: List<SupabaseAccount>): Result<Unit> {
        // Free trial photos live in the developer's storage, which this app has no right to delete from.
        if (record.isFreeTier) return Result.success(Unit)

        val publicUrl = record.effectiveSupabaseUrl
        if (record.storagePath.isBlank() && publicUrl.isBlank()) return Result.success(Unit)

        if (accounts.isEmpty()) {
            return Result.failure(
                IllegalStateException("Supabase is not set up in Settings, so files cannot be deleted from storage.")
            )
        }

        // The file belongs to one of the saved Supabase projects: the keys of that project are needed
        val account = SupabaseAccountManager.accountOfRecord(accounts, record)
            ?: accounts.takeIf { hostOf(publicUrl) == null }?.firstOrNull()
            ?: return Result.failure(
                IllegalStateException("${record.fileName} is stored in another Supabase project than the ones set up in Settings.")
            )

        val location = locateInStorage(record, publicUrl, account.config)
            ?: return Result.failure(IllegalStateException("The storage path of ${record.fileName} is unknown."))

        val result = supabaseClient.deleteObject(account.config, location.first, location.second, publicUrl.ifBlank { null })
        if (result.isSuccess) {
            // the project has room again
            SupabaseAccountManager.recordDelete(appContext, account.id, record.fileSize)
        }
        return result
    }

    /** Bucket and path of the file: stored with the record, or read from the public URL of older records. */
    private fun locateInStorage(record: UploadRecord, publicUrl: String, config: SupabaseConfig): Pair<String, String>? {
        if (record.storagePath.isNotBlank()) {
            return record.storageBucket.ifBlank { config.bucketName }.trim() to record.storagePath
        }
        val marker = "/storage/v1/object/public/"
        val start = publicUrl.indexOf(marker)
        if (start < 0) return null
        val rest = publicUrl.substring(start + marker.length).substringBefore('?')
        val bucket = Uri.decode(rest.substringBefore('/'))
        val path = Uri.decode(rest.substringAfter('/', ""))
        return if (bucket.isBlank() || path.isBlank()) null else bucket to path
    }

    private fun hostOf(url: String): String? {
        if (url.isBlank()) return null
        return try {
            Uri.parse(url).host
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun clearLocalHistory() {
        preferencesManager.clearHistoryCache()
    }

    /** History screen "clear": empties the cache and hides the currently listed entries on this device. */
    suspend fun clearHistory(currentIds: Collection<String>) {
        preferencesManager.hideAndClear(currentIds)
    }
}
