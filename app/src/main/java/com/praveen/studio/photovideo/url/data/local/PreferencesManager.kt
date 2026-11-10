package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

class PreferencesManager(private val context: Context) {

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME] ?: ThemePreference.SYSTEM.name
        try {
            ThemePreference.valueOf(name)
        } catch (_: Throwable) {
            ThemePreference.SYSTEM
        }
    }

    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    val localHistoryFlow: Flow<List<UploadRecord>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_LOCAL_UPLOADS] ?: "[]"
        deserializeList(json)
    }

    /** History entries the user cleared on this device (their links keep working, they are just hidden). */
    val hiddenHistoryIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_IDS] ?: emptySet()
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
        try {
            // Read-modify-write inside one edit{} so two uploads finishing together cannot overwrite each other.
            context.dataStore.edit { prefs ->
                val currentList = deserializeList(prefs[KEY_LOCAL_UPLOADS] ?: "[]").toMutableList()
                currentList.removeAll { it.shortId == record.shortId }
                currentList.add(0, record)
                prefs[KEY_LOCAL_UPLOADS] = serializeList(currentList.take(MAX_LOCAL_RECORDS))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { }
    }

    suspend fun saveAllRecords(records: List<UploadRecord>) {
        try {
            val json = serializeList(records)
            context.dataStore.edit { prefs ->
                prefs[KEY_LOCAL_UPLOADS] = json
            }
        } catch (_: Throwable) { }
    }

    /** Clears only the local cache; the history is re-synced from Firebase (hidden entries come back too). */
    suspend fun clearHistoryCache() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_UPLOADS] = "[]"
            prefs.remove(KEY_HIDDEN_IDS)
        }
    }

    /** Removes deleted uploads from the local copy (and from the hidden list, they no longer exist anywhere). */
    suspend fun removeRecords(ids: Collection<String>) {
        if (ids.isEmpty()) return
        try {
            context.dataStore.edit { prefs ->
                val remaining = deserializeList(prefs[KEY_LOCAL_UPLOADS] ?: "[]").filter { it.shortId !in ids }
                prefs[KEY_LOCAL_UPLOADS] = serializeList(remaining)
                val hidden = prefs[KEY_HIDDEN_IDS]
                if (hidden != null) prefs[KEY_HIDDEN_IDS] = hidden - ids.toSet()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { }
    }

    /** "Clear history" button: empties the local cache and hides the given entries on this device. */
    suspend fun hideAndClear(ids: Collection<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_UPLOADS] = "[]"
            prefs[KEY_HIDDEN_IDS] = (prefs[KEY_HIDDEN_IDS] ?: emptySet()) + ids
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        private val KEY_LOCAL_UPLOADS = stringPreferencesKey("local_cached_uploads")
        private val KEY_HIDDEN_IDS = stringSetPreferencesKey("hidden_history_ids")
        private const val MAX_LOCAL_RECORDS = 500

        private fun recordToJson(record: UploadRecord): JSONObject {
            return JSONObject().apply {
                put("shortId", record.shortId)
                put("ownerInstallationId", record.ownerInstallationId)
                put("fileName", record.fileName)
                put("fileType", record.fileType)
                put("type", record.type)
                put("mimeType", record.mimeType)
                put("fileSize", record.fileSize)
                put("storageBucket", record.storageBucket)
                put("storagePath", record.storagePath)
                put("originalSupabaseUrl", record.originalSupabaseUrl)
                put("supabaseUrl", record.supabaseUrl)
                put("publicUrl", record.publicUrl)
                put("shortUrl", record.shortUrl)
                put("uploadDate", record.uploadDate)
                put("uploadTime", record.uploadTime)
                put("createdAt", record.createdAt)
                put("timestamp", record.timestamp)
                put("uploadDuration", record.uploadDuration)
                put("status", record.status)
                put("isDemo", record.isDemo)
                put("isFreeTier", record.isFreeTier)
            }
        }

        private fun jsonToRecord(obj: JSONObject): UploadRecord {
            return UploadRecord(
                shortId = obj.optString("shortId"),
                ownerInstallationId = obj.optString("ownerInstallationId"),
                fileName = obj.optString("fileName"),
                fileType = obj.optString("fileType"),
                type = obj.optString("type"),
                mimeType = obj.optString("mimeType"),
                fileSize = obj.optLong("fileSize", 0L),
                storageBucket = obj.optString("storageBucket"),
                storagePath = obj.optString("storagePath"),
                originalSupabaseUrl = obj.optString("originalSupabaseUrl"),
                supabaseUrl = obj.optString("supabaseUrl"),
                publicUrl = obj.optString("publicUrl"),
                shortUrl = obj.optString("shortUrl"),
                uploadDate = obj.optString("uploadDate"),
                uploadTime = obj.optString("uploadTime"),
                createdAt = obj.optLong("createdAt", 0L),
                timestamp = obj.optLong("timestamp", 0L),
                uploadDuration = obj.optLong("uploadDuration", 0L),
                status = obj.optString("status", "completed"),
                isDemo = obj.optBoolean("isDemo", false),
                isFreeTier = obj.optBoolean("isFreeTier", false)
            )
        }

        private fun serializeList(list: List<UploadRecord>): String {
            val arr = JSONArray()
            for (record in list) {
                arr.put(recordToJson(record))
            }
            return arr.toString()
        }

        private fun deserializeList(json: String): List<UploadRecord> {
            if (json.isBlank() || json == "[]") return emptyList()
            val list = mutableListOf<UploadRecord>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(jsonToRecord(obj))
                }
            } catch (_: Throwable) { }
            return list
        }
    }
}
