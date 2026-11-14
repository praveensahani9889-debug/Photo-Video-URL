package com.praveen.studio.photovideo.url.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object AdsActivity {
    /** The Activity behind a Compose / wrapped Context, or null. */
    fun find(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
