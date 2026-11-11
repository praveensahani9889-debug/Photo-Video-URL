package com.praveen.studio.photovideo.url.data.remote

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.praveen.studio.photovideo.url.data.local.ConfigBackupCodec
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.SecureStorageManager
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * One profile per phone.
 *
 * - The first installation ID of a phone is registered in Firebase (device_profiles/<hash of the Android ID>) and
 *   used from then on. After an uninstall, a re-install or "clear data" the same ID is found again, so the History,
 *   the free demo counter and the Supabase settings come back and no new profile is created.
 *   (The Android ID stays the same for the same app signing key, until the phone is factory reset.)
 * - The public IP is NOT used to pick a profile (people on the same Wi-Fi / mobile network would see each other's
 *   History). It only limits free demo photos per network (ip_profiles/<hash of the IP>/freeUsed). Only salted
 *   hashes are stored, never the IP or the Android ID itself.
 * - The user's Supabase settings are backed up in Firebase, encrypted with a key that only this phone has.
 */
object IdentityManager {
    private const val TAG = "IdentityManager"

    /** Free demo photos allowed per network (3 per phone; a network can have several phones). */
    const val IP_FREE_MAX = 30L

    private const val RESOLVE_TIMEOUT_MS = 6_000L
    private const val SYNC_TIMEOUT_MS = 4_000L
    private const val INVALID_ANDROID_ID = "9774d56d682e549c"
    private val mutex = Mutex()

    @Volatile
    private var cachedIpKey: String? = null

    @Volatile
    private var ipLookupDone = false

    @Volatile
    private var restoreTried = false

    @Volatile
    private var backupChecked = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Called once while the start screen is shown. Never throws. Afterwards [InstallationIdProvider] holds the
     * phone's permanent ID (or the local one when offline; it is checked again at the next start).
     */
    suspend fun prepare(context: Context) {
        val app = context.applicationContext
        mutex.withLock {
            val provider = InstallationIdProvider(app)
            val androidId = rawAndroidId(app)
            try {
                // never keep the start screen waiting for long when the phone is offline
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveInstallationId(provider, androidId) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Identity notice: ${t.message}")
            }
            try {
                withTimeoutOrNull(SYNC_TIMEOUT_MS) { syncSupabaseConfig(app, provider, androidId) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Settings backup notice: ${t.message}")
            }
        }
    }

    private suspend fun resolveInstallationId(provider: InstallationIdProvider, androidId: String?) {
        if (provider.isVerified()) return
        // Without an Android ID there is nothing that identifies the phone: the local ID stays.
        if (androidId == null) return

        val deviceKey = sha256Hex("ivu-device-v1|$androidId")
        val manager = FirebaseDatabaseManager()

        val existing = manager.fetchDeviceProfile(deviceKey)
        if (existing.isFailure) {
            Log.w(TAG, "Phone lookup failed, will retry at next start: ${existing.exceptionOrNull()?.message}")
            return
        }
        val registeredId = existing.getOrNull()
        if (registeredId != null) {
            provider.adopt(registeredId, verified = true)
            Log.d(TAG, "Existing profile of this phone restored")
            return
        }

        // First time this phone is seen: its current ID becomes the permanent one
        val localId = provider.getInstallationId()
        val registered = manager.registerDeviceProfile(deviceKey, localId)
        registered.getOrNull()?.let { provider.adopt(it, verified = true) }
            ?: Log.w(TAG, "Phone registration failed, will retry at next start: ${registered.exceptionOrNull()?.message}")
    }

    // ---------------------------------------------------------------------------------------------
    // Supabase settings: restore after a re-install, backup after saving
    // ---------------------------------------------------------------------------------------------

    private suspend fun syncSupabaseConfig(context: Context, provider: InstallationIdProvider, androidId: String?) {
        if (androidId == null || !provider.isVerified()) return
        val secure = SecureStorageManager(context)
        val local = secure.getConfig()
        val installationId = provider.getInstallationId()
        val manager = FirebaseDatabaseManager()

        if (local.isConfigured && !local.isPlaceholder) {
            // Older installs: make sure a backup exists (does not delay the start screen)
            if (!backupChecked) {
                backupChecked = true
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val remote = manager.fetchConfigBackup(installationId)
                        if (remote.isSuccess && remote.getOrNull() == null) {
                            manager.saveConfigBackup(installationId, ConfigBackupCodec.encrypt(androidId, local))
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        } else if (!restoreTried) {
            restoreTried = true
            val blob = manager.fetchConfigBackup(installationId).getOrNull() ?: return
            val restored = ConfigBackupCodec.decrypt(androidId, blob)
            if (restored != null && restored.isConfigured && !restored.isPlaceholder) {
                secure.saveConfig(restored)
                Log.d(TAG, "Supabase settings restored")
            }
        }
    }

    /** Backs up the settings the user just saved (encrypted). Best effort. */
    suspend fun backupConfig(context: Context, config: SupabaseConfig) {
        try {
            val app = context.applicationContext
            val provider = InstallationIdProvider(app)
            if (!provider.isVerified()) return
            val androidId = rawAndroidId(app) ?: return
            FirebaseDatabaseManager().saveConfigBackup(
                provider.getInstallationId(),
                ConfigBackupCodec.encrypt(androidId, config)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Backup notice: ${t.message}")
        }
    }

    /** Removes the backup when the user disconnects Supabase, so it is not restored again. */
    suspend fun deleteConfigBackup(context: Context) {
        try {
            val provider = InstallationIdProvider(context.applicationContext)
            FirebaseDatabaseManager().deleteConfigBackup(provider.getInstallationId())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Backup delete notice: ${t.message}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Network key (hashed public IP) for the free demo limit
    // ---------------------------------------------------------------------------------------------

    /** Hash of the current public IP, or null when it cannot be found (then the network limit is skipped). */
    suspend fun getIpKey(): String? {
        cachedIpKey?.let { return it }
        if (ipLookupDone) return null
        val ip = fetchPublicIp()
        ipLookupDone = ip == null
        val key = ip?.let { sha256Hex("ivu-ip-v1|$it") }
        cachedIpKey = key
        return key
    }

    private suspend fun fetchPublicIp(): String? = withContext(Dispatchers.IO) {
        val services = listOf(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://api64.ipify.org"
        )
        for (url in services) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val text = response.body?.string()?.trim().orEmpty()
                        if (text.length in 7..45 && text.all { it.isLetterOrDigit() || it == '.' || it == ':' }) {
                            return@withContext text
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    // ---------------------------------------------------------------------------------------------

    private fun rawAndroidId(context: Context): String? {
        val id = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Throwable) {
            null
        }
        return id?.takeIf { it.isNotBlank() && it != INVALID_ANDROID_ID && !it.all { c -> c == '0' } }
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
