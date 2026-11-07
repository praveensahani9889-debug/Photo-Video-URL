package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.SecureStorageManager
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    context: Context,
    private val secureStorageManager: SecureStorageManager = SecureStorageManager(context),
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val supabaseClient: SupabaseStorageClient = SupabaseStorageClient(),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager()
) {
    val themeFlow: Flow<ThemePreference> = preferencesManager.themeFlow
    val isFirstLaunchFlow: Flow<Boolean> = preferencesManager.isFirstLaunchFlow
    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    fun getConfig(): SupabaseConfig = secureStorageManager.getConfig()

    suspend fun saveConfig(config: SupabaseConfig, userName: String = "") {
        secureStorageManager.saveConfig(config)

        // Sync with Firebase details (only these fields, counters stay untouched)
        val fields = mutableMapOf<String, Any>(
            "supabaseApi" to config.projectUrl,
            "supabaseKey" to config.anonKey
        )
        if (userName.isNotBlank()) fields["name"] = userName
        firebaseDbManager.updateUserDetailFields(installationId, fields)
    }

    suspend fun saveUserName(name: String) {
        firebaseDbManager.updateUserDetailFields(installationId, mapOf("name" to name))
    }

    fun observeUserDetails(): Flow<UserDetails> {
        return firebaseDbManager.observeUserDetails(installationId)
    }

    suspend fun getUserDetails(): UserDetails {
        return firebaseDbManager.fetchUserDetails(installationId)
    }

    fun clearConfig() {
        secureStorageManager.clearConfig()
    }

    suspend fun testConnection(config: SupabaseConfig): Result<String> {
        return supabaseClient.testConnection(config)
    }

    suspend fun setTheme(theme: ThemePreference) {
        preferencesManager.setTheme(theme)
    }

    suspend fun setFirstLaunchCompleted() {
        preferencesManager.setFirstLaunchCompleted()
    }

    suspend fun clearHistoryCache() {
        preferencesManager.clearHistoryCache()
    }
}
