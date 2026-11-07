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

class FirebaseDatabaseManager(
    private val database: FirebaseDatabase = try {
        FirebaseDatabase.getInstance("https://photo-video-url-default-rtdb.firebaseio.com")
    } catch (_: Exception) {
        FirebaseDatabase.getInstance()
    }
) {
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

    private val userRootRef: DatabaseReference
        get() = database.getReference(NODE_USER)

    private val appRef: DatabaseReference
        get() = database.getReference(NODE_APP)

    private val publicLinksRef: DatabaseReference
        get() = database.getReference(NODE_PUBLIC_LINKS)

    private fun userRef(installationId: String): DatabaseReference {
        return userRootRef.child(installationId)
    }

    private fun userDetailsRef(installationId: String): DatabaseReference {
        return userRef(installationId).child(NODE_DETAILS)
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
        try {
            val backendSnap = appRef.child("backend").get().await()
            if (!backendSnap.exists()) {
                appRef.child("backend").setValue(
                    mapOf(
                        "supabaseApi" to "",
                        "supabaseKey" to "",
                        "bucket" to "media"
                    )
                ).await()
            }

            val aboutSnap = appRef.child("about").get().await()
            if (!aboutSnap.exists()) {
                appRef.child("about").setValue(
                    mapOf(
                        "appName" to "Photo Video URL",
                        "contact" to "praveensahani9273@gmail.com",
                        "supportEmail" to "praveensahani9273@gmail.com",
                        "domain" to "https://ivu.gt.tc",
                        "version" to "1.0.0"
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "initializeAppMetadata notice: ${e.message}")
        }
    }

    /**
     * Fetches Supabase API and Key configured manually in Firebase under `app/backend`.
     * Returns null if not set or blank.
     */
    suspend fun fetchAppBackendConfig(): SupabaseConfig? {
        return try {
            val snap = appRef.child("backend").get().await()
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
        } catch (e: Exception) {
            Log.w(TAG, "fetchAppBackendConfig notice: ${e.message}")
            null
        }
    }

    /**
     * Generates a collision-checked random URL-safe ID.
     */
    suspend fun generateUniqueShortId(category: MediaCategory): String {
        val prefix = category.shortIdPrefix
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
                val snapshot = publicLinksRef.child(candidate).get().await()
                if (!snapshot.exists()) {
                    return candidate
                }
            } catch (e: Exception) {
                Log.w(TAG, "Collision check notice: ${e.message}")
                return candidate
            }
        }
        return "${prefix}${System.currentTimeMillis().toString(36)}"
    }

    /**
     * Fetches user details (upload counters, free tier count, custom credentials) using Installation ID.
     */
    suspend fun fetchUserDetails(installationId: String): UserDetails {
        return try {
            val snapshot = userDetailsRef(installationId).get().await()
            if (snapshot.exists()) {
                snapshot.getValue(UserDetails::class.java) ?: UserDetails()
            } else {
                // Check legacy node if present
                val legacySnapshot = userRef(installationId).child(NODE_LEGACY_USER_DETAILS).get().await()
                legacySnapshot.getValue(UserDetails::class.java) ?: UserDetails()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserDetails notice: ${e.message}")
            UserDetails()
        }
    }

    /**
     * Same as [fetchUserDetails] but returns null when Firebase cannot be reached,
     * so callers never mistake "offline" for "fresh user with 3 free uploads".
     */
    suspend fun fetchUserDetailsOrNull(installationId: String): UserDetails? {
        return try {
            val snapshot = userDetailsRef(installationId).get().await()
            if (snapshot.exists()) {
                snapshot.getValue(UserDetails::class.java) ?: UserDetails()
            } else {
                UserDetails()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserDetailsOrNull notice: ${e.message}")
            null
        }
    }

    /**
     * Updates only the given fields under user/{installationId}/details.
     * Unlike saveUserDetails() this never overwrites the upload counters or the free trial count.
     */
    suspend fun updateUserDetailFields(installationId: String, fields: Map<String, Any>) {
        try {
            userDetailsRef(installationId).updateChildren(fields).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating userDetails fields: ${e.message}")
        }
    }

    /**
     * Observes user details in real-time.
     */
    fun observeUserDetails(installationId: String): Flow<UserDetails> = callbackFlow {
        val ref = userDetailsRef(installationId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val details = snapshot.getValue(UserDetails::class.java) ?: UserDetails()
                trySend(details)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeUserDetails cancelled: ${error.message}")
                trySend(UserDetails())
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Saves or updates UserDetails in Firebase under user/{installationId}/details.
     */
    suspend fun saveUserDetails(installationId: String, details: UserDetails) {
        try {
            userDetailsRef(installationId).setValue(details).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving userDetails: ${e.message}")
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
        return try {
            val nodeName = categoryNode(category)
            val categoryRef = userRef(installationId).child(nodeName)

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
            publicLinksRef.child(record.shortId).setValue(publicData).await()

            // 3. Update details counters
            val detailsRef = userDetailsRef(installationId)
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

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register upload in Firebase: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Observes all user uploads across images, videos, documents, files, and apks in real-time.
     */
    fun observeAllUserUploads(installationId: String): Flow<List<UploadRecord>> = callbackFlow {
        val userNodeRef = userRef(installationId)
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
                        } catch (e: Exception) {
                            Log.w(TAG, "Parsing item notice: ${e.message}")
                        }
                    }
                }

                list.sortByDescending { it.createdAt }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeAllUserUploads cancelled: ${error.message}")
                trySend(emptyList())
                close()
            }
        }

        userNodeRef.addValueEventListener(listener)
        awaitClose { userNodeRef.removeEventListener(listener) }
    }

    /**
     * Resolves public link metadata from public_links/{shortId} for deep link preview.
     */
    suspend fun resolvePublicLink(shortId: String): PublicLinkDetails {
        val cleanId = shortId.trim().removePrefix("/").removePrefix("#")
        val defaultUrl = "https://ivu.gt.tc/$cleanId"
        return try {
            val snap = publicLinksRef.child(cleanId).get().await()
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
        } catch (e: Exception) {
            Log.w(TAG, "resolvePublicLink notice: ${e.message}")
            PublicLinkDetails(shortId = cleanId, shortUrl = defaultUrl)
        }
    }
}
