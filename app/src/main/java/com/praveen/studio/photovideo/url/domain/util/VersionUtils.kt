package com.praveen.studio.photovideo.url.domain.util

object VersionUtils {
    /** true when [latest] is a higher version than [current] ("2.5.0" > "2.4.2"). Blank or unreadable -> false. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty()) return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { piece -> piece.takeWhile { it.isDigit() }.toIntOrNull() }
}
