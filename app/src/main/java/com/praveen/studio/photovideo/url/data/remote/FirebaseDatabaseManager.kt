package com.praveen.studio.photovideo.url.data.remote

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.praveen.studio.photovideo.url.data.model.LinkOptions
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.PublicLinkDetails
import com.praveen.studio.photovideo.url.data.model.PublicLinkLookup
import com.praveen.studio.photovideo.url.data.model.PublicLinkRecord
import com.praveen.studio.photovideo.url.data.model.AppControl
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException

/** One line of the Firebase self check shown in Settings. */
data class FirebaseCheckLine(val ok: Boolean, val text: String)

class FirebaseDatabaseManager {
    companion object {
        private const val TAG = "FirebaseDbManager"
        private const val NODE_USER = "user"
        private const val NODE_DETAILS = "details"
        private const val NODE_LEGACY_USER_DETAILS = "userDetails"
        private const val NODE_APP = "app"
        private const val NODE_PUBLIC_LINKS = "public_links"
        private const val NODE_DEVICE_PROFILES = "device_profiles"
        private const val NODE_IP_PROFILES = "ip_profiles"
        private const val NODE_CONFIG_BACKUP = "cfg"

        private const val SHORT_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val SHORT_ID_LENGTH = 7

        private const val REGISTER_TIMEOUT_MS = 20_000L
        private const val PUBLIC_WRITE_TIMEOUT_MS = 15_000L
        private const val COUNTER_TIMEOUT_MS = 8_000L
        private const val LOOKUP_TIMEOUT_MS = 10_000L
        private const val DELETE_TIMEOUT_MS = 12_000L
        private const val HISTORY_TIMEOUT_MS = 25_000L
        private val HISTORY_FOLDERS = listOf("images", "videos", "documents", "files", "apks")

        /** The app/ defaults only need to be checked once per app process, not on every screen. */
        @Volatile
        private var metadataChecked = false

        const val PERMISSION_DENIED_HINT =
            "Firebase rules block saving. Open Firebase Console > Realtime Database > Rules, " +
                "publish the rules from README.md (public_links + user + app) and try again."

        /** True when Firebase refused the request because of the database security rules. */
        fun isPermissionDenied(t: Throwable?): Boolean {
            var current = t
            var depth = 0
            while (current != null && depth < 6) {
                if (current.message.orEmpty().contains("permission denied", ignoreCase = true)) return true
                current = current.cause
                depth++
            }
            return false
        }

        const val DELETE_RULES_HINT =
            "Firebase rules do not allow deleting. Publish the rules from README.md (public_links must allow delete)."

        /** Short text for the user instead of a bare "Permission denied". */
        fun describeError(t: Throwable?): String = when {
            isPermissionDenied(t) -> PERMISSION_DENIED_HINT
            else -> t?.message ?: "unknown error"
        }
    }

    private val random = SecureRandom()

    @Volatile
    private var cachedDatabase: FirebaseDatabase? = null

    /**
     * Safely retrieves FirebaseDatabase without throwing any unhandled exceptions.
     * Prevents startup crashes on devices without Play Services or before FirebaseApp is ready.
     */
    private fun getDatabase(): FirebaseDatabase? {
        if (cachedDatabase != null) return cachedDatabase
        synchronized(this) {
            if (cachedDatabase != null) return cachedDatabase
            cachedDatabase = try {
                FirebaseDatabase.getInstance("https://photo-video-url-default-rtdb.firebaseio.com")
            } catch (_: Throwable) {
                try {
                    FirebaseDatabase.getInstance()
                } catch (t2: Throwable) {
                    Log.w(TAG, "FirebaseDatabase not available: ${t2.message}")
                    null
                }
            }
            return cachedDatabase
        }
    }

    private fun userRootRef(): DatabaseReference? = getDatabase()?.getReference(NODE_USER)

    private fun appRef(): DatabaseReference? = getDatabase()?.getReference(NODE_APP)

    private fun publicLinksRef(): DatabaseReference? = getDatabase()?.getReference(NODE_PUBLIC_LINKS)

    private fun userRef(installationId: String): DatabaseReference? {
        return userRootRef()?.child(installationId)
    }

    private fun userDetailsRef(installationId: String): DatabaseReference? {
        return userRef(installationId)?.child(NODE_DETAILS)
    }

    private fun categoryNode(category: MediaCategory): String {
        return when (category) {
            MediaCategory.IMAGES -> "images"
            MediaCategory.VIDEOS -> "videos"
            MediaCategory.DOCUMENTS -> "documents"
            MediaCategory.FILES -> "files"
            MediaCategory.APKS -> "apks"
        }
    }

