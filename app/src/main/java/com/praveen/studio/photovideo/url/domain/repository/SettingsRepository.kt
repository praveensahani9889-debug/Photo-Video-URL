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
    companion object {
        private val REMOTE_CREDENTIAL_FIELDS = listOf("supabaseApi", "supabaseKey")
    }

    val themeFlow: Flow<ThemePreference> = preferencesManager.themeFlow
    val isFirstLaunchFlow: Flow<Boolean> = preferencesManager.isFirstLaunchFlow
    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    fun getConfig(): SupabaseConfig = secureStorageManager.getConfig()

    suspend fun saveConfig(config: SupabaseConfig, userName: String = "") {
        // Store the normalised URL (https://<ref>.supabase.co), so a pasted "http://..." or ".../rest/v1/"
        // still produces links that the website accepts.
        secureStorageManager.saveConfig(config.copy(projectUrl = config.cleanUrl.ifEmpty { config.projectUrl.trim() }))

        // The Supabase URL / key stay on this device only. Remove copies that older app versions
        // stored in Firebase. Only the name is synced (counters stay untouched).
        firebaseDbManager.removeUserDetailFields(installationId, REMOTE_CREDENTIAL_FIELDS)
        if (userName.isNotBlank()) {
            firebaseDbManager.updateUserDetailFields(installationId, mapOf("name" to userName))
        }
    }

    /** Removes credential copies from Firebase (older app versions uploaded them). */
    suspend fun clearRemoteCredentials() {
        firebaseDbManager.removeUserDetailFields(installationId, REMOTE_CREDENTIAL_FIELDS)
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
