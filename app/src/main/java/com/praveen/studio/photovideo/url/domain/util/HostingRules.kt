package com.praveen.studio.photovideo.url.domain.util

/**
 * Custom hosting address of a published website:  https://ivu.gt.tc/<name><ending>   e.g. ivu.gt.tc/mysite.com
 *
 *  - "ivu.gt.tc/" is fixed.
 *  - <name>: the user's own word (letters, numbers, "-"; 3 to 30 characters). It must be free.
 *  - <ending>: one of [SUFFIXES] (fixed list, the user can only pick from it).
 *
 * Firebase keys cannot contain ".", so the link is saved under [key] ("mysite.ivu.com" -> "mysite,ivu,com").
 * The website (PHP, config/site.php `hosting_suffixes` and .htaccess) uses the same list and the same key.
 */
object HostingRules {
    const val SITE = "ivu.gt.tc"
    const val MIN_NAME = 3
    const val MAX_NAME = 30

    /** The endings a user can choose. KEEP IN SYNC with `hosting_suffixes` in the website's config/site.php. */
    val SUFFIXES = listOf(
        ".com", ".in", ".org", ".net", ".ai", ".co", ".my", ".free", ".ivu", ".ivu.com",
        ".p", ".s", ".html", ".abcd", ".app", ".site", ".online", ".tech", ".store",
        ".blog", ".live", ".xyz", ".dev", ".me", ".page"
    )

    /** Words that cannot be a hosting name on top of [BlockedWords] (they look like official pages of the service). */
    private val EXTRA_RESERVED = setOf(
        "info", "information", "appinfo", "abuse", "report", "press", "news", "host", "hosting", "domain", "domains",
        "dns", "whois", "webmail", "noreply", "ssl", "cdn", "smtp", "vpn", "proxy", "copyright", "dmca", "complaint"
    )

    /** Own endings (Premium 399 and team accounts): 2 to 10 letters / numbers. */
    const val MIN_CUSTOM_ENDING = 2
    const val MAX_CUSTOM_ENDING = 10

    /**
     * Endings that can never be an own ending: they are file types of the website itself (php, css, png ...) and
     * would clash with real files. KEEP IN SYNC with `hosting_reserved_endings` in the website's config/site.php.
     */
    val RESERVED_ENDINGS = setOf(
        "php", "phtml", "js", "mjs", "css", "json", "xml", "txt", "ico", "png", "jpg", "jpeg", "gif", "svg", "webp",
        "avif", "bmp", "htm", "md", "log", "ini", "inc", "bak", "sql", "sh", "yml", "yaml", "lock", "dist", "tmp",
        "map", "zip", "apk", "exe", "pdf", "mp4", "mp3", "webm", "woff", "woff2", "ttf", "eot", "csv", "rss", "atom",
        "env", "git", "conf", "cfg", "bat", "cmd", "pem", "key", "crt", "htaccess", "htpasswd"
    )

    /** An ending of the user's own, with the dot: ".xyz9". */
    fun isValidCustomSuffix(suffix: String): Boolean {
        if (!suffix.startsWith(".")) return false
        val body = suffix.removePrefix(".")
        if (body.length < MIN_CUSTOM_ENDING || body.length > MAX_CUSTOM_ENDING) return false
        if (!body.all { it in 'a'..'z' || it in '0'..'9' }) return false
        return body !in RESERVED_ENDINGS
    }

    /** What the user typed for an own ending, made into the form ".xyz" (lower case, no spaces / dots inside). */
    fun cleanCustomSuffix(input: String): String {
        val body = input.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(MAX_CUSTOM_ENDING)
        return if (body.isEmpty()) "" else ".$body"
    }

    fun normalizeName(input: String): String = input.trim().lowercase()

    /** The reason why [name] + [suffix] cannot be used, or null when it is fine (availability is checked separately). */
    fun problem(
        name: String,
        suffix: String,
        extraBlocked: Collection<String> = emptyList(),
        allowCustomEnding: Boolean = false
    ): String? {
        val n = normalizeName(name)
        if (n.isEmpty()) return "Type a name for your website."
        if (n.any { it.isWhitespace() }) return "Spaces are not allowed. Use - instead."
        if (!n.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
            return "Only letters, numbers and - are allowed (no dots, @, # or other symbols)."
        }
        if (n.length < MIN_NAME) return "Use at least $MIN_NAME characters."
        if (n.length > MAX_NAME) return "Use at most $MAX_NAME characters."
        if (n.startsWith("-") || n.endsWith("-") || n.contains("--")) return "Do not start or end the name with - and do not use -- ."
        if (suffix !in SUFFIXES && !(allowCustomEnding && isValidCustomSuffix(suffix))) {
            return if (allowCustomEnding) "Use 2 to 10 letters or numbers for your own ending." else "Choose one of the endings."
        }
        val parts = n.split('-').filter { it.isNotEmpty() }
        if (n in EXTRA_RESERVED || parts.any { it in EXTRA_RESERVED }) return "This name is reserved. Choose another one."
        if (BlockedWords.isBlocked(n, extraBlocked)) return "This name is not allowed. Choose another one."
        return null
    }

    /** Firebase key of the link: "mysite" + ".ivu.com" -> "mysite,ivu,com" (no dots, so it is a valid key). */
    fun key(name: String, suffix: String): String =
        normalizeName(name) + "," + suffix.removePrefix(".").replace('.', ',')

    /** What the visitor types: "mysite.ivu.com". */
    fun address(name: String, suffix: String): String = normalizeName(name) + suffix

    /**
     * The link of the site. It ends with "/": some web servers refuse an address that ends like a file name such as
     * ".com" (a 403 "Forbidden"), and the "/" also makes the relative links of the site work.
     */
    fun url(name: String, suffix: String): String = "https://$SITE/${address(name, suffix)}/"

    /** True for a hosting key such as "mysite,ivu,com". */
    fun isHostingKey(id: String): Boolean = id.contains(',')
}
