package com.praveen.studio.photovideo.url.data.local

import android.content.Context
import com.praveen.studio.photovideo.url.ads.AdsConfigProvider
import com.praveen.studio.photovideo.url.data.model.AccountInfo
import com.praveen.studio.photovideo.url.data.model.AccountType
import com.praveen.studio.photovideo.url.data.model.PlanLimits
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The account type (Free / Premium 49 / Premium 399 / team) of this phone.
 *
 * The truth is the Firebase node `accounts/<account id>`, which only the owner of the app can change. A copy is saved
 * on the phone, so the plan is known at once when the app opens (also offline). Until the first answer arrives the
 * saved copy is used; a phone that never had a plan is Free.
 */
object AccountStore {
    private const val PREFS = "account_prefs"
    private const val KEY_TYPE = "type"
    private const val KEY_DATE = "purchase_date"
    private const val KEY_EXPIRES = "expires_at"

    private const val MIN_REFRESH_GAP_MS = 60_000L

    @Volatile
    private var lastRefreshAt = 0L

    private val _state = MutableStateFlow(AccountInfo())
    val state: StateFlow<AccountInfo> = _state.asStateFlow()

    val current: AccountInfo get() = _state.value

    /** What the account may do right now. */
    val plan: PlanLimits get() = _state.value.limits

    /** Reads the saved copy (fast, no network). Call once at app start. */
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val info = AccountInfo(
            type = AccountType.parse(prefs.getString(KEY_TYPE, null)),
            purchaseDate = prefs.getString(KEY_DATE, "").orEmpty(),
            expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        )
        publish(info)
    }

    /** Asks the server for the plan of this phone. Never throws; offline keeps the saved copy. */
    suspend fun refresh(context: Context, installationId: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < MIN_REFRESH_GAP_MS) return
        lastRefreshAt = now
        val fetched = try {
            FirebaseDatabaseManager().fetchAccount(installationId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TYPE, fetched.type.key)
            .putString(KEY_DATE, fetched.purchaseDate)
            .putLong(KEY_EXPIRES, fetched.expiresAt)
            .apply()
        publish(fetched)
    }

    private fun publish(info: AccountInfo) {
        _state.value = info
        AdsConfigProvider.planAdFree = !info.limits.showAds
    }
}
