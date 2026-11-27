package com.praveen.studio.photovideo.url.domain.repository

import android.content.Context
import com.praveen.studio.photovideo.url.data.local.AccountStore
import com.praveen.studio.photovideo.url.data.local.InstallationIdProvider
import com.praveen.studio.photovideo.url.data.local.PreferencesManager
import com.praveen.studio.photovideo.url.data.model.AppControlStore
import com.praveen.studio.photovideo.url.data.model.LinkOptions
import com.praveen.studio.photovideo.url.data.model.PublicLinkLookup
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.data.local.RecoveryStore
import com.praveen.studio.photovideo.url.data.remote.HistoryRemoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.praveen.studio.photovideo.url.domain.util.HostingRules
import com.praveen.studio.photovideo.url.domain.util.LinkDraft
import com.praveen.studio.photovideo.url.domain.util.LinkDraftMapper
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules

/** Result of "Check availability" for a custom address. */
sealed interface NameCheck {
    data object Free : NameCheck
    data object Taken : NameCheck
    data class Invalid(val message: String) : NameCheck
    data class Error(val message: String) : NameCheck
}

/**
 * Saves the advanced options of a link: custom address, expiry, password, view limit, page design.
 * Firebase is only used when the user presses "Check availability" or "Apply" (never while typing).
 */
