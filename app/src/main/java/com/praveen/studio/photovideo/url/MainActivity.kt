package com.praveen.studio.photovideo.url

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.praveen.studio.photovideo.url.data.remote.IncomingIntentManager
import com.praveen.studio.photovideo.url.ui.PhotoVideoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        IncomingIntentManager.handleIntent(this, intent)

        setContent {
            PhotoVideoApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IncomingIntentManager.handleIntent(this, intent)
    }
}
