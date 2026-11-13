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
    val rewardedAdsEnabled: Boolean = true,
    val allowActionWhenAdUnavailable: Boolean = true
) {
    /** The SDK is only started when ads can really be shown. */
    val shouldInitializeSdk: Boolean get() = adsEnabled && !premiumAdFree && rewardedAdsEnabled

    /** Copy / Share ask for a rewarded ad only when this is true; otherwise they work normally. */
    val shouldShowRewardedAds: Boolean get() = shouldInitializeSdk
}

object AdsConfigProvider {
    @Volatile
    var current: AdsConfig = AdsConfig()
        private set

    fun update(config: AdsConfig) {
        current = config
    }
}
