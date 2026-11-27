package com.praveen.studio.photovideo.url.ads

/**
 * Switches that decide whether rewarded ads are used at all. The defaults are safe: ads on, no Premium, and the
 * original action (copy / share) still works when no ad can be shown.
 *
 * A future Premium system only has to call [AdsConfigProvider.update] with `premiumAdFree = true` AFTER a genuine,
 * verified entitlement was found. Nothing else in the Copy / Share code has to change.
 */
data class AdsConfig(
    val adsEnabled: Boolean = true,
    val premiumAdFree: Boolean = false,
    /** Copy / Share ask for a rewarded ad first. Off: Copy / Share work normally. */
    val rewardedAdsEnabled: Boolean = false,
    val allowActionWhenAdUnavailable: Boolean = true,
    /** An ad is shown by itself when a link was created, before the result page. */
    val interstitialAdsEnabled: Boolean = true
) {
    private val adsAllowed: Boolean get() = adsEnabled && !premiumAdFree

    /** The SDK is only started when at least one kind of ad can really be shown. */
    val shouldInitializeSdk: Boolean get() = adsAllowed && (rewardedAdsEnabled || interstitialAdsEnabled)

    /** Copy / Share ask for a rewarded ad only when this is true; otherwise they work normally. */
    val shouldShowRewardedAds: Boolean get() = adsAllowed && rewardedAdsEnabled

    /** The automatic ad after a link was created. */
    val shouldShowInterstitialAds: Boolean get() = adsAllowed && interstitialAdsEnabled
}

object AdsConfigProvider {
    @Volatile
    private var base: AdsConfig = AdsConfig()

    /** Set by the account plan: a plan without ads (Premium 399, team accounts) switches every ad off. */
    @Volatile
    var planAdFree: Boolean = false

    val current: AdsConfig
        get() = if (planAdFree && !base.premiumAdFree) base.copy(premiumAdFree = true) else base

    fun update(config: AdsConfig) {
        base = config
    }
}
