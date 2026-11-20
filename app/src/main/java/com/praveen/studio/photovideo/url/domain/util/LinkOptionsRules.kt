package com.praveen.studio.photovideo.url.domain.util

import java.net.URI

/** Checks of the values typed on the Advanced options page. Every function returns null when the value is fine. */
object LinkOptionsRules {
    const val MIN_PASSWORD = 4
    const val MAX_PASSWORD = 64
    const val MAX_VIEWS = 1_000_000L
    const val MAX_BUTTON_TEXT = 30
    const val MAX_TITLE = 60
    const val MAX_MESSAGE = 200
    const val MAX_URL = 500
    const val MIN_EXPIRY_MINUTES = 5L
    const val MAX_EXPIRY_DAYS = 365L

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters."
        password.length > MAX_PASSWORD -> "Use at most $MAX_PASSWORD characters."
        else -> null
    }

    fun viewsProblem(text: String): String? {
        val n = text.trim().toLongOrNull() ?: return "Enter a number, for example 100."
        return if (n < 1L || n > MAX_VIEWS) "Use a number from 1 to $MAX_VIEWS." else null
    }

    fun isHexColor(value: String): Boolean = HEX_COLOR.matches(value.trim())

    fun colorProblem(value: String): String? =
        if (isHexColor(value)) null else "Use a colour like #1E293B (6 letters / numbers after #)."

    /** "example.com/x" becomes "https://example.com/x"; only http(s) links are accepted. */
    fun normalizeUrl(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        return if (t.contains("://")) t else "https://$t"
    }

    fun urlProblem(text: String): String? {
        val url = normalizeUrl(text)
        if (url.isEmpty()) return "Enter the link of the button."
        if (url.length > MAX_URL) return "The link is too long."
        if (url.any { it.isWhitespace() || it == '"' || it == '\'' || it == '<' || it == '>' || it == '\\' || it == '`' }) {
            return "The link contains characters that are not allowed."
        }
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme != "http" && scheme != "https") || host.isNullOrBlank() || !host.contains('.')) {
                "Enter a full web link, for example https://example.com"
            } else {
                null
            }
        } catch (_: Exception) {
            "This does not look like a web link."
        }
    }

    fun buttonTextProblem(text: String): String? = when {
        text.isBlank() -> "Write the text of the button."
        text.trim().length > MAX_BUTTON_TEXT -> "Use at most $MAX_BUTTON_TEXT characters."
        else -> null
    }

    /** Minutes for "<amount> <unit>" or null when the input is wrong. [unit] is 0 minutes, 1 hours, 2 days. */
    fun customExpiryMinutes(amount: String, unit: Int): Long? {
        val n = amount.trim().toLongOrNull() ?: return null
        if (n < 1L) return null
        return when (unit) {
            0 -> n
            1 -> n * 60L
            else -> n * 60L * 24L
        }
    }

    fun expiryProblem(minutes: Long?): String? = when {
        minutes == null -> "Enter a number, for example 3."
        minutes < MIN_EXPIRY_MINUTES -> "The shortest time is $MIN_EXPIRY_MINUTES minutes."
        minutes > MAX_EXPIRY_DAYS * 24L * 60L -> "The longest time is $MAX_EXPIRY_DAYS days."
        else -> null
    }
}
