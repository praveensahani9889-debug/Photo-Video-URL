package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig

class SecureStorageManager(context: Context) {
    private val prefs: SharedPreferences = createPreferences(context)

    companion object {
        private const val TAG = "SecureStorageManager"
        private const val KEY_PROJECT_URL = "supabase_project_url"
        private const val KEY_ANON_KEY = "supabase_anon_key"
        private const val KEY_BUCKET_NAME = "supabase_bucket_name"

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

    fun saveConfig(config: SupabaseConfig) {
        prefs.edit()
            .putString(KEY_PROJECT_URL, config.projectUrl.trim())
            .putString(KEY_ANON_KEY, config.anonKey.trim())
            .putString(KEY_BUCKET_NAME, config.bucketName.trim().ifEmpty { "media" })
            .apply()
    }

    fun getConfig(): SupabaseConfig {
        val url = prefs.getString(KEY_PROJECT_URL, "") ?: ""
        val key = prefs.getString(KEY_ANON_KEY, "") ?: ""
        val bucket = prefs.getString(KEY_BUCKET_NAME, "media") ?: "media"
        return SupabaseConfig(
            projectUrl = url,
            anonKey = key,
            bucketName = bucket
        )
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}
