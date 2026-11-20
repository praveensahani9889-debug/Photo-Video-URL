package com.praveen.studio.photovideo.url.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * One Supabase project of the user (URL + anon key + bucket). A user can save several of them (default max 5,
 * switchable from Firebase `app/backend/maxSupabaseAccounts`). Uploads go to the active one; when it is full the
 * app switches to the next one by itself.
 */
data class SupabaseAccount(
    val id: String,
    val label: String = "",
    val projectUrl: String = "",
    val anonKey: String = "",
    val bucketName: String = "media",
    /** Storage size of the Supabase plan in MB (Free plan = 1024 MB = 1 GB). The anon key cannot read the plan. */
    val capacityMb: Long = DEFAULT_CAPACITY_MB,
    /** Estimated bytes stored in this project: files uploaded with the app + [externalBytes]. */
    val usedBytes: Long = 0L,
    /** Bytes in the bucket that were not uploaded with the app (found by "Scan storage"). */
    val externalBytes: Long = 0L,
    /** Set when Supabase itself refused an upload because the storage is full. */
    val full: Boolean = false,
    val createdAt: Long = 0L,
    val lastScanAt: Long = 0L
) {
    val config: SupabaseConfig
        get() = SupabaseConfig(projectUrl = projectUrl, anonKey = anonKey, bucketName = bucketName)

    val isUsable: Boolean
        get() = config.isConfigured && !config.isPlaceholder

    /** Host of the project, e.g. "abcd.supabase.co" (empty when the URL is not valid). */
    val host: String
        get() = hostOf(config.cleanUrl)

    val displayName: String
        get() = label.trim().ifBlank { host.substringBefore('.').ifBlank { "Supabase" } }

    val capacityBytes: Long
        get() = capacityMb.coerceAtLeast(1L) * 1024L * 1024L

    val freeBytes: Long
        get() = (capacityBytes - usedBytes).coerceAtLeast(0L)

    /** 0f..1f (can be above 1f in the data, the value is clamped for progress bars). */
    val usedFraction: Float
        get() = (usedBytes.toDouble() / capacityBytes.toDouble()).toFloat().coerceIn(0f, 1f)

    val usedPercent: Int
        get() = (usedBytes.toDouble() * 100.0 / capacityBytes.toDouble()).toInt().coerceIn(0, 100)

    /** True when [extraBytes] more would not fit into the estimated free space. */
    fun wouldOverflow(extraBytes: Long): Boolean = usedBytes + extraBytes.coerceAtLeast(0L) > capacityBytes

    /** Full, or so close to full that the app should move on to the next project. */
    val isFullOrNearly: Boolean
        get() = full || usedBytes >= capacityBytes / 100L * 98L

    /**
     * One Supabase project is ONE entry: the same project URL (or the same anon key) cannot be added a second time,
     * whatever the name or the bucket is. To use another bucket of the project, edit the entry.
     */
    fun isDuplicateOf(other: SupabaseAccount): Boolean {
        if (id == other.id) return false
        val sameHost = host.isNotBlank() && host.equals(other.host, ignoreCase = true)
        val key = anonKey.trim()
        val sameKey = key.isNotEmpty() && key == other.anonKey.trim()
        return sameHost || sameKey
    }

    fun sameProject(other: SupabaseAccount): Boolean =
        host.equals(other.host, ignoreCase = true) && bucketName.trim().equals(other.bucketName.trim(), ignoreCase = true)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("url", projectUrl)
        .put("key", anonKey)
        .put("bucket", bucketName)
        .put("capMb", capacityMb)
        .put("used", usedBytes)
        .put("ext", externalBytes)
        .put("full", full)
        .put("created", createdAt)
        .put("scan", lastScanAt)

    /** Same as [toJson] without the usage numbers: this is what the encrypted Firebase backup stores. */
    fun toBackupJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("url", projectUrl)
        .put("key", anonKey)
        .put("bucket", bucketName)
        .put("capMb", capacityMb)
        .put("created", createdAt)

    companion object {
        const val DEFAULT_CAPACITY_MB = 1024L
        const val DEFAULT_MAX_ACCOUNTS = 5

        fun hostOf(url: String): String {
            if (url.isBlank()) return ""
            return try {
                URI(url).host?.lowercase().orEmpty()
            } catch (_: Exception) {
                ""
            }
        }

        fun fromJson(obj: JSONObject): SupabaseAccount = SupabaseAccount(
            id = obj.optString("id").ifBlank { newId() },
            label = obj.optString("label"),
            projectUrl = obj.optString("url"),
            anonKey = obj.optString("key"),
            bucketName = obj.optString("bucket").ifBlank { "media" },
            capacityMb = obj.optLong("capMb", DEFAULT_CAPACITY_MB).let { if (it <= 0L) DEFAULT_CAPACITY_MB else it },
            usedBytes = obj.optLong("used", 0L).coerceAtLeast(0L),
            externalBytes = obj.optLong("ext", 0L).coerceAtLeast(0L),
            full = obj.optBoolean("full", false),
            createdAt = obj.optLong("created", 0L),
            lastScanAt = obj.optLong("scan", 0L)
        )

        fun listToJson(list: List<SupabaseAccount>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String?): List<SupabaseAccount> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { fromJson(it) } }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        fun newId(): String = "sb_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
    }
}

/** What happened to the Supabase accounts that the user should be told about. */
sealed interface AccountEvent {
    val message: String

    data class Switched(val from: String, val to: String, val reasonFull: Boolean) : AccountEvent {
        override val message: String
            get() = if (reasonFull) "$from is full. Switched to $to automatically." else "Switched to $to."
    }

    data class AllFull(val lastName: String) : AccountEvent {
        override val message: String
            get() = "All your Supabase storages are full. Add another Supabase in Settings."
    }

    /** The only project that is left is almost full: time to add another one. */
    data class NearlyFull(val name: String) : AccountEvent {
        override val message: String
            get() = "$name is almost full. Add another Supabase in Settings so uploads can switch automatically."
    }
}
