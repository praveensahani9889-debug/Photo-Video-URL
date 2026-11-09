package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import android.util.Log
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

class HistoryRepository(
    context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager()
) {
    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    fun getHistoryFlow(): Flow<List<UploadRecord>> {
        // onStart emits an empty remote list first, so the local cache shows immediately even when
        // Firebase has not answered yet (offline / slow network).
        val remoteFlow = firebaseDbManager.observeAllUserUploads(installationId)
            .catch { e ->
                Log.w("HistoryRepository", "Remote Firebase flow error: ${e.message}")
                emit(emptyList())
            }
            .onStart { emit(emptyList()) }
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

    suspend fun clearLocalHistory() {
        preferencesManager.clearHistoryCache()
    }

    /** History screen "clear": empties the cache and hides the currently listed entries on this device. */
    suspend fun clearHistory(currentIds: Collection<String>) {
        preferencesManager.hideAndClear(currentIds)
    }
}
