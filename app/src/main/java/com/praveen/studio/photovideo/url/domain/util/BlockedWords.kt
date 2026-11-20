package com.praveen.studio.photovideo.url.domain.util

/**
 * Names that cannot be used as a custom link (ivu.gt.tc/<name>): the owner's own names, pages of the website, abusive
 * words and words for illegal content. The developer can add more words at any time without an update, in Firebase
 * `app/backend/blockedWords` (words separated by commas).
 */
object BlockedWords {

    /** Whole names (or whole parts between "-" / "_") that are reserved: website pages, owner names, common routes. */
    private val RESERVED = setOf(
        // owner / brand
        "admin", "administrator", "praveen", "praveenstudio", "praveen_studio", "photovideourl", "photovideo", "ivu",
        "ivuofficial", "ivugttc", "photovideourlapp", "official", "staff", "moderator", "owner", "founder", "ceo",
        "team", "mod", "superuser", "sysadmin", "webmaster", "postmaster", "hostmaster",
        // website pages and system names (every page and folder of https://ivu.gt.tc)
        "about", "contact", "terms", "privacy", "policy", "help", "support", "faq", "legal", "cookies", "tos",
        "features", "feature", "disclaimer", "howitworks", "how", "thumb", "thumbs", "config", "cache", "errors",
        "api", "app", "apps", "www", "web", "site", "home", "index", "main", "static", "assets", "css", "js", "img",
        "favicon", "robots", "sitemap", "login", "logout", "signin", "signup", "register", "account", "dashboard",
        "settings", "download", "downloads", "view", "views", "share", "search", "mail", "email", "ftp", "root",
        "system", "server", "null", "undefined", "test", "demo", "error", "status", "health", "public", "private",
        "image", "images", "video", "videos", "document", "documents", "file", "files", "apk", "apks",
        "link", "links", "short", "url", "upload", "uploads", "update", "updates", "premium", "pricing",
        // names that can fool people (banks, payments, big companies, authorities)
        "security", "secure", "verify", "verified", "verification", "payment", "payments", "bank", "banking",
        "wallet", "paytm", "phonepe", "gpay", "googlepay", "upi", "lottery", "winner", "giveaway", "kyc",
        "refund", "billing", "helpline", "customercare", "google", "youtube", "facebook", "instagram", "whatsapp",
        "telegram", "twitter", "snapchat", "tiktok", "microsoft", "amazon", "flipkart", "apple", "gov",
        "government", "police", "cyber", "cybercrime", "anonymous"
    )

    /** Blocked anywhere inside a name (spaces, "-", "_" and number tricks like "a5s" are ignored). */
    private val CONTAINS = listOf(
        // owner names
        "admin", "praveen",
        // abusive words (English)
        "fuck", "shit", "bitch", "bastard", "asshole", "dickhead", "pussy", "cunt", "whore", "slut",
        "nigger", "nigga", "faggot", "motherfucker", "cocksucker", "jackass", "dumbass", "wanker", "tranny",
        "retarded", "scumbag", "douchebag", "shithead", "bullshit",
        // abusive words (Hindi / Urdu in English letters)
        "madarchod", "maderchod", "mderchod", "behenchod", "behnchod", "bhenchod", "bahenchod", "chutiya", "chutiye",
        "chutiyapa", "chutmarike", "gandu", "gaandu", "bhosdi", "bhosad", "bhosdike", "bhosdika", "bhosdiwala",
        "bhosadi", "raand", "randwa", "randikhana", "lavda", "lawda", "harami", "haramkhor", "haramzada", "haramzadi",
        "kamina", "kamine", "kutiya", "bhadwa", "bhadwe", "bhadva", "chinaal", "jhaatu", "jhatu", "lundfakir",
        // sexual content
        "porn", "xnxx", "xvideos", "pornhub", "nudes", "hentai", "sexvideo", "onlyfans", "blowjob", "handjob",
        "rimjob", "cumshot", "bukkake", "gangbang", "creampie", "deepthroat", "masturbat", "orgasm", "sexchat",
        "xhamster", "youporn", "redtube", "brazzers", "bangbros", "chaturbate", "stripchat", "camgirl", "callgirl",
        "prostitute", "nudepic",
        // child abuse, violence, terror
        "childporn", "kidporn", "pedophile", "paedophile", "jailbait", "childabuse", "incest", "rapist", "terrorist",
        "bombmaking", "killyou", "alqaeda",
        // drugs, crime, fraud
        "cocaine", "heroin", "narcotic", "marijuana", "methamphetamine", "phishing", "phisher", "scammer", "fakeid",
        "darkweb", "freerecharge", "freemoney", "moneyhack", "upihack", "bankhack", "paytmhack", "otpbypass",
        "otpbomber", "smsbomber", "callbomber", "cardingtool", "creditcarddump"
    )

    /** Short or easily mistaken words: blocked only as a whole name or a whole part ("sex", "cp"), so normal names stay free. */
    private val EXACT_ONLY = setOf(
        "sex", "sexy", "xxx", "cp", "csam", "loli", "rape", "ass", "dick", "fag", "cock", "tits", "anal", "bomb", "nude",
        "kill", "meth", "weed", "isis", "nazi", "scam", "fraud", "hack", "crack", "cvv", "otp", "lund", "gand",
        "chut", "bsdk", "fuk", "randi", "lauda", "retard", "pedo", "paedo", "carding", "boobs", "jihad",
        "twat", "prick", "milf", "hooker", "escort", "chod", "chodu", "chodna", "lodu", "loda", "kutta", "kutte",
        "saala", "sala", "tatti", "gaand", "hijda", "mdma", "lsd", "ganja", "charas", "bhosda", "bkl", "mkc"
    )

    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b', '$' to 's', '@' to 'a')

    /** True when [slug] (already lower case) must not be used. [extra] = words from Firebase `blockedWords`. */
    fun isBlocked(slug: String, extra: Collection<String> = emptyList()): Boolean {
        val name = slug.lowercase()
        val parts = name.split('-', '_').filter { it.isNotEmpty() }
        val compact = name.replace("-", "").replace("_", "")

        if (name in RESERVED || compact in RESERVED) return true

        // number tricks: "adm1n", "a55hole" -> compare the readable version too (1 can mean i or l)
        val variants = linkedSetOf(compact, leet(compact, 'i'), leet(compact, 'l'))
        val partVariants = parts.flatMap { listOf(it, leet(it, 'i'), leet(it, 'l')) }.toSet()

        if (partVariants.any { it in EXACT_ONLY }) return true
        if (variants.any { v -> CONTAINS.any { v.contains(it) } }) return true

        val remote = extra.map { it.trim().lowercase().replace("-", "").replace("_", "") }.filter { it.length >= 2 }
        if (remote.isNotEmpty() && variants.any { v -> remote.any { v.contains(it) } }) return true
        return false
    }

    private fun leet(text: String, oneMeans: Char): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(if (c == '1') oneMeans else (LEET[c] ?: c))
        }
        return sb.toString()
    }
}
