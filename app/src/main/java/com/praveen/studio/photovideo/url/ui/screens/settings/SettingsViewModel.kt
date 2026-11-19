package com.praveen.studio.photovideo.url.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.data.remote.BucketUsage
import com.praveen.studio.photovideo.url.data.remote.FirebaseCheckLine
import com.praveen.studio.photovideo.url.data.remote.HistoryRemoteStore
import com.praveen.studio.photovideo.url.data.remote.SetupCheckItem
import com.praveen.studio.photovideo.url.domain.repository.HistoryRepository
import com.praveen.studio.photovideo.url.domain.repository.SettingsRepository
import com.praveen.studio.photovideo.url.domain.util.CacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FirebaseCheckState {
    data object Idle : FirebaseCheckState
    data object Checking : FirebaseCheckState
    data class Done(val lines: List<FirebaseCheckLine>) : FirebaseCheckState
}

/** The setup check shown in the Supabase setup dialog and on the Analyze page. */
sealed interface SetupCheckState {
    data object Idle : SetupCheckState
    data object Running : SetupCheckState
    data class Done(val items: List<SetupCheckItem>) : SetupCheckState
}

/** "Scan storage" of one project. */
sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val filesFound: Int) : ScanState
    data class Done(val usage: BucketUsage) : ScanState
    data class Failed(val message: String) : ScanState
}

