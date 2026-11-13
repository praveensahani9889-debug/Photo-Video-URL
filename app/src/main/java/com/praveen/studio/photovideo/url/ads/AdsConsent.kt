package com.praveen.studio.photovideo.url.ads

import android.content.Context

enum class AdsConsentState { UNKNOWN, PERSONALIZED_ALLOWED, PERSONALIZED_DECLINED }

/** What is passed to the ad SDK before it starts. */
data class AdPrivacy(
    /** true = only contextual (non-personalized) ads. */
    val nonBehavioral: Boolean,
    /** true / false when the user decided about personalized ads, null when there is no decision. */
    val userConsent: Boolean?
)

/**
 * Keeps the user's choice about personalized ads (on this phone only, nothing is sent anywhere by the app).
 *
 * The app has no consent screen yet, so the state stays UNKNOWN and only non-personalized ads are requested.
 * A consent screen (required in some regions) only has to call [setState].
 */
class AdsConsent(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ads_consent_prefs", Context.MODE_PRIVATE)

    fun state(): AdsConsentState =
        try {
            AdsConsentState.valueOf(prefs.getString(KEY_STATE, null) ?: AdsConsentState.UNKNOWN.name)
        } catch (_: IllegalArgumentException) {
            AdsConsentState.UNKNOWN
        }

    fun setState(state: AdsConsentState) {
        prefs.edit().putString(KEY_STATE, state.name).apply()
    }

    companion object {
        private const val KEY_STATE = "personalized_ads_state"

        fun toPrivacy(state: AdsConsentState): AdPrivacy = when (state) {
            AdsConsentState.PERSONALIZED_ALLOWED -> AdPrivacy(nonBehavioral = false, userConsent = true)
            AdsConsentState.PERSONALIZED_DECLINED -> AdPrivacy(nonBehavioral = true, userConsent = false)
            AdsConsentState.UNKNOWN -> AdPrivacy(nonBehavioral = true, userConsent = null)
        }
    }
}
