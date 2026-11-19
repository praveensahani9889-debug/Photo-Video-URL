package com.praveen.studio.photovideo.url.data.remote

import android.content.Context
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.UserDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The user's counters / name / free-trial usage, kept in memory and on the phone.
 *
 * Before, two screens listened live to `user/<id>/details`, so every change was downloaded again and again.
 * Now the details are read from Firebase only
 *  - when they are older than [TTL_MS] and a screen needs them, or
 *  - when the user forces it,
 * and they are updated locally when something is uploaded (the upload writes the counters to Firebase itself).
 */
object UserDetailsStore {
    private const val TTL_MS = 10 * 60_000L
    private const val PREFS = "user_details_cache"

    private val _state = MutableStateFlow(UserDetails())
    val state: StateFlow<UserDetails> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var lastFetchAt = 0L

    @Volatile
    private var forId = ""

    /** Shows the details saved on the phone at once (no network). Safe to call many times. */
    fun load(context: Context, installationId: String) {
        if (forId == installationId) return
        synchronized(this) {
            if (forId == installationId) return
            forId = installationId
            lastFetchAt = 0L
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString("json_$installationId", null)
            _state.value = if (json.isNullOrBlank()) UserDetails() else fromJson(json)
        }
    }

    /** Reads the details from Firebase when they are stale (or [force]). Never throws. */
    suspend fun refresh(
        context: Context,
        installationId: String,
        manager: FirebaseDatabaseManager,
        force: Boolean = false
    ) {
        load(context, installationId)
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && lastFetchAt != 0L && now - lastFetchAt < TTL_MS) return
            val fetched = manager.fetchUserDetailsOrNull(installationId) ?: return
            lastFetchAt = now
            _state.value = fetched
            persist(context, installationId, fetched)
        }
    }

    /** An upload finished: the counters grow here too (the upload already wrote them to Firebase). */
    fun onUploadSucceeded(context: Context, installationId: String, category: MediaCategory, isFreeTier: Boolean) {
        load(context, installationId)
        val current = _state.value
        val updated = when (category) {
            MediaCategory.IMAGES -> current.copy(totalPhotosUpload = current.totalPhotosUpload + 1)
            MediaCategory.VIDEOS -> current.copy(totalVideosUpload = current.totalVideosUpload + 1)
            MediaCategory.DOCUMENTS -> current.copy(totalDocumentsUpload = current.totalDocumentsUpload + 1)
            MediaCategory.FILES -> current.copy(totalFilesUpload = current.totalFilesUpload + 1)
            MediaCategory.APKS -> current.copy(totalApksUpload = current.totalApksUpload + 1)
        }.let { if (isFreeTier) it.copy(freeTierUploadsUsed = it.freeTierUploadsUsed + 1) else it }
        _state.value = updated
        persist(context, installationId, updated)
    }

    /** The name was saved to Firebase. */
    fun onNameSaved(context: Context, installationId: String, name: String) {
        load(context, installationId)
        val updated = _state.value.copy(name = name)
        _state.value = updated
        persist(context, installationId, updated)
    }

    private fun persist(context: Context, installationId: String, details: UserDetails) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("json_$installationId", toJson(details))
                .apply()
        } catch (_: Throwable) {
        }
    }

    private fun toJson(d: UserDetails): String = JSONObject()
        .put("name", d.name)
        .put("photos", d.totalPhotosUpload)
        .put("videos", d.totalVideosUpload)
        .put("docs", d.totalDocumentsUpload)
        .put("files", d.totalFilesUpload)
        .put("apks", d.totalApksUpload)
        .put("premium", d.premium)
        .put("freeUsed", d.freeTierUploadsUsed)
        .toString()

    private fun fromJson(json: String): UserDetails = try {
        val o = JSONObject(json)
        UserDetails(
            name = o.optString("name"),
            totalPhotosUpload = o.optLong("photos"),
            totalVideosUpload = o.optLong("videos"),
            totalDocumentsUpload = o.optLong("docs"),
            totalFilesUpload = o.optLong("files"),
            totalApksUpload = o.optLong("apks"),
            premium = o.optBoolean("premium"),
            freeTierUploadsUsed = o.optLong("freeUsed")
        )
    } catch (_: Throwable) {
        UserDetails()
    }
}