class LinkOptionsRepository(
    context: Context,
    private val firebase: FirebaseDatabaseManager = FirebaseDatabaseManager(),
    private val preferencesManager: PreferencesManager = PreferencesManager(context),
    installationIdProvider: InstallationIdProvider = InstallationIdProvider(context)
) {
    private val installationId: String = installationIdProvider.getInstallationId()

    /** Is the custom address allowed by the rules and still free? One Firebase read. */
    suspend fun checkName(input: String): NameCheck {
        val slug = LinkSlugRules.normalize(input)
        LinkSlugRules.problem(slug, AppControlStore.current.blockedWords)?.let { return NameCheck.Invalid(it) }
        if (slug.isEmpty()) return NameCheck.Invalid("Type the name you want.")
        val free = firebase.isLinkNameFree(slug)
        return free.fold(
            onSuccess = { if (it) NameCheck.Free else NameCheck.Taken },
            onFailure = { NameCheck.Error(it.message ?: "Could not check the name.") }
        )
    }

    /** The options saved for a link now (read from the public link, where the website keeps the view counter). */
    suspend fun loadOptions(record: UploadRecord): LinkOptions {
        return when (val lookup = firebase.lookupPublicLink(record.shortId)) {
            is PublicLinkLookup.Found -> lookup.record.options
            else -> record.options
        }
    }

    /** Website addresses with an ending (.com .in ...) belong to the Premium plans. Null when the plan allows them. */
    private fun hostingPlanProblem(): String? =
        if (AccountStore.plan.hostingEndings) null else "Website addresses with an ending are part of Premium."

    /** "Check availability" of a website address (name + ending), without writing anything. */
    suspend fun checkHosting(name: String, suffix: String): NameCheck {
        hostingPlanProblem()?.let { return NameCheck.Invalid(it) }
        HostingRules.problem(name, suffix, AppControlStore.current.blockedWords, AccountStore.plan.customEndings)
            ?.let { return NameCheck.Invalid(it) }
        return firebase.isLinkNameFree(HostingRules.key(name, suffix)).fold(
            onSuccess = { free -> if (free) NameCheck.Free else NameCheck.Taken },
            onFailure = { NameCheck.Error(it.message ?: "Could not check the name.") }
        )
    }

    /**
     * Website hosting, chosen ONCE right after the upload: the address (name + ending, optional: without a name the
     * automatic https://ivu.gt.tc/web_... stays) and how long the site stays online ([days], 0 = never expires; kept
     * inside the limits the developer set on the server). After this it cannot be changed again.
     */
    suspend fun applyHosting(record: UploadRecord, name: String, suffix: String, days: Int): Result<UploadRecord> {
        val control = AppControlStore.current
        var newId = record.shortId
        var displayUrl: String? = null
        if (name.isNotBlank()) {
            hostingPlanProblem()?.let { return Result.failure(IllegalArgumentException(it)) }
            HostingRules.problem(name, suffix, control.blockedWords, AccountStore.plan.customEndings)?.let {
                return Result.failure(IllegalArgumentException(it))
            }
            val key = HostingRules.key(name, suffix)
            val free = firebase.isLinkNameFree(key).getOrElse { return Result.failure(it) }
            if (!free) return Result.failure(IllegalArgumentException("This address is already taken. Choose another one."))
            newId = key
            displayUrl = HostingRules.url(name, suffix)
        }
        val maxDays = control.websiteMaxExpiryDays
        val safeDays = when {
            maxDays > 0 && (days <= 0 || days > maxDays) -> maxDays
            days < 0 -> 0
            else -> days
        }
        val expiresAt = if (safeDays > 0) System.currentTimeMillis() + safeDays * 86_400_000L else 0L
        val saved = firebase.applyLinkOptions(installationId, record, newId, LinkOptions(expiresAt = expiresAt), displayUrl)
        saved.onSuccess { updated ->
            preferencesManager.replaceRecord(record.shortId, updated)
            HistoryRemoteStore.replace(record.shortId, updated)
            withContext(Dispatchers.IO) { RecoveryStore.renameUpload(record.shortId, updated) }
        }
        return saved
    }

    /**
     * Same checks as [apply], but NOTHING is written: used by a text post, which is saved in the database only when
     * it is published (the Advanced options page opens before that). Returns the record with the chosen address and
     * options.
     */
    suspend fun prepare(record: UploadRecord, draft: LinkDraft, existing: LinkOptions): Result<UploadRecord> {
        val typed = LinkSlugRules.normalize(draft.slug)
        val newSlug = if (typed.isEmpty()) record.shortId else typed
        if (newSlug != record.shortId) {
            LinkSlugRules.problem(newSlug, AppControlStore.current.blockedWords)?.let {
                return Result.failure(IllegalArgumentException(it))
            }
            val free = firebase.isLinkNameFree(newSlug).getOrElse { return Result.failure(it) }
            if (!free) return Result.failure(IllegalArgumentException("This name is already taken. Choose another one."))
        }
        val options = when (val mapped = LinkDraftMapper.toOptions(draft, existing)) {
            is LinkDraftMapper.Result.Ok -> mapped.options
            is LinkDraftMapper.Result.Error -> return Result.failure(IllegalArgumentException(mapped.message))
        }
        val fullUrl = LinkSlugRules.fullUrl(newSlug)
        return Result.success(record.copy(shortId = newSlug, publicUrl = fullUrl, shortUrl = fullUrl, options = options))
    }

    /**
     * Validates the draft and saves it. A new address moves the link to https://ivu.gt.tc/<name>.
     * Returns the changed record; the History and the saved copy on the phone are updated at once.
     */
    suspend fun apply(
        record: UploadRecord,
        draft: LinkDraft,
        existing: LinkOptions,
        allowRename: Boolean = true
    ): Result<UploadRecord> {
        val typed = LinkSlugRules.normalize(draft.slug)
        val newSlug = if (typed.isEmpty()) record.shortId else typed

        // The address is chosen ONCE, on the options page that opens right after the upload. Later (also when that page
        // was skipped) it stays as it is, and a custom name that was created can never be changed.
        if (newSlug != record.shortId) {
            if (!LinkRules.isTypedId(record.shortId)) {
                return Result.failure(IllegalArgumentException("A custom name cannot be changed after it was created."))
            }
            if (!allowRename) {
                return Result.failure(IllegalArgumentException("The link address can only be chosen right after the upload."))
            }
        }

        if (newSlug != record.shortId) {
            LinkSlugRules.problem(newSlug, AppControlStore.current.blockedWords)?.let {
                return Result.failure(IllegalArgumentException(it))
            }
        }

        val options = when (val mapped = LinkDraftMapper.toOptions(draft, existing)) {
            is LinkDraftMapper.Result.Ok -> mapped.options
            is LinkDraftMapper.Result.Error -> return Result.failure(IllegalArgumentException(mapped.message))
        }

        // nothing chosen: nothing to save
        if (newSlug == record.shortId && options == existing) return Result.success(record)

        val saved = firebase.applyLinkOptions(installationId, record, newSlug, options)
        saved.onSuccess { updated ->
            preferencesManager.replaceRecord(record.shortId, updated)
            HistoryRemoteStore.replace(record.shortId, updated)
            withContext(Dispatchers.IO) { RecoveryStore.renameUpload(record.shortId, updated) }
        }
        return saved
    }
}
