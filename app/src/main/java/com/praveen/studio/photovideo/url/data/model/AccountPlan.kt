package com.praveen.studio.photovideo.url.data.model

/**
 * The account type of a user. It is set by the owner of the app in the Firebase node `accounts/<account id>`
 * (field `type`), never by the app itself, so nobody can upgrade themselves.
 *
 *   free          Free plan (default when there is no entry)
 *   premium_49    Premium 49  (Silver badge)
 *   premium_399   Premium 399 (Gold badge)
 *   friend / moderator / admin   get everything of Premium 399, plus unlimited storage projects and no website limit
 */
enum class AccountType(val key: String, val label: String) {
    FREE("free", "Free"),
    PREMIUM_49("premium_49", "Premium 49"),
    PREMIUM_399("premium_399", "Premium 399"),
    FRIEND("friend", "Friend"),
    MODERATOR("moderator", "Moderator"),
    ADMIN("admin", "Admin");

    /** Plans that are bought (they can expire). The team accounts never expire. */
    val isPaid: Boolean get() = this == PREMIUM_49 || this == PREMIUM_399

    /** Team accounts: friend, moderator, admin. */
    val isTeam: Boolean get() = this == FRIEND || this == MODERATOR || this == ADMIN

    companion object {
        /** Reads the text typed in the console. Anything unknown is Free. */
        fun parse(raw: String?): AccountType {
            val t = raw.orEmpty().trim().lowercase().replace(' ', '_').replace('-', '_')
            return when (t) {
                "premium_49", "premium49", "plan_49", "49", "silver" -> PREMIUM_49
                "premium_399", "premium399", "plan_399", "399", "gold" -> PREMIUM_399
                "friend", "friends" -> FRIEND
                "moderator", "mod" -> MODERATOR
                "admin", "administrator" -> ADMIN
                else -> FREE
            }
        }
    }
}

/** What a plan allows. A negative number or [Int.MAX_VALUE] means "no limit". */
data class PlanLimits(
    val imagesPerDay: Int,
    val videosPerDay: Int,
    val documentsPerDay: Int,
    val apksPerDay: Int,
    val filesPerDay: Int,
    val textsPerDay: Int,
    /** One website every this many hours (0 = no waiting time). */
    val websiteEveryHours: Int,
    /** Password, expiry, view limit, page design ... after an upload. */
    val advancedOptions: Boolean,
    /** Website addresses with an ending such as .com .in .ai */
    val hostingEndings: Boolean,
    /** The user can type an ending of their own. */
    val customEndings: Boolean,
    /** How many Supabase projects can be added ([Int.MAX_VALUE] = unlimited). */
    val maxSupabase: Int,
    val showAds: Boolean,
    val premiumSupport: Boolean,
    val earlyAccess: Boolean
) {
    val unlimitedSupabase: Boolean get() = maxSupabase == Int.MAX_VALUE

    /** Uploads allowed per day for a category, or [UNLIMITED]. */
    fun dailyLimit(category: MediaCategory): Int = when (category) {
        MediaCategory.IMAGES -> imagesPerDay
        MediaCategory.VIDEOS -> videosPerDay
        MediaCategory.DOCUMENTS -> documentsPerDay
        MediaCategory.APKS -> apksPerDay
        MediaCategory.FILES -> filesPerDay
        MediaCategory.TEXT -> textsPerDay
        MediaCategory.WEB -> UNLIMITED
    }

    companion object {
        const val UNLIMITED = -1
    }
}

object Plans {
    private const val U = PlanLimits.UNLIMITED

    val FREE = PlanLimits(
        imagesPerDay = 3, videosPerDay = 2, documentsPerDay = 1, apksPerDay = 1, filesPerDay = 1, textsPerDay = 5,
        websiteEveryHours = 36,
        advancedOptions = false, hostingEndings = false, customEndings = false,
        maxSupabase = 1, showAds = true, premiumSupport = false, earlyAccess = false
    )

    val PREMIUM_49 = PlanLimits(
        imagesPerDay = 10, videosPerDay = 5, documentsPerDay = 3, apksPerDay = 2, filesPerDay = 2, textsPerDay = 10,
        websiteEveryHours = 12,
        advancedOptions = true, hostingEndings = true, customEndings = false,
        maxSupabase = 2, showAds = true, premiumSupport = false, earlyAccess = false
    )

    val PREMIUM_399 = PlanLimits(
        imagesPerDay = U, videosPerDay = U, documentsPerDay = U, apksPerDay = U, filesPerDay = U, textsPerDay = U,
        websiteEveryHours = 6,
        advancedOptions = true, hostingEndings = true, customEndings = true,
        maxSupabase = 5, showAds = false, premiumSupport = true, earlyAccess = true
    )

    /** Friend, moderator, admin: everything of Premium 399, without the website waiting time, unlimited projects. */
    val TEAM = PREMIUM_399.copy(websiteEveryHours = 0, maxSupabase = Int.MAX_VALUE)

    fun forType(type: AccountType): PlanLimits = when (type) {
        AccountType.FREE -> FREE
        AccountType.PREMIUM_49 -> PREMIUM_49
        AccountType.PREMIUM_399 -> PREMIUM_399
        AccountType.FRIEND, AccountType.MODERATOR, AccountType.ADMIN -> TEAM
    }
}

/**
 * The account of this phone as read from `accounts/<id>`:
 *  - type          see [AccountType]
 *  - purchaseDate  text such as "26 Nov 2026" (only shown, the owner types it when a plan is given)
 *  - expiresAt     optional, milliseconds. After this time a paid plan counts as Free again.
 */
data class AccountInfo(
    val type: AccountType = AccountType.FREE,
    val purchaseDate: String = "",
    val expiresAt: Long = 0L
) {
    /** The plan that really counts now (an expired paid plan is Free). */
    val effectiveType: AccountType
        get() = if (type.isPaid && expiresAt > 0L && System.currentTimeMillis() > expiresAt) AccountType.FREE else type

    val limits: PlanLimits get() = Plans.forType(effectiveType)

    val isFree: Boolean get() = effectiveType == AccountType.FREE
}
