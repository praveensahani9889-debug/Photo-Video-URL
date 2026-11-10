package com.praveen.studio.photovideo.url

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Shown (in its own process) when the app crashes. It only uses plain Android views, so it still works
 * when the problem is inside the normal app. The report tells exactly what went wrong.
 */
class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_REPORT = "crash_report"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent?.getStringExtra(EXTRA_REPORT).orEmpty().ifBlank { "No details were recorded." }
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Photo Video URL stopped unexpectedly"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(pad, pad, pad, pad / 2)
        }
        val hint = TextView(this).apply {
            text = "Tap \"Copy report\" and send the text to the developer. It shows exactly what went wrong."
            setPadding(pad, 0, pad, pad / 2)
        }
        val body = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(pad, pad / 2, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(body) }

        val copy = Button(this).apply {
            text = "Copy report"
            setOnClickListener {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash report", report))
                    Toast.makeText(this@CrashReportActivity, "Report copied", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }
        }
        val share = Button(this).apply {
            text = "Share"
            setOnClickListener {
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                        putExtra(Intent.EXTRA_SUBJECT, "Photo Video URL crash report")
                    }
                    startActivity(Intent.createChooser(send, "Share crash report"))
                } catch (_: Throwable) {
                }
            }
        }
        val reopen = Button(this).apply {
            text = "Open app again"
            setOnClickListener {
                try {
                    packageManager.getLaunchIntentForPackage(packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(it)
                    }
                } catch (_: Throwable) {
                }
                finish()
            }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(pad / 2, 0, pad / 2, pad / 2)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(copy, lp)
            addView(share, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reopen, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(hint)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        setContentView(root)
    }
}