    /**
     * Creates the default `app/backend` and `app/about` nodes when they are missing (only works when the database
     * rules allow it; with the recommended read-only rules the developer creates them in the console).
     * Runs at most once per app process and never delays the caller.
     */
    private fun ensureAppDefaults(root: DatabaseReference, hasBackend: Boolean, hasAbout: Boolean) {
        if (metadataChecked) return
        metadataChecked = true
        if (hasBackend && hasAbout) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!hasBackend) {
                    root.child("backend").setValue(
                        mapOf(
                            "supabaseApi" to "",
                            "supabaseKey" to "",
                            "bucket" to "media",
                            "demoEnabled" to true,
                            "demoImageLimit" to 3,
                            "ipLimitEnabled" to true,
                            "ipFreeLimit" to 30,
                            "maxSupabaseAccounts" to 5
                        )
                    ).await()
                }
                if (!hasAbout) {
                    root.child("about").setValue(
                        mapOf(
                            "appName" to "Photo Video URL",
                            "contact" to "https://ivu.gt.tc/contact",
                            "supportUrl" to "https://ivu.gt.tc/contact",
                            "termsUrl" to "https://ivu.gt.tc/terms",
                            "privacyUrl" to "https://ivu.gt.tc/privacy",
                            "domain" to "https://ivu.gt.tc",
                            "version" to "2.9.0"
                        )
                    ).await()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ensureAppDefaults notice: ${t.message}")
            }
        }
    }

    /**
     * Reads the remote switches from `app/backend` (demo on/off + limit, network limit on/off + limit, max Supabase
     * projects, maintenance, new update). ONE request for the whole `app` node.
     * Returns null when they cannot be read (offline); the caller then keeps the last known values.
     */
    suspend fun fetchAppControl(): AppControl? {
        val root = appRef() ?: return null
        return try {
            val appSnap = withTimeout(LOOKUP_TIMEOUT_MS) { root.get().await() }
            val snap = appSnap.child("backend")
            ensureAppDefaults(root, snap.exists(), appSnap.child("about").exists())
            if (!snap.exists()) {
                AppControl()
            } else {
                fun text(key: String): String = snap.child(key).getValue(String::class.java)?.trim().orEmpty()
                val limitNode = snap.child("demoImageLimit")
                val ipLimitNode = snap.child("ipFreeLimit")
                val maxAccountsNode = snap.child("maxSupabaseAccounts")
                AppControl(
                    demoEnabled = readBoolean(snap.child("demoEnabled"), true),
                    demoImageLimit = if (limitNode.exists()) getNumberAsLong(limitNode).coerceAtLeast(0L) else 3L,
                    ipLimitEnabled = readBoolean(snap.child("ipLimitEnabled"), true),
                    ipFreeLimit = if (ipLimitNode.exists()) getNumberAsLong(ipLimitNode).coerceAtLeast(0L) else 30L,
                    maxSupabaseAccounts = if (maxAccountsNode.exists()) getNumberAsLong(maxAccountsNode).toInt() else 5,
                    advancedOptionsEnabled = readBoolean(snap.child("advancedOptionsEnabled"), true),
                    blockedWords = text("blockedWords").split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                    maintenance = readBoolean(snap.child("maintenance"), false),
                    maintenanceMessage = text("maintenanceMessage"),
                    newUpdate = readBoolean(snap.child("newUpdate"), false),
                    latestVersion = text("latestVersion"),
                    updateLink = text("updateLink"),
                    forceUpdate = readBoolean(snap.child("forceUpdate"), false),
                    updateMessage = text("updateMessage")
                )
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "fetchAppControl notice: ${t.message}")
            null
        }
    }

    /** true / false can be typed in the console as a boolean, as "true" / "false" text or as 1 / 0. */
    private fun readBoolean(snapshot: DataSnapshot, default: Boolean): Boolean {
        if (!snapshot.exists()) return default
        return when (val value = snapshot.value) {
            is Boolean -> value
            is String -> value.trim().equals("true", ignoreCase = true) || value.trim() == "1"
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    /**
     * Fetches Supabase API and Key configured manually in Firebase under `app/backend`.
     * Returns null if not set or blank.
     */
    suspend fun fetchAppBackendConfig(): SupabaseConfig? {
        val root = appRef() ?: return null
        return try {
            val snap = root.child("backend").get().await()
            if (snap.exists()) {
                val api = snap.child("supabaseApi").getValue(String::class.java)?.trim().orEmpty()
                val key = snap.child("supabaseKey").getValue(String::class.java)?.trim().orEmpty()
                val bucket = snap.child("bucket").getValue(String::class.java)?.trim() ?: "media"
                if (api.isNotBlank() && key.isNotBlank()) {
                    val cfg = SupabaseConfig(projectUrl = api, anonKey = key, bucketName = bucket)
                    if (!cfg.isPlaceholder) cfg else null
                } else {
                    null
                }
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchAppBackendConfig notice: ${t.message}")
            null
        }
    }

    /**
     * Generates a collision-checked random URL-safe ID.
     */
    suspend fun generateUniqueShortId(category: MediaCategory): String {
        val prefix = category.shortIdPrefix
        val linksRef = publicLinksRef()
        if (linksRef == null) {
            return "${prefix}${System.currentTimeMillis().toString(36)}"
        }

        var attempts = 0
        while (attempts < 5) {
            attempts++
            val sb = StringBuilder(prefix)
            for (i in 0 until SHORT_ID_LENGTH) {
                val idx = random.nextInt(SHORT_ID_ALPHABET.length)
                sb.append(SHORT_ID_ALPHABET[idx])
            }
            val candidate = sb.toString()

            try {
                val snapshot = linksRef.child(candidate).get().await()
                if (!snapshot.exists()) {
                    return candidate
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Collision check notice: ${t.message}")
                return candidate
            }
        }
        return "${prefix}${System.currentTimeMillis().toString(36)}"
    }

    private fun getNumberAsLong(snapshot: DataSnapshot): Long {
        val v = snapshot.value ?: return 0L
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun parseUserDetailsSafe(snapshot: DataSnapshot?): UserDetails {
        if (snapshot == null || !snapshot.exists()) return UserDetails()
        return try {
            val name = snapshot.child("name").getValue(String::class.java) ?: ""
            val photos = getNumberAsLong(snapshot.child("totalPhotosUpload"))
            val videos = getNumberAsLong(snapshot.child("totalVideosUpload"))
            val docs = getNumberAsLong(snapshot.child("totalDocumentsUpload"))
            val files = getNumberAsLong(snapshot.child("totalFilesUpload"))
            val apks = getNumberAsLong(snapshot.child("totalApksUpload"))
            val api = snapshot.child("supabaseApi").getValue(String::class.java).orEmpty()
            val key = snapshot.child("supabaseKey").getValue(String::class.java).orEmpty()
            val premium = snapshot.child("premium").getValue(Boolean::class.java) ?: false
            val freeUsed = getNumberAsLong(snapshot.child("freeTierUploadsUsed"))
            // the number of free demo photos is set by the developer in app/backend (demoEnabled / demoImageLimit)
            val freeMax = AppControlStore.current.effectiveDemoLimit

            UserDetails(
                name = name,
                totalPhotosUpload = photos,
                totalVideosUpload = videos,
                totalDocumentsUpload = docs,
                totalFilesUpload = files,
                totalApksUpload = apks,
                supabaseApi = api,
                supabaseKey = key,
                premium = premium,
                freeTierUploadsUsed = freeUsed,
                freeTierMax = freeMax
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Error in parseUserDetailsSafe: ${t.message}")
            UserDetails()
        }
    }

    /**
     * Fetches user details (upload counters, free tier count, custom credentials) using Installation ID.
     */
    suspend fun fetchUserDetails(installationId: String): UserDetails {
        val ref = userDetailsRef(installationId) ?: return UserDetails()
        return try {
            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                parseUserDetailsSafe(snapshot)
            } else {
                val legacySnapshot = userRef(installationId)?.child(NODE_LEGACY_USER_DETAILS)?.get()?.await()
                parseUserDetailsSafe(legacySnapshot)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchUserDetails notice: ${t.message}")
            UserDetails()
        }
    }

    /**
     * Same as [fetchUserDetails] but returns null when Firebase cannot be reached,
     * so callers never mistake "offline" for "fresh user with 3 free uploads".
     */
    suspend fun fetchUserDetailsOrNull(installationId: String): UserDetails? {
        val ref = userDetailsRef(installationId) ?: return null
        return try {
            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                parseUserDetailsSafe(snapshot)
            } else {
                UserDetails()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fetchUserDetailsOrNull notice: ${t.message}")
            null
        }
    }

    /**
     * Updates only the given fields under user/{installationId}/details.
     * Unlike saveUserDetails() this never overwrites the upload counters or the free trial count.
     */
    suspend fun updateUserDetailFields(installationId: String, fields: Map<String, Any>) {
        val ref = userDetailsRef(installationId) ?: return
        try {
            ref.updateChildren(fields).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed updating userDetails fields: ${t.message}")
        }
    }

    /**
     * Deletes the given fields under user/{installationId}/details (used to wipe credentials that older
     * app versions copied into Firebase).
     */
    suspend fun removeUserDetailFields(installationId: String, keys: List<String>) {
        val ref = userDetailsRef(installationId) ?: return
        try {
            val updates = HashMap<String, Any?>()
            keys.forEach { updates[it] = null }
            ref.updateChildren(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed removing userDetails fields: ${t.message}")
        }
    }

    /**
     * Saves or updates UserDetails in Firebase under user/{installationId}/details.
     */
    suspend fun saveUserDetails(installationId: String, details: UserDetails) {
        val ref = userDetailsRef(installationId) ?: return
        try {
            ref.setValue(details).await()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed saving userDetails: ${t.message}")
        }
    }

    /**
     * Registers an upload in the clean Firebase structure:
     * user/{installationId}/{images|videos|documents|files|apks}/{shortId}
     * and publishes the public resolver node in public_links/{shortId}.
     *
     * Returns a failure when the public link could not be saved, so the caller never shows a short link
     * that the website would answer with "not found".
     */
    suspend fun registerUpload(
        installationId: String,
        record: UploadRecord,
        category: MediaCategory,
        isFreeTier: Boolean
    ): Result<Unit> {
        val userNode = userRef(installationId)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        val publicLinks = publicLinksRef()
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))

        return try {
            val nodeName = categoryNode(category)
            val categoryRef = userNode.child(nodeName)
            val typeName = category.name.lowercase().removeSuffix("s")

            val uploadData = hashMapOf<String, Any>(
                "fileName" to record.fileName,
                "fileType" to typeName,
                "fileSize" to record.fileSize,
                "mimeType" to record.mimeType,
                "storageBucket" to record.storageBucket,
                "storagePath" to record.storagePath,
                "supabaseUrl" to record.effectiveSupabaseUrl,
                "shortUrl" to record.effectiveShortUrl,
                "uploadDate" to record.uploadDate,
                "uploadTime" to record.uploadTime,
                "timestamp" to record.createdAt,
                "shortId" to record.shortId,
                "isFreeTier" to isFreeTier
            )

            // File name used by the website for the download name / page title: the sanitized storage
            // name without the short id prefix (the original device file name stays private).
            val publicFileName = record.storagePath
                .substringAfterLast('/')
                .removePrefix("${record.shortId}_")

            val publicData = hashMapOf<String, Any>(
                "type" to typeName,
                "mimeType" to record.mimeType,
                "supabaseUrl" to record.effectiveSupabaseUrl,
                "createdAt" to ServerValue.TIMESTAMP,
                "active" to true
            )
            if (publicFileName.isNotBlank()) {
                publicData["fileName"] = publicFileName
            }

            withTimeout(REGISTER_TIMEOUT_MS) {
                // 1. private history entry
                categoryRef.child(record.shortId).setValue(uploadData).await()

                // 2. public resolver node (what https://ivu.gt.tc/<shortId> reads).
                //    On a retry the previous attempt may already have reached the server (and with create-only
                //    Firebase rules a second write would be refused), so an identical node counts as saved.
                try {
                    val existing = try {
                        publicLinks.child(record.shortId).get().await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                    val alreadyPublished = existing != null && existing.exists() &&
                        existing.child("supabaseUrl").getValue(String::class.java) == record.effectiveSupabaseUrl
                    if (!alreadyPublished) {
                        publicLinks.child(record.shortId).setValue(publicData).await()
                    }
                } catch (t: Throwable) {
                    // Do not leave a history entry whose link can never open.
                    try {
                        categoryRef.child(record.shortId).removeValue()
                    } catch (_: Throwable) {
                    }
                    throw t
                }
            }

            // 3. counters are best effort: a counter problem must not fail a finished upload
            try {
                withTimeout(COUNTER_TIMEOUT_MS) {
                    val detailsRef = userDetailsRef(installationId)
                    if (detailsRef != null) {
                        val fieldToIncrement = when (category) {
                            MediaCategory.IMAGES -> "totalPhotosUpload"
                            MediaCategory.VIDEOS -> "totalVideosUpload"
                            MediaCategory.DOCUMENTS -> "totalDocumentsUpload"
                            MediaCategory.FILES -> "totalFilesUpload"
                            MediaCategory.APKS -> "totalApksUpload"
                        }
                        detailsRef.child(fieldToIncrement).setValue(ServerValue.increment(1)).await()
                        if (isFreeTier) {
                            detailsRef.child("freeTierUploadsUsed").setValue(ServerValue.increment(1)).await()
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                Log.w(TAG, "Counter update timed out")
            } catch (t: Throwable) {
                Log.w(TAG, "Counter update notice: ${t.message}")
            }

            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timed out while registering upload")
            Result.failure(IllegalStateException("Timed out while saving the link. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register upload in Firebase: ${t.message}")
            Result.failure(IllegalStateException(describeError(t), t))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Phone identity: one profile per phone, kept after uninstall / "clear data"
    // ---------------------------------------------------------------------------------------------

    /** The installationId registered for this phone (device key = hash of the Android ID), or null when none yet. */
    suspend fun fetchDeviceProfile(deviceKey: String): Result<String?> {
        val db = getDatabase() ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            val snapshot = withTimeout(LOOKUP_TIMEOUT_MS) {
                db.getReference(NODE_DEVICE_PROFILES).child(deviceKey).child("installationId").get().await()
            }
            Result.success(snapshot.getValue(String::class.java)?.takeIf { it.isNotBlank() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out while checking this phone"))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Saves the FIRST installationId of this phone. The rules make this node create-only, so the first ID stays
     * forever. Returns the ID that is registered now (the existing one when the phone was registered meanwhile).
     */
    suspend fun registerDeviceProfile(deviceKey: String, installationId: String): Result<String> {
        val db = getDatabase() ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            withTimeout(LOOKUP_TIMEOUT_MS) {
                db.getReference(NODE_DEVICE_PROFILES).child(deviceKey).setValue(
                    hashMapOf<String, Any>(
                        "installationId" to installationId,
                        "createdAt" to ServerValue.TIMESTAMP
                    )
                ).await()
            }
            Result.success(installationId)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out while registering this phone"))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // refused because it exists already: use the registered one
            val existing = fetchDeviceProfile(deviceKey).getOrNull()
            if (existing != null) Result.success(existing) else Result.failure(t)
        }
    }

    /** Free trial photos used from this network (hashed IP). Null when unknown (offline, rules). */
    suspend fun fetchIpFreeUsed(ipKey: String): Long? {
        val db = getDatabase() ?: return null
        return try {
            val snapshot = withTimeout(LOOKUP_TIMEOUT_MS) {
                db.getReference(NODE_IP_PROFILES).child(ipKey).child("freeUsed").get().await()
            }
            getNumberAsLong(snapshot)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            null
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun incrementIpFreeUsed(ipKey: String) {
        val db = getDatabase() ?: return
        try {
            withTimeout(COUNTER_TIMEOUT_MS) {
                db.getReference(NODE_IP_PROFILES).child(ipKey).child("freeUsed")
                    .setValue(ServerValue.increment(1)).await()
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (t: Throwable) {
            Log.w(TAG, "IP counter notice: ${t.message}")
        }
    }

    // ---- encrypted backup of the user's Supabase settings: user/<id>/cfg ----

    suspend fun fetchConfigBackup(installationId: String): Result<String?> {
        val ref = userRef(installationId)?.child(NODE_CONFIG_BACKUP)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            val snapshot = withTimeout(LOOKUP_TIMEOUT_MS) { ref.get().await() }
            Result.success(snapshot.getValue(String::class.java)?.takeIf { it.isNotBlank() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out"))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun saveConfigBackup(installationId: String, blob: String): Result<Unit> {
        val ref = userRef(installationId)?.child(NODE_CONFIG_BACKUP)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            withTimeout(LOOKUP_TIMEOUT_MS) { ref.setValue(blob).await() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out"))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun deleteConfigBackup(installationId: String): Result<Unit> {
        val ref = userRef(installationId)?.child(NODE_CONFIG_BACKUP)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            withTimeout(LOOKUP_TIMEOUT_MS) { ref.removeValue().await() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out"))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Removes an upload from Firebase: the private history entry (user/<id>/<category>/<shortId>) and the public
     * link (public_links/<shortId>) that the website reads.
     * Success value: true when both were removed, false when only the history entry could be removed
     * (the public link is refused by the database rules).
     */
    suspend fun deleteUpload(installationId: String, shortId: String, fileType: String): Result<Boolean> {
        val userNode = userRef(installationId)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        val publicLinks = publicLinksRef()
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        if (shortId.isBlank()) return Result.success(true)

        // The category folder is known from the file type; if it is not, every folder is cleaned (a no-op where empty).
        val normalizedType = fileType.lowercase().removeSuffix("s")
        val folders = HISTORY_FOLDERS.filter { it.removeSuffix("s") == normalizedType }
            .ifEmpty { HISTORY_FOLDERS }

        return try {
            withTimeout(DELETE_TIMEOUT_MS) {
                for (folder in folders) {
                    userNode.child(folder).child(shortId).removeValue().await()
                }
            }
            val linkRemoved = try {
                withTimeout(DELETE_TIMEOUT_MS) {
                    publicLinks.child(shortId).removeValue().await()
                }
                true
            } catch (e: TimeoutCancellationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Public link delete notice: ${t.message}")
                if (isPermissionDenied(t)) false else throw t
            }
            Result.success(linkRemoved)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out while deleting. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to delete upload in Firebase: ${t.message}")
            Result.failure(IllegalStateException(if (isPermissionDenied(t)) DELETE_RULES_HINT else describeError(t), t))
        }
    }

    /**
     * Loads the whole upload history of this phone ONCE (user/{installationId}). There is no live listener any
     * more: a listener downloaded the complete history again after every single change and kept it in memory.
     * The caller decides when to load (History screen opened, pull to refresh) and keeps the result.
     */
    suspend fun fetchAllUserUploads(installationId: String): Result<List<UploadRecord>> {
        val ref = userRef(installationId)
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            val snapshot = withTimeout(HISTORY_TIMEOUT_MS) { ref.get().await() }
            // parsing hundreds of records stays off the main thread
            val list = withContext(Dispatchers.Default) { parseUploads(snapshot, installationId) }
            Result.success(list)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out while loading the history. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "fetchAllUserUploads notice: ${t.message}")
            Result.failure(t)
        }
    }

    private fun parseUploads(snapshot: DataSnapshot, installationId: String): List<UploadRecord> {
        val list = mutableListOf<UploadRecord>()
        val categoryFolders = listOf("images", "videos", "documents", "files", "apks")
        for (folder in categoryFolders) {
            val folderSnapshot = snapshot.child(folder)
            for (child in folderSnapshot.children) {
                try {
                    val shortId = child.key ?: ""
                    val fileName = child.child("fileName").getValue(String::class.java) ?: shortId
                    val uploadDate = child.child("uploadDate").getValue(String::class.java) ?: ""
                    val uploadTime = child.child("uploadTime").getValue(String::class.java) ?: ""
                    val type = child.child("fileType").getValue(String::class.java)
                        ?: child.child("type").getValue(String::class.java) ?: folder
                    val supabaseUrl = child.child("supabaseUrl").getValue(String::class.java) ?: ""
                    val shortUrl = child.child("shortUrl").getValue(String::class.java) ?: "https://ivu.gt.tc/$shortId"
                    val fileSize = child.child("fileSize").getValue(Long::class.java) ?: 0L
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val isFreeTier = child.child("isFreeTier").getValue(Boolean::class.java) ?: false
                    val mimeType = child.child("mimeType").getValue(String::class.java)
                        ?: LinkRules.mimeFromExtension(fileName)
                    val storagePath = child.child("storagePath").getValue(String::class.java) ?: ""
                    val storageBucket = child.child("storageBucket").getValue(String::class.java) ?: ""

                    list.add(
                        UploadRecord(
                            shortId = shortId,
                            ownerInstallationId = installationId,
                            fileName = fileName,
                            fileType = folder.replaceFirstChar { it.uppercase() },
                            type = type,
                            mimeType = mimeType,
                            storageBucket = storageBucket,
                            storagePath = storagePath,
                            supabaseUrl = supabaseUrl,
                            originalSupabaseUrl = supabaseUrl,
                            shortUrl = shortUrl,
                            publicUrl = shortUrl,
                            fileSize = fileSize,
                            uploadDate = uploadDate,
                            uploadTime = uploadTime,
                            createdAt = timestamp,
                            timestamp = timestamp,
                            isFreeTier = isFreeTier,
                            options = parseOptions(child, isPublicNode = false)
                        )
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Parsing item notice: ${t.message}")
                }
            }
        }
        list.sortByDescending { it.createdAt }
        return list
    }

    /**
     * Self check for Settings: tells exactly which Firebase step works and which one is refused
     * (usually the database security rules).
     */
    suspend fun runConnectionCheck(installationId: String): List<FirebaseCheckLine> {
        val lines = mutableListOf<FirebaseCheckLine>()
        val db = getDatabase()
        if (db == null) {
            lines += FirebaseCheckLine(false, "Firebase is not set up in this build.")
            return lines
        }
        lines += FirebaseCheckLine(true, "Firebase project: ${db.app.options.projectId ?: "unknown"}")

        lines += probe("Read short links (public_links)") {
            db.getReference(NODE_PUBLIC_LINKS).child("__ivu_check__").get().await()
        }
        lines += probe("Save history (user)") {
            db.getReference(NODE_USER).child(installationId).child("_check")
                .setValue(ServerValue.TIMESTAMP).await()
        }
        val checkKey = "__chk_" + installationId.filter { it.isLetterOrDigit() }.take(8)
        lines += probe("Create short link (public_links)") {
            val ref = db.getReference(NODE_PUBLIC_LINKS).child(checkKey)
            val existing = try {
                ref.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            // create-only rules refuse a second write to the same key, so an earlier test node counts as OK
            if (existing == null || !existing.exists()) {
                ref.setValue(
                    hashMapOf<String, Any>(
                        "type" to "check",
                        "active" to false,
                        "createdAt" to ServerValue.TIMESTAMP
                    )
                ).await()
            }
        }
        return lines
    }

    private suspend fun probe(label: String, block: suspend () -> Unit): FirebaseCheckLine {
        return try {
            withTimeout(10_000L) { block() }
            FirebaseCheckLine(true, "$label: OK")
        } catch (e: TimeoutCancellationException) {
            FirebaseCheckLine(false, "$label: no answer (check your internet)")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val reason = if (isPermissionDenied(t)) "BLOCKED by Firebase rules" else (t.message ?: "error")
            FirebaseCheckLine(false, "$label: $reason")
        }
    }

    /**
     * Reads a public link exactly like the website does (same node paths, same field fallbacks),
     * so the in-app viewer shows what the browser shows.
     */
    suspend fun lookupPublicLink(id: String): PublicLinkLookup {
        val db = getDatabase() ?: return PublicLinkLookup.Error
        val paths = listOf(NODE_PUBLIC_LINKS, "photo_video_url/$NODE_PUBLIC_LINKS")
        for ((index, path) in paths.withIndex()) {
            try {
                val snap = withTimeout(LOOKUP_TIMEOUT_MS) {
                    db.getReference(path).child(id).get().await()
                }
                if (snap.exists()) {
                    val record = parsePublicLinkRecord(snap)
                    if (record != null) return PublicLinkLookup.Found(record)
                }
            } catch (e: TimeoutCancellationException) {
                if (index == 0) return PublicLinkLookup.Error
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "lookupPublicLink notice ($path): ${t.message}")
                // An error on the secondary path must not hide a plain "not found".
                if (index == 0) return PublicLinkLookup.Error
            }
        }
        return PublicLinkLookup.Missing
    }

    private fun parsePublicLinkRecord(snap: DataSnapshot): PublicLinkRecord? {
        fun text(vararg keys: String): String {
            for (key in keys) {
                val v = snap.child(key).value
                if (v is String && v.isNotEmpty()) return v
            }
            return ""
        }

        val url = text("originalUrl", "supabaseUrl", "url")
        if (url.isEmpty()) return null

        val activeRaw = snap.child("active").value
        val active = !(activeRaw == false ||
                (activeRaw is Number && activeRaw.toLong() == 0L) ||
                (activeRaw is String && (activeRaw == "0" || activeRaw.equals("false", ignoreCase = true))))

        val type = text("fileType", "type").lowercase().ifEmpty { null }
        return PublicLinkRecord(
            url = url,
            type = type,
            mime = text("mimeType", "mime").lowercase(),
            fileName = text("fileName"),
            storagePath = text("storagePath"),
            active = active,
            options = parseOptions(snap, isPublicNode = true)
        )
    }

    /** Reads the advanced options of a Firebase node (public link or history entry). Missing fields = not set. */
    private fun parseOptions(node: DataSnapshot, isPublicNode: Boolean): LinkOptions {
        fun number(key: String): Long = (node.child(key).value as? Number)?.toLong() ?: 0L
        fun text(key: String): String = (node.child(key).value as? String)?.trim().orEmpty()
        val button = node.child("button")
        val allowRaw = node.child("allowDownload").value
        val hash = if (isPublicNode) text("passwordHash") else ""
        val flag = node.child("hasPassword").value
        return LinkOptions(
            expiresAt = number("expiresAt"),
            maxViews = number("maxViews"),
            views = number("views"),
            passwordHash = hash,
            passwordSalt = if (isPublicNode) text("passwordSalt") else "",
            passwordIter = number("passwordIter").toInt(),
            hasPassword = flag == true || hash.isNotEmpty(),
            bgColor = text("bgColor"),
            buttonText = (button.child("text").value as? String).orEmpty(),
            buttonColor = (button.child("color").value as? String).orEmpty(),
            buttonUrl = (button.child("url").value as? String).orEmpty(),
            pageTitle = text("pageTitle"),
            pageMessage = text("pageMessage"),
            allowDownload = !(allowRaw == false || (allowRaw is String && allowRaw.equals("false", ignoreCase = true)))
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Advanced link options: custom address, expiry, password, view limit, page design
    // ---------------------------------------------------------------------------------------------

    /** Is https://ivu.gt.tc/<slug> still free? One read of public_links/<slug>. */
    suspend fun isLinkNameFree(slug: String): Result<Boolean> {
        val links = publicLinksRef()
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        return try {
            val snap = withTimeout(LOOKUP_TIMEOUT_MS) { links.child(slug).get().await() }
            Result.success(!snap.exists())
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("No answer. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(describeError(t), t))
        }
    }

    private fun folderOf(record: UploadRecord): String? {
        val candidates = listOf(record.fileType, record.type, record.shortId.substringBefore('_'))
        for (candidate in candidates) {
            val key = candidate.lowercase().removeSuffix("s")
            if (key.isEmpty()) continue
            HISTORY_FOLDERS.firstOrNull { it.removeSuffix("s") == key }?.let { return it }
        }
        return null
    }

    private fun snapshotToMap(snap: DataSnapshot): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        (snap.value as? Map<*, *>)?.forEach { (k, v) -> if (k is String) map[k] = v }
        return map
    }

    /**
     * Saves the advanced options of a link and, when [newSlug] differs from the current id, moves the link to the
     * new address (https://ivu.gt.tc/<newSlug>). Both Firebase places are updated: public_links/<id> (what the
     * website reads) and the history entry user/<uid>/<category>/<id>.
     *
     * Works with the existing database rules (public links can only be created or deleted, not changed):
     *  - new address: ONE atomic update removes the old nodes and creates the new ones; if the name is taken in the
     *    meantime Firebase refuses the whole update and nothing changes;
     *  - same address: the public node is removed and written again with the new options.
     * Returns the changed record.
     */
    suspend fun applyLinkOptions(
        installationId: String,
        record: UploadRecord,
        newSlug: String,
        options: LinkOptions
    ): Result<UploadRecord> {
        val db = getDatabase()
            ?: return Result.failure(IllegalStateException("Firebase Database unavailable"))
        val folder = folderOf(record)
            ?: return Result.failure(IllegalStateException("The file type of this link is unknown."))
        val oldId = record.shortId
        val renamed = newSlug != oldId
        val fullUrl = LinkSlugRules.fullUrl(newSlug)
        val userPath = "$NODE_USER/$installationId/$folder"

        return try {
            withTimeout(REGISTER_TIMEOUT_MS) {
                val userSnap = db.getReference(userPath).child(oldId).get().await()
                val publicSnap = db.getReference(NODE_PUBLIC_LINKS).child(oldId).get().await()

                // history entry: what is there now (or rebuilt from the record), with the new address + options
                val userMap = snapshotToMap(userSnap)
                if (userMap.isEmpty()) {
                    userMap["fileName"] = record.fileName
                    userMap["fileType"] = record.type.ifEmpty { record.fileType }.lowercase().removeSuffix("s")
                    userMap["fileSize"] = record.fileSize
                    userMap["mimeType"] = record.mimeType
                    userMap["storageBucket"] = record.storageBucket
                    userMap["storagePath"] = record.storagePath
                    userMap["supabaseUrl"] = record.effectiveSupabaseUrl
                    userMap["uploadDate"] = record.uploadDate
                    userMap["uploadTime"] = record.uploadTime
                    userMap["timestamp"] = record.createdAt
                    userMap["isFreeTier"] = record.isFreeTier
                }
                userMap["shortId"] = newSlug
                userMap["shortUrl"] = fullUrl
                options.applyTo(userMap, publicNode = false)

                // public node: what the website reads
                val originalPublic = snapshotToMap(publicSnap)
                val publicMap = LinkedHashMap(originalPublic)
                if (publicMap.isEmpty()) {
                    publicMap["type"] = record.type.ifEmpty { record.fileType }.lowercase().removeSuffix("s")
                    publicMap["mimeType"] = record.mimeType
                    publicMap["supabaseUrl"] = record.effectiveSupabaseUrl
                    publicMap["createdAt"] = ServerValue.TIMESTAMP
                    publicMap["active"] = true
                    val name = record.storagePath.substringAfterLast('/').removePrefix("${record.shortId}_")
                    if (name.isNotBlank()) publicMap["fileName"] = name
                }
                options.applyTo(publicMap, publicNode = true)

                if (renamed) {
                    val updates = HashMap<String, Any?>()
                    updates["$NODE_PUBLIC_LINKS/$oldId"] = null
                    updates["$NODE_PUBLIC_LINKS/$newSlug"] = publicMap
                    updates["$userPath/$oldId"] = null
                    updates["$userPath/$newSlug"] = userMap
                    db.reference.updateChildren(updates).await()
                } else {
                    // the page of the website first (it is what visitors see), then the entry of the History
                    replacePublicNode(db.getReference(NODE_PUBLIC_LINKS).child(oldId), publicMap, originalPublic)
                    db.getReference(userPath).child(oldId).setValue(userMap).await()
                }
            }
            Result.success(
                record.copy(
                    shortId = newSlug,
                    publicUrl = fullUrl,
                    shortUrl = fullUrl,
                    options = options
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Timed out while saving. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "applyLinkOptions notice: ${t.message}")
            val message = if (renamed && isPermissionDenied(t)) {
                "This name was just taken by someone else. Choose another one."
            } else {
                describeError(t)
            }
            Result.failure(IllegalStateException(message, t))
        }
    }

    /**
     * Saves the new public node of a link.
     *  - Current rules (firebase-rules.json): the node is replaced in ONE step, the website never sees a missing link.
     *  - Older rules only allow "create" and "delete": the node is deleted and written again. This part cannot be
     *    cancelled half way (a time out between the two steps would leave the link without its page), and if the
     *    new node is refused the old one is put back.
     */
    private suspend fun replacePublicNode(
        ref: DatabaseReference,
        newMap: Map<String, Any?>,
        oldMap: Map<String, Any?>
    ) {
        try {
            withTimeout(PUBLIC_WRITE_TIMEOUT_MS) { ref.setValue(newMap).await() }
            return
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (!isPermissionDenied(t) || oldMap.isEmpty()) throw t
        }

        withContext(NonCancellable) {
            ref.removeValue()          // sent first: Firebase keeps the order of the writes
            val write = ref.setValue(newMap)
            try {
                withTimeout(PUBLIC_WRITE_TIMEOUT_MS) { write.await() }
            } catch (e: TimeoutCancellationException) {
                // Slow connection: both steps are already queued and are completed when the connection is back.
                throw IllegalStateException("The connection is slow. The change was sent and is saved as soon as the internet is back.")
            } catch (t: Throwable) {
                ref.setValue(oldMap)    // refused: the old page is put back
                throw t
            }
        }
    }

    /**
     * Resolves public link metadata from public_links/{shortId} for deep link preview.
     */
    suspend fun resolvePublicLink(shortId: String): PublicLinkDetails {
        val cleanId = shortId.trim().removePrefix("/").removePrefix("#")
        val defaultUrl = "https://ivu.gt.tc/$cleanId"
        val linksRef = publicLinksRef() ?: return PublicLinkDetails(shortId = cleanId, shortUrl = defaultUrl)

        return try {
            val snap = linksRef.child(cleanId).get().await()
            if (snap.exists()) {
                val type = snap.child("type").getValue(String::class.java).orEmpty()
                val mimeType = snap.child("mimeType").getValue(String::class.java).orEmpty()
                val supabaseUrl = snap.child("supabaseUrl").getValue(String::class.java).orEmpty()
                val active = snap.child("active").getValue(Boolean::class.java) ?: true
                val createdAt = snap.child("createdAt").getValue(Long::class.java) ?: 0L
                PublicLinkDetails(
                    shortId = cleanId,
                    shortUrl = defaultUrl,
                    supabaseUrl = supabaseUrl,
                    type = type,
                    mimeType = mimeType,
                    active = active,
                    createdAt = createdAt
                )
            } else {
                PublicLinkDetails(shortId = cleanId, shortUrl = defaultUrl)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "resolvePublicLink notice: ${t.message}")
            PublicLinkDetails(shortId = cleanId, shortUrl = defaultUrl)
        }
    }
}
