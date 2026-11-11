package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import java.util.UUID

class InstallationIdProvider(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_installation_prefs", Context.MODE_PRIVATE)

    /**
     * Gets the unique Installation ID of this phone's profile.
     *
     * After [adopt] it is the first ID that Firebase has registered for this phone (see IdentityManager), so it is the
     * same after an uninstall, a re-install or "clear data". Before that it is a locally generated ID.
     */
    fun getInstallationId(): String {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id.isNullOrBlank()) {
            id = "inst_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    /** True once the ID was confirmed with (or registered in) Firebase, so it is the permanent ID of this phone. */
    fun isVerified(): Boolean = prefs.getBoolean(KEY_VERIFIED, false)

    /** Stores the ID that belongs to this phone. */
    fun adopt(installationId: String, verified: Boolean) {
        prefs.edit()
            .putString(KEY_INSTALLATION_ID, installationId)
            .putBoolean(KEY_VERIFIED, verified)
            .apply()
    }

    fun getDeviceFingerprint(): String = getInstallationId()

    companion object {
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_VERIFIED = "installation_id_verified"
    }
}
