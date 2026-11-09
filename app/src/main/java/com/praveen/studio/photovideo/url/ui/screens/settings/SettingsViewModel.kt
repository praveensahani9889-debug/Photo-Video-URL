package com.praveen.studio.photovideo.url.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.data.model.UserDetails
import com.praveen.studio.photovideo.url.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)

    val themePreference: StateFlow<ThemePreference> = settingsRepository.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    val installationId: String = settingsRepository.installationId
    val deviceFingerprint: String get() = installationId

    val userDetails: StateFlow<UserDetails> = settingsRepository.observeUserDetails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserDetails())

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _projectUrl = MutableStateFlow("")
    val projectUrl: StateFlow<String> = _projectUrl.asStateFlow()

    private val _anonKey = MutableStateFlow("")
    val anonKey: StateFlow<String> = _anonKey.asStateFlow()

    private val _bucketName = MutableStateFlow("media")
    val bucketName: StateFlow<String> = _bucketName.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    init {
        loadConfig()
        viewModelScope.launch {
            val details = settingsRepository.getUserDetails()
            if (details.name.isNotBlank()) {
                _userName.value = details.name
            }
        }
    }

    private fun loadConfig() {
        val config = settingsRepository.getConfig()
        _projectUrl.value = config.projectUrl
        _anonKey.value = config.anonKey
        _bucketName.value = config.bucketName.ifEmpty { "media" }
        _isSaved.value = config.isConfigured
    }

    private var userNameJob: Job? = null

    fun setUserName(name: String) {
        _userName.value = name
        // Save once the user stops typing instead of writing to Firebase on every key press.
        userNameJob?.cancel()
        userNameJob = viewModelScope.launch {
            delay(700)
            settingsRepository.saveUserName(name)
        }
    }

    fun setProjectUrl(url: String) {
        _projectUrl.value = url
        _testState.value = ConnectionTestState.Idle
    }

    fun setAnonKey(key: String) {
        _anonKey.value = key
        _testState.value = ConnectionTestState.Idle
    }

    fun setBucketName(name: String) {
        _bucketName.value = name
        _testState.value = ConnectionTestState.Idle
    }

    fun saveConfig() {
        val config = SupabaseConfig(
            projectUrl = _projectUrl.value.trim(),
            anonKey = _anonKey.value.trim(),
            bucketName = _bucketName.value.trim().ifEmpty { "media" }
        )
        viewModelScope.launch {
            settingsRepository.saveConfig(config, _userName.value.trim())
            // Show the normalised URL that was really stored
            _projectUrl.value = config.cleanUrl.ifEmpty { config.projectUrl }
            _isSaved.value = config.isConfigured
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testState.value = ConnectionTestState.Testing
            val config = SupabaseConfig(
                projectUrl = _projectUrl.value.trim(),
                anonKey = _anonKey.value.trim(),
                bucketName = _bucketName.value.trim().ifEmpty { "media" }
            )

            val result = settingsRepository.testConnection(config)
            if (result.isSuccess) {
                _testState.value = ConnectionTestState.Success(result.getOrNull() ?: "Connected successfully!")
            } else {
                _testState.value = ConnectionTestState.Error(
                    result.exceptionOrNull()?.message ?: "Connection failed. Please check URL and anon key."
                )
            }
        }
    }

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

    fun clearCredentials() {
        settingsRepository.clearConfig()
        _projectUrl.value = ""
        _anonKey.value = ""
        _bucketName.value = "media"
        _isSaved.value = false
        _testState.value = ConnectionTestState.Idle
        viewModelScope.launch {
            settingsRepository.clearRemoteCredentials()
        }
    }
}
