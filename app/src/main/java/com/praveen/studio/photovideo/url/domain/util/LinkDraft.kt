package com.praveen.studio.photovideo.url.domain.util

import com.praveen.studio.photovideo.url.data.model.LinkOptions

/** What the user has typed / switched on the Advanced options page. Every option starts switched off. */
data class LinkDraft(
    /** Custom address (the part after ivu.gt.tc/). Empty = keep the automatic link. */
    val slug: String = "",

    val expiryOn: Boolean = false,
    /** 0 = 1 hour, 1 = 24 hours, 2 = 7 days, 3 = 30 days, 4 = custom, 5 = keep the time that is already set. */
    val expiryPreset: Int = 1,
    val customAmount: String = "1",
    /** 0 = minutes, 1 = hours, 2 = days. */
    val customUnit: Int = 2,

    val passwordOn: Boolean = false,
    val password: String = "",

    val viewsOn: Boolean = false,
    val maxViews: String = "100",

    val bgOn: Boolean = false,
    val bgColor: String = "#1E293B",

    val buttonOn: Boolean = false,
    val buttonText: String = "",
    val buttonColor: String = LinkOptions.DEFAULT_BUTTON_COLOR,
    val buttonUrl: String = "",

    val allowDownload: Boolean = true,
    val pageTitle: String = "",
    val pageMessage: String = ""
) {
    companion object {
        const val PRESET_CUSTOM = 4
        const val PRESET_KEEP = 5
        val PRESET_MINUTES = longArrayOf(60L, 24L * 60L, 7L * 24L * 60L, 30L * 24L * 60L)
        val PRESET_LABELS = listOf("1 hour", "24 hours", "7 days", "30 days", "Custom")

        /** The page shows what is already saved for the link (editing a link that has options). */
        fun fromOptions(slug: String, o: LinkOptions): LinkDraft = LinkDraft(
            slug = slug,
            expiryOn = o.expiresAt > 0L,
            expiryPreset = if (o.expiresAt > 0L) PRESET_KEEP else 1,
            passwordOn = o.isPasswordProtected,
            viewsOn = o.maxViews > 0L,
            maxViews = if (o.maxViews > 0L) o.maxViews.toString() else "100",
            bgOn = o.bgColor.isNotBlank(),
            bgColor = o.bgColor.ifBlank { "#1E293B" },
            buttonOn = o.hasButton,
            buttonText = o.buttonText,
            buttonColor = o.buttonColor.ifBlank { LinkOptions.DEFAULT_BUTTON_COLOR },
            buttonUrl = o.buttonUrl,
            allowDownload = o.allowDownload,
            pageTitle = o.pageTitle,
            pageMessage = o.pageMessage
        )
    }
}

/** Turns the typed values into [LinkOptions], or an error text for the first wrong value. */
object LinkDraftMapper {

    sealed interface Result {
        data class Ok(val options: LinkOptions) : Result
        data class Error(val message: String) : Result
    }

    /**
     * [existing] = the options the link has now (the view counter and an existing password are kept).
     * The password is hashed here, so it never leaves this function in readable form.
     */
    fun toOptions(draft: LinkDraft, existing: LinkOptions = LinkOptions(), now: Long = System.currentTimeMillis()): Result {
        var expiresAt = 0L
        if (draft.expiryOn) {
            expiresAt = when (draft.expiryPreset) {
                LinkDraft.PRESET_KEEP -> existing.expiresAt
                LinkDraft.PRESET_CUSTOM -> {
                    val minutes = LinkOptionsRules.customExpiryMinutes(draft.customAmount, draft.customUnit)
                    LinkOptionsRules.expiryProblem(minutes)?.let { return Result.Error(it) }
                    now + minutes!! * 60_000L
                }
                else -> now + LinkDraft.PRESET_MINUTES[draft.expiryPreset.coerceIn(0, LinkDraft.PRESET_MINUTES.size - 1)] * 60_000L
            }
        }

        var hash = ""
        var salt = ""
        var iterations = 0
        var hasPassword = false
        if (draft.passwordOn) {
            if (draft.password.isNotEmpty()) {
                LinkOptionsRules.passwordProblem(draft.password)?.let { return Result.Error(it) }
                val hashed = PasswordHasher.hash(draft.password)
                hash = hashed.hash
                salt = hashed.salt
                iterations = hashed.iterations
                hasPassword = true
            } else if (existing.isPasswordProtected) {
                // the password is already set and was left empty: keep it
                hash = existing.passwordHash
                salt = existing.passwordSalt
                iterations = existing.passwordIter
                hasPassword = true
            } else {
                return Result.Error("Type the password, or switch the password option off.")
            }
        }

        var maxViews = 0L
        if (draft.viewsOn) {
            LinkOptionsRules.viewsProblem(draft.maxViews)?.let { return Result.Error(it) }
            maxViews = draft.maxViews.trim().toLong()
        }

        var bg = ""
        if (draft.bgOn) {
            LinkOptionsRules.colorProblem(draft.bgColor)?.let { return Result.Error(it) }
            bg = draft.bgColor.trim().uppercase()
        }

        var buttonText = ""
        var buttonColor = ""
        var buttonUrl = ""
        if (draft.buttonOn) {
            LinkOptionsRules.buttonTextProblem(draft.buttonText)?.let { return Result.Error(it) }
            LinkOptionsRules.colorProblem(draft.buttonColor)?.let { return Result.Error(it) }
            LinkOptionsRules.urlProblem(draft.buttonUrl)?.let { return Result.Error(it) }
            buttonText = draft.buttonText.trim()
            buttonColor = draft.buttonColor.trim().uppercase()
            buttonUrl = LinkOptionsRules.normalizeUrl(draft.buttonUrl)
        }

        val title = draft.pageTitle.trim()
        val message = draft.pageMessage.trim()
        if (title.length > LinkOptionsRules.MAX_TITLE) return Result.Error("The title can have at most ${LinkOptionsRules.MAX_TITLE} characters.")
        if (message.length > LinkOptionsRules.MAX_MESSAGE) return Result.Error("The message can have at most ${LinkOptionsRules.MAX_MESSAGE} characters.")

        return Result.Ok(
            LinkOptions(
                expiresAt = expiresAt,
                maxViews = maxViews,
                views = existing.views,
                passwordHash = hash,
                passwordSalt = salt,
                passwordIter = iterations,
                hasPassword = hasPassword,
                bgColor = bg,
                buttonText = buttonText,
                buttonColor = buttonColor,
                buttonUrl = buttonUrl,
                pageTitle = title,
                pageMessage = message,
                allowDownload = draft.allowDownload
            )
        )
    }
}
