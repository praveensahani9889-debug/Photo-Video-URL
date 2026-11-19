package com.praveen.studio.photovideo.url.data.remote

import com.praveen.studio.photovideo.url.data.model.UploadRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The upload history as loaded from Firebase, kept in memory ONCE for the whole app.
 *
 * It replaces a live listener on the complete `user/<id>` node, which downloaded the whole history again after
 * every change and held several copies of it in memory. Now:
 *  - the history is loaded when the History screen opens and the copy is older than [TTL_MS], or on a manual refresh;
 *  - an upload adds its record here ([upsert]), a delete removes it ([remove]) - no new download is needed.
 */
object HistoryRemoteStore {
    private const val TTL_MS = 5 * 60_000L

    private val _records = MutableStateFlow<List<UploadRecord>>(emptyList())
    val records: StateFlow<List<UploadRecord>> = _records.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val mutex = Mutex()
    private val lock = Any()

    @Volatile
    private var lastFetchAt = 0L

    @Volatile
    private var forId = ""

    /** Ids removed while a load was running (that load may still contain them). */
    private val removedDuringLoad = HashSet<String>()

    /**
     * Loads the history from Firebase when the copy is stale (or [force]).
     * Returns the full list when it was loaded now, null when nothing was loaded (still fresh, offline or error).
     */
    suspend fun refresh(
        installationId: String,
        manager: FirebaseDatabaseManager,
        force: Boolean = false
    ): List<UploadRecord>? {
        return mutex.withLock {
            if (forId != installationId) {
                forId = installationId
                lastFetchAt = 0L
                _records.value = emptyList()
            }
            val startedAt = System.currentTimeMillis()
            if (!force && lastFetchAt != 0L && startedAt - lastFetchAt < TTL_MS) return@withLock null

            synchronized(lock) { removedDuringLoad.clear() }
            _refreshing.value = true
            try {
                val result = manager.fetchAllUserUploads(installationId)
                val fetched = result.getOrNull() ?: return@withLock null

                val merged = synchronized(lock) {
                    val ids = fetched.asSequence().map { it.shortId }.toHashSet()
                    // an upload that finished while the download ran may be missing from it
                    val justAdded = _records.value.filter { it.createdAt >= startedAt - 5_000L && it.shortId !in ids }
                    (fetched.filter { it.shortId !in removedDuringLoad } + justAdded)
                        .sortedByDescending { it.createdAt }
                }
                lastFetchAt = System.currentTimeMillis()
                _records.value = merged
                merged
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** A new upload: shows up in History at once, without asking Firebase. */
    fun upsert(record: UploadRecord) {
        synchronized(lock) {
            val list = _records.value.filterNot { it.shortId == record.shortId }
            _records.value = (listOf(record) + list).sortedByDescending { it.createdAt }
        }
    }

    /** Deleted uploads disappear at once, without asking Firebase. */
    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val set = ids.toHashSet()
            removedDuringLoad.addAll(set)
            _records.value = _records.value.filterNot { it.shortId in set }
        }
    }
}