/** What the user types in the Supabase setup dialog. [id] is null for a new project. */
data class SupabaseDraft(
    val id: String? = null,
    val label: String = "",
    val url: String = "",
    val key: String = "",
    val bucket: String = "media",
    /** Storage size of the Supabase plan in GB (Free plan = 1). */
    val capacityGb: String = "1"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val historyRepository by lazy { HistoryRepository(application) }

    val themePreference: StateFlow<ThemePreference> = settingsRepository.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    val installationId: String = settingsRepository.installationId
    val deviceFingerprint: String get() = installationId

    /** Counters saved on the phone. Firebase is only asked when they are stale (no live listener). */
    val userDetails: StateFlow<UserDetails> = settingsRepository.userDetails

    // ---------------------------------------------------------------------------------------------
    // Profile: the name is sent to Firebase ONCE, when the user taps Save / Update
    // ---------------------------------------------------------------------------------------------

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _savedName = MutableStateFlow("")
    val savedName: StateFlow<String> = _savedName.asStateFlow()

    val nameChanged: StateFlow<Boolean> = combine(_userName, _savedName) { typed, saved ->
        typed.trim() != saved.trim()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _nameSaving = MutableStateFlow(false)
    val nameSaving: StateFlow<Boolean> = _nameSaving.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Supabase projects
    // ---------------------------------------------------------------------------------------------

    val accounts: StateFlow<List<SupabaseAccount>> = settingsRepository.accounts
    val activeAccountId: StateFlow<String> = settingsRepository.activeAccountId

    /** How many projects the user may add (the developer can change it in Firebase app/backend). */
    val maxAccounts: Int get() = AppControlStore.current.effectiveMaxAccounts

    private val _draft = MutableStateFlow<SupabaseDraft?>(null)
    val draft: StateFlow<SupabaseDraft?> = _draft.asStateFlow()

    private val _draftError = MutableStateFlow<String?>(null)
    val draftError: StateFlow<String?> = _draftError.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _draftCheck = MutableStateFlow<SetupCheckState>(SetupCheckState.Idle)
    val draftCheck: StateFlow<SetupCheckState> = _draftCheck.asStateFlow()

    // Analyze page
    private val _scanStates = MutableStateFlow<Map<String, ScanState>>(emptyMap())
    val scanStates: StateFlow<Map<String, ScanState>> = _scanStates.asStateFlow()

    private val _accountChecks = MutableStateFlow<Map<String, SetupCheckState>>(emptyMap())
    val accountChecks: StateFlow<Map<String, SetupCheckState>> = _accountChecks.asStateFlow()

    /** Uploads known from History (used for the per-type numbers of every project). */
    val historyRecords: StateFlow<List<UploadRecord>> = HistoryRemoteStore.records

    // ---------------------------------------------------------------------------------------------

    private val _firebaseCheck = MutableStateFlow<FirebaseCheckState>(FirebaseCheckState.Idle)
    val firebaseCheck: StateFlow<FirebaseCheckState> = _firebaseCheck.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    init {
        refreshCacheSize()
        settingsRepository.loadSavedUserDetails()
        applyRemoteName(settingsRepository.userDetails.value.name)
        viewModelScope.launch {
            // only reads Firebase when the saved copy is older than a few minutes
            settingsRepository.refreshUserDetails(false)
            applyRemoteName(settingsRepository.userDetails.value.name)
        }
    }

    private fun applyRemoteName(remote: String) {
        if (_userName.value == _savedName.value) _userName.value = remote
        _savedName.value = remote
    }

    fun setUserName(name: String) {
        _userName.value = name
    }

    /** Sends the name to Firebase (one write) and tells the screen when it is done. */
    fun saveUserName(onDone: () -> Unit) {
        if (_nameSaving.value) return
        val name = _userName.value.trim()
        viewModelScope.launch {
            _nameSaving.value = true
            try {
                settingsRepository.saveUserName(name)
                _userName.value = name
                _savedName.value = name
                onDone()
            } finally {
                _nameSaving.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Setup dialog
    // ---------------------------------------------------------------------------------------------

    fun openAddDialog() {
        _draftError.value = null
        _draftCheck.value = SetupCheckState.Idle
        _draft.value = SupabaseDraft()
    }

    fun openEditDialog(id: String) {
        val account = accounts.value.firstOrNull { it.id == id } ?: return
        _draftError.value = null
        _draftCheck.value = SetupCheckState.Idle
        _draft.value = SupabaseDraft(
            id = account.id,
            label = account.label,
            url = account.projectUrl,
            key = account.anonKey,
            bucket = account.bucketName,
            capacityGb = formatGb(account.capacityMb)
        )
    }

    fun closeDialog() {
        _draft.value = null
        _draftError.value = null
        _draftCheck.value = SetupCheckState.Idle
    }

    fun updateDraft(change: (SupabaseDraft) -> SupabaseDraft) {
        _draft.update { current -> current?.let(change) }
        _draftError.value = null
        _draftCheck.value = SetupCheckState.Idle
    }

    private fun draftToAccount(d: SupabaseDraft): SupabaseAccount? {
        val gb = d.capacityGb.trim().replace(',', '.').toDoubleOrNull()
        if (gb == null || gb <= 0.0) {
            _draftError.value = "Enter the storage size of your Supabase plan in GB, for example 1."
            return null
        }
        return SupabaseAccount(
            id = d.id ?: SupabaseAccount.newId(),
            label = d.label.trim(),
            projectUrl = d.url.trim(),
            anonKey = d.key.trim(),
            bucketName = d.bucket.trim().ifEmpty { "media" },
            capacityMb = (gb * 1024.0).toLong().coerceAtLeast(1L)
        )
    }

    /** Runs the full setup check for what is typed in the dialog (connection, public bucket, upload, read, delete). */
    fun runDraftCheck() {
        val d = _draft.value ?: return
        if (_draftCheck.value is SetupCheckState.Running) return
        val config = com.praveen.studio.photovideo.url.data.model.SupabaseConfig(
            projectUrl = d.url.trim(),
            anonKey = d.key.trim(),
            bucketName = d.bucket.trim().ifEmpty { "media" }
        )
        viewModelScope.launch {
            _draftCheck.value = SetupCheckState.Running
            val items = try {
                settingsRepository.runSetupCheck(config)
            } catch (t: Throwable) {
                listOf(SetupCheckItem("Setup check", com.praveen.studio.photovideo.url.data.remote.CheckState.FAIL, t.message ?: "Check failed"))
            }
            _draftCheck.value = SetupCheckState.Done(items)
        }
    }

    /** Saves the project (on the phone + one encrypted backup write to Firebase). */
    fun saveDraft(onSaved: (String) -> Unit) {
        val d = _draft.value ?: return
        if (_saving.value) return
        val account = draftToAccount(d) ?: return
        if (!account.config.isConfigured) {
            _draftError.value = "Enter the Project URL and the anon key."
            return
        }
        if (account.config.isPlaceholder) {
            _draftError.value = "This looks like an example value. Enter your own Project URL and anon key."
            return
        }
        if (accounts.value.any { it.id != account.id && it.sameProject(account) }) {
            _draftError.value = "This Supabase project and bucket are already added."
            return
        }
        val editing = d.id != null
        viewModelScope.launch {
            _saving.value = true
            try {
                val ok = settingsRepository.saveAccount(account, maxAccounts)
                if (!ok) {
                    _draftError.value = "You can add up to $maxAccounts Supabase projects. Delete one to add another."
                } else {
                    closeDialog()
                    onSaved(if (editing) "Supabase updated" else "Supabase connected")
                }
            } finally {
                _saving.value = false
            }
        }
    }

    fun deleteAccount(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.deleteAccount(id)
            _scanStates.update { it - id }
            _accountChecks.update { it - id }
            onDone()
        }
    }

    fun setActiveAccount(id: String) {
        settingsRepository.setActiveAccount(id)
    }

    // ---------------------------------------------------------------------------------------------
    // Analyze page
    // ---------------------------------------------------------------------------------------------

    /** Loads the History once (only if it is stale) so the numbers per file type are known. */
    fun loadAnalyzeData() {
        viewModelScope.launch { historyRepository.refresh(false) }
    }

    fun scanAccount(account: SupabaseAccount) {
        if (_scanStates.value[account.id] is ScanState.Scanning) return
        viewModelScope.launch {
            _scanStates.update { it + (account.id to ScanState.Scanning(0)) }
            val result = try {
                settingsRepository.scanAccount(account) { files ->
                    _scanStates.update { it + (account.id to ScanState.Scanning(files)) }
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
            _scanStates.update {
                it + (account.id to (result.getOrNull()?.let { usage -> ScanState.Done(usage) }
                    ?: ScanState.Failed(result.exceptionOrNull()?.message ?: "Scan failed")))
            }
        }
    }

    fun checkAccount(account: SupabaseAccount) {
        if (_accountChecks.value[account.id] is SetupCheckState.Running) return
        viewModelScope.launch {
            _accountChecks.update { it + (account.id to SetupCheckState.Running) }
            val items = try {
                settingsRepository.runSetupCheck(account.config)
            } catch (t: Throwable) {
                listOf(SetupCheckItem("Setup check", com.praveen.studio.photovideo.url.data.remote.CheckState.FAIL, t.message ?: "Check failed"))
            }
            _accountChecks.update { it + (account.id to SetupCheckState.Done(items)) }
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    fun clearHistoryCache() {
        viewModelScope.launch {
            settingsRepository.clearHistoryCache()
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) {
                CacheCleaner.sizeBytes(getApplication<Application>())
            }
        }
    }

    /** Clears the local history copy and every cached file. [onFinished] gets the freed bytes. */
    fun clearAllCache(onFinished: (Long) -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearHistoryCache()
            val freed = CacheCleaner.clearAll(getApplication<Application>())
            _cacheBytes.value = withContext(Dispatchers.IO) {
                CacheCleaner.sizeBytes(getApplication<Application>())
            }
            onFinished(freed)
        }
    }

    fun checkFirebase() {
        if (_firebaseCheck.value is FirebaseCheckState.Checking) return
        viewModelScope.launch {
            _firebaseCheck.value = FirebaseCheckState.Checking
            val lines = try {
                settingsRepository.checkFirebase()
            } catch (t: Throwable) {
                listOf(FirebaseCheckLine(false, "Check failed: ${t.message ?: "unknown error"}"))
            }
            _firebaseCheck.value = FirebaseCheckState.Done(lines)
        }
    }

    private fun formatGb(mb: Long): String {
        val gb = mb / 1024.0
        return if (gb == gb.toLong().toDouble()) gb.toLong().toString() else String.format(java.util.Locale.US, "%.2f", gb)
    }
}
