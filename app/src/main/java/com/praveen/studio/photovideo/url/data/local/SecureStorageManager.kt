package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig

/**
 * Keeps the user's Supabase projects on the phone (encrypted with the Android Keystore).
 *
 * The encrypted preferences are created ONCE per app process and shared: creating them is slow and memory hungry
 * (Keystore + Tink key sets), and it used to happen again for every repository that was created.
 *
 * Older app versions stored a single project (url / key / bucket). It is converted into the first account the first
 * time the accounts are read, so nobody has to set up Supabase again after the update.
 */
class SecureStorageManager(context: Context) {
    private val prefs: SharedPreferences = sharedPreferences(context.applicationContext)

    companion object {
        private const val TAG = "SecureStorageManager"
        private const val KEY_PROJECT_URL = "supabase_project_url"
        private const val KEY_ANON_KEY = "supabase_anon_key"
        private const val KEY_BUCKET_NAME = "supabase_bucket_name"
        private const val KEY_ACCOUNTS = "supabase_accounts_json"
        private const val KEY_ACTIVE_ID = "supabase_active_id"

        @Volatile
        private var shared: SharedPreferences? = null

        private fun sharedPreferences(context: Context): SharedPreferences {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                val created = createPreferences(context)
                shared = created
                return created
            }
        }

        private fun createPreferences(context: Context): SharedPreferences {
            // Android 9 (API 28) has known Keystore/Tink compatibility issues with EncryptedSharedPreferences.
            // On Android 9 and below, use standard private SharedPreferences (protected by Linux app sandbox).
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                return context.getSharedPreferences("supabase_config_prefs", Context.MODE_PRIVATE)
            }

            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    "supabase_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Falling back to private SharedPreferences: ${t.message}")
                context.getSharedPreferences("supabase_config_prefs", Context.MODE_PRIVATE)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Accounts (up to 5 Supabase projects)
    // ---------------------------------------------------------------------------------------------

    fun getAccounts(): List<SupabaseAccount> {
        if (!prefs.contains(KEY_ACCOUNTS)) {
            migrateLegacyConfig()
        }
        return SupabaseAccount.listFromJson(prefs.getString(KEY_ACCOUNTS, null))
    }

    fun getActiveId(): String = prefs.getString(KEY_ACTIVE_ID, "").orEmpty()

    fun saveAccounts(accounts: List<SupabaseAccount>, activeId: String) {
        prefs.edit()
            .putString(KEY_ACCOUNTS, SupabaseAccount.listToJson(accounts))
            .putString(KEY_ACTIVE_ID, activeId)
            .apply()
    }

    private fun migrateLegacyConfig() {
        val url = prefs.getString(KEY_PROJECT_URL, "").orEmpty()
        val key = prefs.getString(KEY_ANON_KEY, "").orEmpty()
        val bucket = prefs.getString(KEY_BUCKET_NAME, "media").orEmpty().ifBlank { "media" }
        val editor = prefs.edit()
        if (url.isNotBlank() && key.isNotBlank()) {
            val account = SupabaseAccount(
                id = SupabaseAccount.newId(),
                label = "",
                projectUrl = url.trim(),
                anonKey = key.trim(),
                bucketName = bucket.trim(),
                createdAt = System.currentTimeMillis()
            )
            editor.putString(KEY_ACCOUNTS, SupabaseAccount.listToJson(listOf(account)))
            editor.putString(KEY_ACTIVE_ID, account.id)
        } else {
            editor.putString(KEY_ACCOUNTS, "[]")
            editor.putString(KEY_ACTIVE_ID, "")
        }
        // the single-project keys are not used any more
        editor.remove(KEY_PROJECT_URL).remove(KEY_ANON_KEY).remove(KEY_BUCKET_NAME)
        editor.apply()
    }

    // ---------------------------------------------------------------------------------------------
    // Single-project view (the active account). Kept so older code paths keep working.
    // ---------------------------------------------------------------------------------------------

    fun getConfig(): SupabaseConfig {
        val accounts = getAccounts()
        val activeId = getActiveId()
        val account = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
        return account?.config ?: SupabaseConfig()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
        prefs.edit().putString(KEY_ACCOUNTS, "[]").putString(KEY_ACTIVE_ID, "").apply()
    }
}
