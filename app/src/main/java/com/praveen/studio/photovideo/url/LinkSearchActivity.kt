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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.model.ThemePreference
import com.praveen.studio.photovideo.url.ui.screens.search.LinkSearchScreen
import com.praveen.studio.photovideo.url.ui.theme.PhotoVideoUrlTheme

/** "Search & Download" screen, opened from the bottom bar of the main screen. */
class LinkSearchActivity : ComponentActivity() {
    companion object {
        /** Set by the Quick Settings tile: the QR scanner opens at once. */
        const val EXTRA_OPEN_SCANNER = "open_scanner"
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
            var openScanner by rememberSaveable {
                mutableStateOf(intent?.getBooleanExtra(EXTRA_OPEN_SCANNER, false) == true)
            }
            PhotoVideoUrlTheme(themePreference = themePreference) {
                LinkSearchScreen(
                    onClose = { finish() },
                    scannerRequest = openScanner,
                    onScannerRequestHandled = { openScanner = false }
                )
            }
        }
    }
}
