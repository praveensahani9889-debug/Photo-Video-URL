package com.praveen.studio.photovideo.url

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.ui.components.NavTab
import com.praveen.studio.photovideo.url.ui.screens.search.LinkSearchScreen
import com.praveen.studio.photovideo.url.ui.theme.PhotoVideoUrlTheme

/** "Search & Download" screen, opened from the bottom bar of the main screen. */
class LinkSearchActivity : ComponentActivity() {
    companion object {
        /** Set by the Quick Settings tile: the QR scanner opens at once. */
        const val EXTRA_OPEN_SCANNER = "open_scanner"
    }

    /** A tab of the bottom bar: the main screen opens on that tab and this screen closes. */
    private fun openMainTab(tab: NavTab) {
        if (tab == NavTab.SEARCH) return
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_TAB, tab.name)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (t: Throwable) {
            Log.w("LinkSearchActivity", "openMainTab failed: ${t.message}")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                enableEdgeToEdge()
            }
        } catch (t: Throwable) {
            Log.w("LinkSearchActivity", "enableEdgeToEdge skipped: ${t.message}")
        }

        setContent {
            val preferencesManager = remember { PreferencesManager(applicationContext) }
            val themePreference by preferencesManager.themeFlow.collectAsState(initial = ThemePreference.SYSTEM)
            PhotoVideoUrlTheme(themePreference = themePreference) {
                LinkSearchScreen(
                    onClose = { finish() },
                    onNavigate = { tab -> openMainTab(tab) },
                    openScannerOnStart = intent?.getBooleanExtra(EXTRA_OPEN_SCANNER, false) == true
                )
            }
        }
    }
}
