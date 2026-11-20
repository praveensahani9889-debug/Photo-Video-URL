package com.praveen.studio.photovideo.url.data.model

/**
 * The optional "advanced" settings of one short link. Everything is optional: the default value of every field means
 * "not set". They are saved in Firebase next to the file details (public_links/<id> for the website, and the history
 * entry user/<uid>/<category>/<id> for the app), so the website can read them.
 *
 * Firebase field names (see README, "Advanced link options"):
 *   expiresAt (ms), maxViews, views (counted by the website), passwordHash / passwordSalt / passwordIter,
 *   hasPassword, bgColor, button { text, color, url }, pageTitle, pageMessage, allowDownload.
 */
data class LinkOptions(
    /** Epoch milliseconds when the link stops working and the file is deleted. 0 = never. */
    val expiresAt: Long = 0L,
    /** The link stops working after this many views. 0 = unlimited. */
    val maxViews: Long = 0L,
    /** Views counted so far (the website counts them; the app only reads the number). */
    val views: Long = 0L,
    /** PBKDF2-HMAC-SHA256 of the password (hex), the salt (hex) and the iterations. Empty = no password. */
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val passwordIter: Int = 0,
    /** True when a password is set (the history entry keeps only this flag, never the hash). */
    val hasPassword: Boolean = false,
    /** Page background colour as #RRGGBB. Empty = the website default. */
    val bgColor: String = "",
    /** Button under the file: text, colour (#RRGGBB) and link (https://...). */
    val buttonText: String = "",
    val buttonColor: String = "",
    val buttonUrl: String = "",
    /** Title and message shown on the page. */
    val pageTitle: String = "",
    val pageMessage: String = "",
    /** false = the page shows the file but offers no download button. */
    val allowDownload: Boolean = true
) {
    val isPasswordProtected: Boolean get() = hasPassword || passwordHash.isNotEmpty()

    val hasButton: Boolean get() = buttonText.isNotBlank() && buttonUrl.isNotBlank()

    /** True when nothing is set (every field has its "not set" value; [views] is only a counter). */
    val isDefault: Boolean
        get() = copy(views = 0L) == LinkOptions()

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt > 0L && now >= expiresAt

    fun isViewLimitReached(): Boolean = maxViews > 0L && views >= maxViews

    /**
     * Writes these options into [target] (a copy of a Firebase node): first every option key is removed, then the
     * keys that are set are added, so a switched-off option really disappears. [views] is never touched (the website
     * owns that counter). The history entry gets the password flag but not the hash.
     */
    fun applyTo(target: MutableMap<String, Any?>, publicNode: Boolean) {
        OPTION_KEYS.forEach { target.remove(it) }
        if (expiresAt > 0L) target["expiresAt"] = expiresAt
        if (maxViews > 0L) target["maxViews"] = maxViews
        if (isPasswordProtected) {
            target["hasPassword"] = true
            if (publicNode && passwordHash.isNotEmpty()) {
                target["passwordHash"] = passwordHash
                target["passwordSalt"] = passwordSalt
                target["passwordIter"] = passwordIter.toLong()
            }
        }
        if (bgColor.isNotBlank()) target["bgColor"] = bgColor
        if (hasButton) {
            target["button"] = mapOf(
                "text" to buttonText,
                "color" to buttonColor.ifBlank { DEFAULT_BUTTON_COLOR },
                "url" to buttonUrl
            )
        }
        if (pageTitle.isNotBlank()) target["pageTitle"] = pageTitle
        if (pageMessage.isNotBlank()) target["pageMessage"] = pageMessage
        if (!allowDownload) target["allowDownload"] = false
    }

    /** Short lines for the result page and History ("Expires in 7 days", "Password protected", ...). */
    fun summary(now: Long = System.currentTimeMillis()): List<String> {
        val lines = ArrayList<String>()
        if (expiresAt > 0L) {
            lines += if (isExpired(now)) "Expired" else "Expires " + java.text.DateFormat
                .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(expiresAt))
        }
        if (isPasswordProtected) lines += "Password protected"
        if (maxViews > 0L) lines += if (views > 0L) "Views $views / $maxViews" else "Max $maxViews views"
        if (bgColor.isNotBlank()) lines += "Custom background"
        if (hasButton) lines += "Button: $buttonText"
        if (!allowDownload) lines += "Download off"
        return lines
    }

    companion object {
        const val DEFAULT_BUTTON_COLOR = "#0EA5E9"

        /** Every Firebase key that belongs to the options (without "views"). */
        val OPTION_KEYS = listOf(
            "expiresAt", "maxViews", "passwordHash", "passwordSalt", "passwordIter", "hasPassword",
            "bgColor", "button", "pageTitle", "pageMessage", "allowDownload"
        )
    }
}
