package com.praveen.studio.photovideo.url

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.praveen.studio.photovideo.url.domain.util.CacheCleaner
import kotlin.system.exitProcess

class PhotoVideoApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "PhotoVideoApplication"

        /** Same values as google-services.json (public Firebase client config, not a secret). */
        private const val FB_APP_ID = "1:411751765755:android:25f661b0368263a129425e"
        private const val FB_API_KEY = "AIzaSyD4uBodIsPtE1CyCNA5p6_4Ci8Oyf0c6Ok"
        private const val FB_PROJECT_ID = "photo-video-url"
        private const val FB_SENDER_ID = "411751765755"
        private const val FB_BUCKET = "photo-video-url.firebasestorage.app"
        const val FB_DATABASE_URL = "https://photo-video-url-default-rtdb.firebaseio.com"

        /** Thumbnails of History are full size files on the server, so the disk cache must stay small. */
        private const val IMAGE_DISK_CACHE_BYTES = 32L * 1024L * 1024L
    }

    override fun onCreate() {
        super.onCreate()

        // The crash report screen runs in its own process and must stay free of any app start-up work,
        // otherwise a problem in the start-up code would crash the report screen too.
        if (Application.getProcessName().orEmpty().endsWith(":crash")) return

        // Guard against background thread crashes from network or service libraries.
        // A crash on the main thread opens the crash report screen with the exact error text.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            if (thread.name == "main") {
                if (showCrashScreen(throwable)) {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        initFirebase()

        // Leftover ZIPs / extracted APKs / PDF previews from earlier sessions are not needed any more.
        try {
            Thread {
                try {
                    CacheCleaner.cleanTempFiles(applicationContext)
                } catch (_: Throwable) {
                }
            }.start()
        } catch (_: Throwable) {
        }
    }

    /** Opens [CrashReportActivity] (separate process) with the stack trace. Returns true when it was started. */
    private fun showCrashScreen(throwable: Throwable): Boolean {
        return try {
            val report = buildString {
                append("App: Photo Video URL ").append(BuildConfig.VERSION_NAME).append('\n')
                append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
                append(Log.getStackTraceString(throwable))
            }.take(14000)
            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_REPORT, report)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            // give the system a moment to take over the start request before this process ends
            Thread.sleep(300)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Normally the google-services plugin fills in the Firebase settings from google-services.json.
     * When a build was made without that file (or the plugin skipped it) Firebase would stay unconfigured
     * and every upload would silently miss the database. The same settings are used as a fallback.
     */
    private fun initFirebase() {
        try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase default init notice: ${t.message}")
        }
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(FB_APP_ID)
                    .setApiKey(FB_API_KEY)
                    .setProjectId(FB_PROJECT_ID)
                    .setGcmSenderId(FB_SENDER_ID)
                    .setStorageBucket(FB_BUCKET)
                    .setDatabaseUrl(FB_DATABASE_URL)
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.w(TAG, "FirebaseApp initialised from built-in fallback options")
            } else {
                Log.d(TAG, "FirebaseApp initialized successfully")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase fallback init notice: ${t.message}")
        }
    }

    /** One shared image loader with small caches (the default one may fill 250 MB of disk). */
    override fun newImageLoader(): ImageLoader {
        return try {
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                        .build()
                }
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .crossfade(false)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "Image loader fallback: ${t.message}")
            ImageLoader.Builder(this).build()
        }
    }
}
