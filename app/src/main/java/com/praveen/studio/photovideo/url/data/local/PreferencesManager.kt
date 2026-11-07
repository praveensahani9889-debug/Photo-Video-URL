package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class PreferencesManager(private val context: Context) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, UploadRecord::class.java)
    private val adapter = moshi.adapter<List<UploadRecord>>(listType)

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME] ?: ThemePreference.SYSTEM.name
        try {
            ThemePreference.valueOf(name)
        } catch (_: Exception) {
            ThemePreference.SYSTEM
        }
    }

    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    val localHistoryFlow: Flow<List<UploadRecord>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_LOCAL_UPLOADS] ?: "[]"
        try {
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }

    suspend fun addUploadRecord(record: UploadRecord) {
        val currentList = localHistoryFlow.first().toMutableList()
        // Remove if existing with same shortId
        currentList.removeAll { it.shortId == record.shortId }
        currentList.add(0, record)
        val json = adapter.toJson(currentList)
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_UPLOADS] = json
        }
    }

    suspend fun saveAllRecords(records: List<UploadRecord>) {
        val json = adapter.toJson(records)
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_UPLOADS] = json
        }
    }

    suspend fun clearHistoryCache() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_UPLOADS] = "[]"
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        private val KEY_LOCAL_UPLOADS = stringPreferencesKey("local_cached_uploads")
    }
}
