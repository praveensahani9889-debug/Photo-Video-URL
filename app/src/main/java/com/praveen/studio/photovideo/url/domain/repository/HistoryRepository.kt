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

class HistoryRepository(
    context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager()
) {
    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    fun getHistoryFlow(): Flow<List<UploadRecord>> {
        val remoteFlow = firebaseDbManager.observeAllUserUploads(installationId)
            .catch { e ->
                Log.w("HistoryRepository", "Remote Firebase flow error: ${e.message}")
                emit(emptyList())
            }
        val localFlow = preferencesManager.localHistoryFlow
            .catch { e ->
                Log.w("HistoryRepository", "Local flow error: ${e.message}")
                emit(emptyList())
            }

        return combine(remoteFlow, localFlow) { remoteList, localList ->
            val mergedMap = LinkedHashMap<String, UploadRecord>()

            // Add local first
            for (item in localList) {
                mergedMap[item.shortId] = item
            }

            // Remote items overwrite/supplement local
            for (item in remoteList) {
                mergedMap[item.shortId] = item
            }

            mergedMap.values.sortedByDescending { it.createdAt }
        }
    }

    suspend fun clearLocalHistory() {
        preferencesManager.clearHistoryCache()
    }
}
