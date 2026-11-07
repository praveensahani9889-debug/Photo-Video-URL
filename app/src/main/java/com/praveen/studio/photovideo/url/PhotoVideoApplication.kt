package com.praveen.studio.photovideo.url

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class PhotoVideoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Guard against background thread crashes from network or service libraries
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PhotoVideoApplication", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            if (thread.name == "main") {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            FirebaseApp.initializeApp(this)
            Log.d("PhotoVideoApplication", "FirebaseApp initialized successfully")
        } catch (t: Throwable) {
            Log.w("PhotoVideoApplication", "Firebase App init notice: ${t.message}")
        }
    }
}
