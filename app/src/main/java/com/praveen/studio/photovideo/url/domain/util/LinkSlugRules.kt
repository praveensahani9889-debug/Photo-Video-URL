package com.praveen.studio.photovideo.url.domain.util

/**
 * Rules for a custom link address: https://ivu.gt.tc/<name>.
 * Letters (any case, saved in lower case), numbers, "-" and "_"; 3 to 30 characters; no spaces or other symbols.
 */
object LinkSlugRules {
    const val SITE = "ivu.gt.tc"
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val ALLOWED = Regex("^[a-z0-9][a-z0-9_-]*$")
    private val TYPED_PREFIX = Regex("^(image|video|document|file|apk)_", RegexOption.IGNORE_CASE)

    fun normalize(input: String): String = input.trim().lowercase()

    fun fullUrl(slug: String): String = "https://$SITE/$slug"

    /**
     * Returns the reason why [input] cannot be used, or null when it is fine. An empty input is not an error here
     * (it means "keep the automatic link"); the caller handles that.
     */
    fun problem(input: String, extraBlocked: Collection<String> = emptyList()): String? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        if (raw.any { it.isWhitespace() }) return "Spaces are not allowed. Use - or _ instead."
        val name = raw.lowercase()
        if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) {
            return "Only letters, numbers, - and _ are allowed (no @, #, ., / or other symbols)."
        }
        if (name.length < MIN_LENGTH) return "Use at least $MIN_LENGTH characters."
        if (name.length > MAX_LENGTH) return "Use at most $MAX_LENGTH characters."
        if (!ALLOWED.matches(name)) return "The name must start with a letter or a number."
        if (TYPED_PREFIX.containsMatchIn(name)) return "This format is used by the automatic links. Choose another name."
        if (BlockedWords.isBlocked(name, extraBlocked)) return "This name is not allowed. Choose another one."
        return null
    }
}
