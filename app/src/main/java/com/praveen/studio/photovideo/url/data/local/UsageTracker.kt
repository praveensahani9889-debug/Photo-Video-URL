package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.PlanLimits
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Counts what was uploaded today (per file type) and when the last website was published, so the limits of the
 * account plan can be applied. The numbers are kept on the phone; a new day starts at midnight (phone time).
 */
object UsageTracker {
    private const val PREFS = "usage_prefs"
    private const val KEY_DAY = "day"
    private const val KEY_LAST_WEB = "last_website_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun countKey(category: MediaCategory) = "count_${category.name}"

    /** Clears the counters when the day changed. */
    private fun rollOver(context: Context) {
        val p = prefs(context)
        val day = today()
        if (p.getString(KEY_DAY, null) != day) {
            val editor = p.edit().putString(KEY_DAY, day)
            MediaCategory.entries.forEach { editor.remove(countKey(it)) }
            editor.apply()
        }
    }

    @Synchronized
    fun usedToday(context: Context, category: MediaCategory): Int {
        rollOver(context)
        return prefs(context).getInt(countKey(category), 0)
    }

    /** How many more of this type may be uploaded today, or [Int.MAX_VALUE] when there is no limit. */
    fun remainingToday(context: Context, category: MediaCategory, plan: PlanLimits): Int {
        val limit = plan.dailyLimit(category)
        if (limit < 0) return Int.MAX_VALUE
        return (limit - usedToday(context, category)).coerceAtLeast(0)
    }

    @Synchronized
    fun recordUpload(context: Context, category: MediaCategory) {
        if (category == MediaCategory.WEB) return
        rollOver(context)
        val p = prefs(context)
        p.edit().putInt(countKey(category), p.getInt(countKey(category), 0) + 1).apply()
    }

    /** Milliseconds the user still has to wait before the next website, 0 when a website may be published now. */
    fun websiteWaitMillis(context: Context, plan: PlanLimits): Long {
        if (plan.websiteEveryHours <= 0) return 0L
        val last = prefs(context).getLong(KEY_LAST_WEB, 0L)
        if (last <= 0L) return 0L
        val wait = last + plan.websiteEveryHours * 3_600_000L - System.currentTimeMillis()
        return wait.coerceAtLeast(0L)
    }

    fun recordWebsite(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_WEB, System.currentTimeMillis()).apply()
    }

    /** "5 h 20 min" */
    fun formatWait(millis: Long): String {
        val totalMinutes = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    /** Short name of a category for messages, e.g. "photos". */
    fun plural(category: MediaCategory): String = when (category) {
        MediaCategory.IMAGES -> "photos"
        MediaCategory.VIDEOS -> "videos"
        MediaCategory.DOCUMENTS -> "documents"
        MediaCategory.FILES -> "files"
        MediaCategory.APKS -> "APKs"
        MediaCategory.TEXT -> "text posts"
        MediaCategory.WEB -> "websites"
    }
}
