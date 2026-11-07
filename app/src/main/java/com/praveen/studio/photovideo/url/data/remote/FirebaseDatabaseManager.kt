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
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UserDetails
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

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
            val name = snapshot.child("name").getValue(String::class.java) ?: "Praveen"
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

            val uploadData = mapOf(
                "fileName" to record.fileName,
                "fileType" to category.name.lowercase().removeSuffix("s"),
                "fileSize" to record.fileSize,
                "supabaseUrl" to record.effectiveSupabaseUrl,
                "shortUrl" to record.effectiveShortUrl,
                "uploadDate" to record.uploadDate,
                "uploadTime" to record.uploadTime,
                "timestamp" to record.createdAt,
                "shortId" to record.shortId,
                "isFreeTier" to isFreeTier
            )

            categoryRef.child(record.shortId).setValue(uploadData).await()

            // 2. Save public resolver node
            val publicData = mapOf(
                "type" to category.name.lowercase().removeSuffix("s"),
                "mimeType" to record.mimeType,
                "supabaseUrl" to record.effectiveSupabaseUrl,
                "createdAt" to ServerValue.TIMESTAMP,
                "active" to true
            )
            publicLinks.child(record.shortId).setValue(publicData).await()

            // 3. Update details counters
            val detailsRef = userDetailsRef(installationId)
            if (detailsRef != null) {
                val fieldToIncrement = when (category) {
                    MediaCategory.IMAGES -> "totalPhotosUpload"
                    MediaCategory.VIDEOS -> "totalVideosUpload"
                    MediaCategory.DOCUMENTS -> "totalDocumentsUpload"
                    MediaCategory.FILES -> "totalFilesUpload"
                    MediaCategory.APKS -> "totalApksUpload"
                }
                detailsRef.child(fieldToIncrement).setValue(ServerValue.increment(1))

                if (isFreeTier) {
                    detailsRef.child("freeTierUploadsUsed").setValue(ServerValue.increment(1))
                }
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register upload in Firebase: ${t.message}")
            Result.failure(t)
        }
    }

    /**
     * Observes all user uploads across images, videos, documents, files, and apks in real-time.
     */
    fun observeAllUserUploads(installationId: String): Flow<List<UploadRecord>> = callbackFlow {
        val userNodeRef = userRef(installationId)
        if (userNodeRef == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
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

                            list.add(
                                UploadRecord(
                                    shortId = shortId,
                                    ownerInstallationId = installationId,
                                    fileName = fileName,
                                    fileType = folder.replaceFirstChar { it.uppercase() },
                                    type = type,
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
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeAllUserUploads cancelled: ${error.message}")
                trySend(emptyList())
            }
        }

        try {
            userNodeRef.addValueEventListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "addValueEventListener error: ${t.message}")
            trySend(emptyList())
        }

        awaitClose {
            try {
                userNodeRef.removeEventListener(listener)
            } catch (_: Throwable) {}
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
