package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import com.praveen.studio.photovideo.url.AppNotifications
import com.praveen.studio.photovideo.url.data.model.AccountEvent
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's Supabase projects (up to 5) and which one is used for uploads.
 *
 *  - Uploads go to the active project. When it is full (Supabase refused the upload, or the estimate says it is
 *    full) the next project that has room becomes active, a notification is shown, and the upload goes on there.
 *  - The estimate of used storage is kept up to date locally (upload +, delete -, History sync, "Scan storage"),
 *    so nothing has to be asked from Supabase or Firebase for it.
 */
object SupabaseAccountManager {
    private val lock = Any()

    private val _accounts = MutableStateFlow<List<SupabaseAccount>>(emptyList())
    val accounts: StateFlow<List<SupabaseAccount>> = _accounts.asStateFlow()

    private val _activeId = MutableStateFlow("")
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    @Volatile
    private var loaded = false

    /** Accounts that already got the "almost full" warning in this app session. */
    private val warned = HashSet<String>()

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val storage = SecureStorageManager(context)
            val list = storage.getAccounts()
            _accounts.value = list
            _activeId.value = resolveActiveId(list, storage.getActiveId())
            loaded = true
        }
    }

    private fun resolveActiveId(list: List<SupabaseAccount>, wanted: String): String {
        if (list.any { it.id == wanted }) return wanted
        return list.firstOrNull { !it.full }?.id ?: list.firstOrNull()?.id.orEmpty()
    }

    private fun persist(context: Context) {
        SecureStorageManager(context).saveAccounts(_accounts.value, _activeId.value)
    }

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    fun load(context: Context) = ensureLoaded(context.applicationContext)

    fun list(context: Context): List<SupabaseAccount> {
        ensureLoaded(context.applicationContext)
        return _accounts.value
    }

    fun get(context: Context, id: String): SupabaseAccount? = list(context).firstOrNull { it.id == id }

    fun hasUsableAccount(context: Context): Boolean = list(context).any { it.isUsable }

    /** The project that uploads use now (null until the user has set up Supabase). */
    fun active(context: Context): SupabaseAccount? {
        val list = list(context)
        val id = _activeId.value
        return list.firstOrNull { it.id == id && it.isUsable } ?: list.firstOrNull { it.isUsable }
    }

    fun activeConfig(context: Context): SupabaseConfig? = active(context)?.config

    // ---------------------------------------------------------------------------------------------
    // Editing (Settings)
    // ---------------------------------------------------------------------------------------------

    /**
     * Adds a project, or updates the one with the same id. Usage numbers of an existing project are kept (they are
     * reset when the project or bucket itself changed). Returns false when the limit of projects is reached.
     */
    fun upsert(context: Context, account: SupabaseAccount, maxAccounts: Int): Boolean {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            val list = _accounts.value.toMutableList()
            val index = list.indexOfFirst { it.id == account.id }
            if (index >= 0) {
                val old = list[index]
                list[index] = if (!old.sameProject(account)) {
                    account.copy(usedBytes = 0L, externalBytes = 0L, full = false, lastScanAt = 0L, createdAt = old.createdAt)
                } else {
                    account.copy(
                        usedBytes = old.usedBytes,
                        externalBytes = old.externalBytes,
                        full = if (account.capacityMb != old.capacityMb) false else old.full,
                        lastScanAt = old.lastScanAt,
                        createdAt = old.createdAt
                    )
                }
            } else {
                if (list.size >= maxAccounts.coerceAtLeast(1)) return false
                list.add(account.copy(createdAt = if (account.createdAt == 0L) System.currentTimeMillis() else account.createdAt))
            }
            _accounts.value = list
            if (_activeId.value.isBlank() || list.none { it.id == _activeId.value }) {
                _activeId.value = account.id
            }
            persist(app)
            return true
        }
    }

    fun remove(context: Context, id: String) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            val list = _accounts.value.filterNot { it.id == id }
            _accounts.value = list
            warned.remove(id)
            if (_activeId.value == id || list.none { it.id == _activeId.value }) {
                _activeId.value = resolveActiveId(list, "")
            }
            persist(app)
        }
    }

    fun setActive(context: Context, id: String) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            if (_accounts.value.none { it.id == id }) return
            _activeId.value = id
            persist(app)
        }
    }

    /** Replaces everything with the accounts of the encrypted Firebase backup (after a re-install). */
    fun restore(context: Context, accounts: List<SupabaseAccount>, activeId: String) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            if (_accounts.value.isNotEmpty() || accounts.isEmpty()) return
            _accounts.value = accounts
            _activeId.value = resolveActiveId(accounts, activeId)
            persist(app)
        }
    }

    fun clearAll(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            _accounts.value = emptyList()
            _activeId.value = ""
            warned.clear()
            SecureStorageManager(app).clearConfig()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Uploads: choosing the project, switching when it is full
    // ---------------------------------------------------------------------------------------------

    /**
     * The project for the next upload of [sizeBytes]. The active one is used while it has room; otherwise the next
     * project with room becomes active (the user is told). Null when every project is full or nothing is set up.
     */
    fun pickForUpload(context: Context, sizeBytes: Long): SupabaseAccount? {
        val app = context.applicationContext
        ensureLoaded(app)
        val result: Pair<SupabaseAccount?, AccountEvent?> = synchronized(lock) {
            val candidates = _accounts.value.filter { it.isUsable && !it.full }
            if (candidates.isEmpty()) return@synchronized Pair<SupabaseAccount?, AccountEvent?>(null, null)

            val current = candidates.firstOrNull { it.id == _activeId.value }
            val chosen: SupabaseAccount = if (current != null && !current.wouldOverflow(sizeBytes)) {
                current
            } else {
                // nothing has room by the estimate: let Supabase decide (the estimate can be wrong for a bigger plan)
                candidates.firstOrNull { !it.wouldOverflow(sizeBytes) } ?: current ?: candidates.first()
            }

            if (chosen.id == _activeId.value) {
                Pair<SupabaseAccount?, AccountEvent?>(chosen, null)
            } else {
                val previous = _accounts.value.firstOrNull { it.id == _activeId.value }
                _activeId.value = chosen.id
                persist(app)
                val event = if (previous != null) {
                    AccountEvent.Switched(previous.displayName, chosen.displayName, reasonFull = true)
                } else {
                    null
                }
                Pair<SupabaseAccount?, AccountEvent?>(chosen, event)
            }
        }
        result.second?.let { announce(app, it) }
        return result.first
    }

    /**
     * Supabase refused an upload because the storage of [id] is full. The project is marked as full and the next
     * one that has room is returned (null when there is none, the user is told).
     */
    fun markFullAndPickNext(context: Context, id: String, sizeBytes: Long): SupabaseAccount? {
        val app = context.applicationContext
        ensureLoaded(app)
        var name = "Supabase"
        synchronized(lock) {
            _accounts.value = _accounts.value.map {
                if (it.id == id) {
                    name = it.displayName
                    it.copy(full = true, usedBytes = maxOf(it.usedBytes, it.capacityBytes))
                } else {
                    it
                }
            }
            persist(app)
        }
        val next = pickForUpload(app, sizeBytes)
        if (next == null) announce(app, AccountEvent.AllFull(name))
        return next
    }

    /** An upload to [id] finished: the estimate grows, and a nearly full project hands over to the next one. */
    fun recordUpload(context: Context, id: String, bytes: Long) {
        val app = context.applicationContext
        ensureLoaded(app)
        var event: AccountEvent? = null
        synchronized(lock) {
            val list = _accounts.value.map {
                if (it.id == id) it.copy(usedBytes = it.usedBytes + bytes.coerceAtLeast(0L)) else it
            }
            _accounts.value = list
            val updated = list.firstOrNull { it.id == id }
            if (updated != null && updated.isFullOrNearly && _activeId.value == id) {
                val next = list.firstOrNull { it.id != id && it.isUsable && !it.full && !it.isFullOrNearly }
                if (next != null) {
                    _activeId.value = next.id
                    event = AccountEvent.Switched(updated.displayName, next.displayName, reasonFull = true)
                } else if (warned.add(id)) {
                    event = AccountEvent.NearlyFull(updated.displayName)
                }
            }
            persist(app)
        }
        event?.let { announce(app, it) }
    }

    /** A file of [id] was deleted: the estimate shrinks, and a project that had room again is no longer "full". */
    fun recordDelete(context: Context, id: String, bytes: Long) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            _accounts.value = _accounts.value.map {
                if (it.id != id) {
                    it
                } else {
                    val used = (it.usedBytes - bytes.coerceAtLeast(0L)).coerceAtLeast(0L)
                    it.copy(usedBytes = used, full = if (used < it.capacityBytes / 100L * 95L) false else it.full)
                }
            }
            warned.remove(id)
            persist(app)
        }
    }

    /**
     * History was loaded completely from Firebase: the files the app uploaded are known, so the estimate of every
     * project is set to "uploaded files + files found outside the app by the last scan".
     */
    fun reconcile(context: Context, records: List<UploadRecord>) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            val current = _accounts.value
            if (current.isEmpty()) return
            val sums = recordSums(current, records)
            val updated = current.map { account ->
                val used = (sums[account.id] ?: 0L) + account.externalBytes
                if (used == account.usedBytes) account else account.copy(usedBytes = used)
            }
            if (updated != current) {
                _accounts.value = updated
                persist(app)
            }
        }
    }

    /** Bytes of the files that the app uploaded, per project id (free trial photos are not counted). */
    fun recordSums(accounts: List<SupabaseAccount>, records: List<UploadRecord>): Map<String, Long> {
        val sums = HashMap<String, Long>()
        for (record in records) {
            if (record.isFreeTier || record.isDemo) continue
            val account = accountOfRecord(accounts, record) ?: continue
            sums[account.id] = (sums[account.id] ?: 0L) + record.fileSize.coerceAtLeast(0L)
        }
        return sums
    }

    /** The project that holds [record]: same host, and the same bucket when the record knows its bucket. */
    fun accountOfRecord(accounts: List<SupabaseAccount>, record: UploadRecord): SupabaseAccount? {
        val host = SupabaseAccount.hostOf(record.effectiveSupabaseUrl)
        if (host.isBlank()) return null
        val sameHost = accounts.filter { it.host.equals(host, ignoreCase = true) }
        if (sameHost.isEmpty()) return null
        val bucket = record.storageBucket.trim()
        return sameHost.firstOrNull { bucket.isNotEmpty() && it.bucketName.trim().equals(bucket, ignoreCase = true) }
            ?: sameHost.first()
    }

    /** "Scan storage" found the real size of the bucket. */
    fun applyScan(context: Context, id: String, scannedBytes: Long, trackedBytes: Long) {
        val app = context.applicationContext
        ensureLoaded(app)
        synchronized(lock) {
            _accounts.value = _accounts.value.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        usedBytes = scannedBytes,
                        externalBytes = (scannedBytes - trackedBytes).coerceAtLeast(0L),
                        lastScanAt = System.currentTimeMillis(),
                        full = if (scannedBytes < it.capacityBytes / 100L * 95L) false else it.full
                    )
                }
            }
            warned.remove(id)
            persist(app)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun announce(context: Context, event: AccountEvent) {
        _events.tryEmit(event)
        val title = when (event) {
            is AccountEvent.Switched -> "Supabase storage switched"
            is AccountEvent.AllFull -> "Supabase storage full"
            is AccountEvent.NearlyFull -> "Supabase storage almost full"
        }
        AppNotifications.show(context, title, event.message, null)
    }
}
