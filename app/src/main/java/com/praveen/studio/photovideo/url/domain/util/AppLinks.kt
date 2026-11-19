package com.praveen.studio.photovideo.url.domain.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Pages of the website (https://ivu.gt.tc). The app only opens them in the browser. */
object AppLinks {
    const val DOMAIN = "https://ivu.gt.tc"
    const val ABOUT = "$DOMAIN/about"
    const val CONTACT = "$DOMAIN/contact"
    const val TERMS = "$DOMAIN/terms"
    const val PRIVACY = "$DOMAIN/privacy"

    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
