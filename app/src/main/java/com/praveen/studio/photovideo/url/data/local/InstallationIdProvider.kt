package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import android.provider.Settings
import java.util.UUID

class InstallationIdProvider(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_installation_prefs", Context.MODE_PRIVATE)

    /**
     * Gets a persistent unique Installation ID for the user's app installation.
     */
    fun getInstallationId(): String {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id.isNullOrBlank()) {
            id = "inst_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }

    fun getDeviceFingerprint(): String = getInstallationId()



    companion object {
        private const val KEY_INSTALLATION_ID = "installation_id"
    }
}
