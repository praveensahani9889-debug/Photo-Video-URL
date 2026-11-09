package com.praveen.studio.photovideo.url

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
import com.praveen.studio.photovideo.url.ui.PhotoVideoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native transparent edge-to-edge was introduced in Android 10 (API 29).
        // On Android 9 (API 28), guard against decor view crashes across OEM forks.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                enableEdgeToEdge()
            }
        } catch (t: Throwable) {
            Log.w("MainActivity", "enableEdgeToEdge skipped: ${t.message}")
        }

        // Handle the launch intent only once. After a recreation (theme change, process restore) or when
        // the app is reopened from the recent apps list the same intent is delivered again, which would
        // re-open a viewer / share dialog the user already closed (and could upload the files twice).
        val launchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !launchedFromHistory) {
            try {
                IncomingIntentManager.handleIntent(this, intent)
            } catch (t: Throwable) {
                Log.w("MainActivity", "handleIntent error: ${t.message}")
            }
        }

        setContent {
            PhotoVideoApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            IncomingIntentManager.handleIntent(this, intent)
        } catch (t: Throwable) {
            Log.w("MainActivity", "onNewIntent handleIntent error: ${t.message}")
        }
    }
}
