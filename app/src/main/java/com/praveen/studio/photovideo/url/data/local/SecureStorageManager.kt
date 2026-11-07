package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig

class SecureStorageManager(context: Context) {
    private val prefs: SharedPreferences = try {
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
    } catch (e: Exception) {
        Log.w("SecureStorageManager", "Falling back to private SharedPreferences: ${e.message}")
        context.getSharedPreferences("supabase_config_prefs", Context.MODE_PRIVATE)
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

    companion object {
        private const val KEY_PROJECT_URL = "supabase_project_url"
        private const val KEY_ANON_KEY = "supabase_anon_key"
        private const val KEY_BUCKET_NAME = "supabase_bucket_name"
    }
}
