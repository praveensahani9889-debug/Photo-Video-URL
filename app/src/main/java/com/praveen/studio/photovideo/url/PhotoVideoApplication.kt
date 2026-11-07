package com.praveen.studio.photovideo.url

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class PhotoVideoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d("PhotoVideoApplication", "Firebase and AppCheck initialized successfully")
        } catch (e: Exception) {
            Log.w("PhotoVideoApplication", "Firebase AppCheck init notice: ${e.message}")
        }
    }
}
