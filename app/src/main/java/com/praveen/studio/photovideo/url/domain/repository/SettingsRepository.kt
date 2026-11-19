package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.BucketUsage
import com.praveen.studio.photovideo.url.data.remote.FirebaseCheckLine
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.remote.HistoryRemoteStore
import com.praveen.studio.photovideo.url.data.remote.IdentityManager
import com.praveen.studio.photovideo.url.data.remote.SetupCheckItem
import com.praveen.studio.photovideo.url.data.remote.SupabaseStorageClient
import com.praveen.studio.photovideo.url.data.remote.UserDetailsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    private val installationIdProvider: InstallationIdProvider = InstallationIdProvider(context),
    private val supabaseClient: SupabaseStorageClient = SupabaseStorageClient(),
    private val firebaseDbManager: FirebaseDatabaseManager = FirebaseDatabaseManager()
) {
    companion object {
        private val REMOTE_CREDENTIAL_FIELDS = listOf("supabaseApi", "supabaseKey")
        private const val FLAGS = "app_flags"
        private const val FLAG_LEGACY_CLEANED = "legacy_credentials_cleaned"
    }

    val themeFlow: Flow<ThemePreference> = preferencesManager.themeFlow
    val isFirstLaunchFlow: Flow<Boolean> = preferencesManager.isFirstLaunchFlow
    val installationId: String = installationIdProvider.getInstallationId()
    val deviceFingerprint: String get() = installationId

    // ---------------------------------------------------------------------------------------------
    // Supabase projects (up to 5)
    // ---------------------------------------------------------------------------------------------

    val accounts: StateFlow<List<SupabaseAccount>>
        get() {
            SupabaseAccountManager.load(context)
            return SupabaseAccountManager.accounts
        }

    val activeAccountId: StateFlow<String> get() = SupabaseAccountManager.activeId

    /**
     * Saves a new or edited project on the phone and backs it up (encrypted) in Firebase with ONE write.
     * Returns false when the limit of projects is reached.
     */
    suspend fun saveAccount(account: SupabaseAccount, maxAccounts: Int): Boolean {
        // Store the normalised URL (https://<ref>.supabase.co), so a pasted "http://..." or ".../rest/v1/"
        // still produces links that the website accepts.
        val config = account.config
        val normalized = account.copy(
            label = account.label.trim(),
            projectUrl = config.cleanUrl.ifEmpty { account.projectUrl.trim() },
            anonKey = account.anonKey.trim(),
            bucketName = account.bucketName.trim().ifEmpty { "media" }
        )
        if (!SupabaseAccountManager.upsert(context, normalized, maxAccounts)) return false

        // Encrypted backup (only this phone can open it): after a re-install the settings come back by themselves
        IdentityManager.backupAccounts(context)
        cleanLegacyCredentialsOnce()
        return true
    }

    suspend fun deleteAccount(id: String) {
        SupabaseAccountManager.remove(context, id)
        // the backup is updated (or removed when nothing is left), otherwise it would be restored at the next start
        IdentityManager.backupAccounts(context)
    }

    fun setActiveAccount(id: String) {
        SupabaseAccountManager.setActive(context, id)
    }

    /** Readable copies that older app versions stored in Firebase are removed (once per install). */
    private suspend fun cleanLegacyCredentialsOnce() {
        val flags = context.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)
        if (flags.getBoolean(FLAG_LEGACY_CLEANED, false)) return
        firebaseDbManager.removeUserDetailFields(installationId, REMOTE_CREDENTIAL_FIELDS)
        flags.edit().putBoolean(FLAG_LEGACY_CLEANED, true).apply()
    }

    suspend fun testConnection(config: SupabaseConfig): Result<String> {
        return supabaseClient.testConnection(config)
    }

    /** Connection, public bucket, upload / read / delete permission of one project. */
    suspend fun runSetupCheck(config: SupabaseConfig): List<SetupCheckItem> {
        return supabaseClient.runSetupCheck(config)
    }

    /** Lists the real files of the bucket and updates the storage estimate of the project. */
    suspend fun scanAccount(account: SupabaseAccount, onProgress: (Int) -> Unit): Result<BucketUsage> {
        val result = supabaseClient.scanBucketUsage(account.config, onProgress)
        result.getOrNull()?.let { usage ->
            val tracked = SupabaseAccountManager
                .recordSums(SupabaseAccountManager.list(context), HistoryRemoteStore.records.value)[account.id] ?: 0L
            SupabaseAccountManager.applyScan(context, account.id, usage.totalBytes, tracked)
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Profile
    // ---------------------------------------------------------------------------------------------

    /** Saves the name to Firebase (one write; the caller decides when, there is no auto-save while typing). */
    suspend fun saveUserName(name: String) {
        firebaseDbManager.updateUserDetailFields(installationId, mapOf("name" to name))
        UserDetailsStore.onNameSaved(context, installationId, name)
    }

    val userDetails: StateFlow<UserDetails> get() = UserDetailsStore.state

    /** Reads the counters from Firebase only when the saved copy is stale (or [force]). */
    suspend fun refreshUserDetails(force: Boolean = false) {
        UserDetailsStore.refresh(context, installationId, firebaseDbManager, force)
    }

    fun loadSavedUserDetails() {
        UserDetailsStore.load(context, installationId)
    }

    // ---------------------------------------------------------------------------------------------

    suspend fun setTheme(theme: ThemePreference) {
        preferencesManager.setTheme(theme)
    }

    suspend fun setFirstLaunchCompleted() {
        preferencesManager.setFirstLaunchCompleted()
    }

    suspend fun clearHistoryCache() {
        preferencesManager.clearHistoryCache()
    }

    suspend fun checkFirebase(): List<FirebaseCheckLine> {
        return firebaseDbManager.runConnectionCheck(installationId)
    }
}
