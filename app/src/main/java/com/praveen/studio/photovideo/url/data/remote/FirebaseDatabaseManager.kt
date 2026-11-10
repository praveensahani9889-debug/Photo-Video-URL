package com.praveen.studio.photovideo.url.data.remote

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.PublicLinkDetails
import com.praveen.studio.photovideo.url.data.model.PublicLinkLookup
import com.praveen.studio.photovideo.url.data.model.PublicLinkRecord
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
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

        private const val SHORT_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val SHORT_ID_LENGTH = 7

        private const val REGISTER_TIMEOUT_MS = 20_000L
        private const val COUNTER_TIMEOUT_MS = 8_000L
        private const val LOOKUP_TIMEOUT_MS = 10_000L
        private const val DELETE_TIMEOUT_MS = 12_000L
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
     * Initializes default `app/` structure in Firebase if missing.
     * Developer can manually populate `app/backend/supabaseApi` & `supabaseKey` in Firebase console.
     */
    suspend fun initializeAppMetadataIfNeeded() {
        if (metadataChecked) return
        val root = appRef() ?: return
        try {
            val backendSnap = root.child("backend").get().await()
            if (!backendSnap.exists()) {
                root.child("backend").setValue(
                    mapOf(
                        "supabaseApi" to "",
                        "supabaseKey" to "",
                        "bucket" to "media"
                    )
                ).await()
            }

            val aboutSnap = root.child("about").get().await()
            if (!aboutSnap.exists()) {
                root.child("about").setValue(
                    mapOf(
                        "appName" to "Photo Video URL",
                        "contact" to "praveensahani9273@gmail.com",
                        "supportEmail" to "praveensahani9273@gmail.com",
                        "domain" to "https://ivu.gt.tc",
                        "version" to "1.0.0"
                    )
                ).await()
            }
            metadataChecked = true
        } catch (t: Throwable) {
            Log.w(TAG, "initializeAppMetadata notice: ${t.message}")
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
            val freeMax = getNumberAsLong(snapshot.child("freeTierMax")).takeIf { it > 0 } ?: 3L

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
     * Observes user details in real-time.
     */
    fun observeUserDetails(installationId: String): Flow<UserDetails> = callbackFlow {
        val ref = userDetailsRef(installationId)
        if (ref == null) {
            trySend(UserDetails())
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val details = parseUserDetailsSafe(snapshot)
                    trySend(details)
                } catch (t: Throwable) {
                    Log.w(TAG, "observeUserDetails onDataChange notice: ${t.message}")
                    trySend(UserDetails())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeUserDetails cancelled: ${error.message}")
                trySend(UserDetails())
            }
        }

        try {
            ref.addValueEventListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "addValueEventListener error: ${t.message}")
            trySend(UserDetails())
        }

        awaitClose {
            try {
                ref.removeEventListener(listener)
            } catch (_: Throwable) {}
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
     * Observes all user uploads across images, videos, documents, files, and apks in real-time.
     * Firebase delivers snapshots on the main thread; parsing them (hundreds of records) happens on a
     * background dispatcher so scrolling and typing stay smooth.
     */
    fun observeAllUserUploads(installationId: String): Flow<List<UploadRecord>> =
        observeUserSnapshots(installationId)
            .conflate()
            .map { snapshot ->
                if (snapshot == null) emptyList() else parseUploads(snapshot, installationId)
            }
            .flowOn(Dispatchers.Default)

    private fun observeUserSnapshots(installationId: String): Flow<DataSnapshot?> = callbackFlow {
        val userNodeRef = userRef(installationId)
        if (userNodeRef == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeAllUserUploads cancelled: code=${error.code} ${error.message}")
                trySend(null)
            }
        }

        try {
            userNodeRef.addValueEventListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "addValueEventListener error: ${t.message}")
            trySend(null)
        }

        awaitClose {
            try {
                userNodeRef.removeEventListener(listener)
            } catch (_: Throwable) {}
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
                            isFreeTier = isFreeTier
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
            active = active
        )
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
