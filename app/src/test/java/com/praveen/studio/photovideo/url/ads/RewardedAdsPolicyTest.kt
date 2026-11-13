package com.praveen.studio.photovideo.url.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardedAdsPolicyTest {

    @Test
    fun defaultsAreSafe() {
        val config = AdsConfig()
        assertTrue(config.adsEnabled)
        assertFalse(config.premiumAdFree)
        assertTrue(config.rewardedAdsEnabled)
        assertTrue(config.allowActionWhenAdUnavailable)
        assertTrue(config.shouldShowRewardedAds)
        assertTrue(config.shouldInitializeSdk)
    }

    @Test
    fun adsOffMeansNoSdkAndNoAds() {
        val config = AdsConfig(adsEnabled = false)
        assertFalse(config.shouldInitializeSdk)
        assertFalse(config.shouldShowRewardedAds)
    }

    @Test
    fun premiumAdFreeMeansNoAds() {
        val config = AdsConfig(premiumAdFree = true)
        assertFalse(config.shouldInitializeSdk)
        assertFalse(config.shouldShowRewardedAds)
    }

    @Test
    fun rewardedAdsOffMeansCopyAndShareWorkNormally() {
        val config = AdsConfig(rewardedAdsEnabled = false)
        assertFalse(config.shouldShowRewardedAds)
    }

    @Test
    fun completedAdRunsTheAction() {
        val decision = RewardedOutcomePolicy.decide(RewardedResult.Rewarded, AdsConfig(), "copied")
        assertEquals(GateDecision.RunAction, decision)
    }

    @Test
    fun skippedOrClosedAdNeverRunsTheAction() {
        val decision = RewardedOutcomePolicy.decide(RewardedResult.NotCompleted, AdsConfig(), "shared")
        assertTrue(decision is GateDecision.Blocked)
    }

    @Test
    fun technicalFailureRunsTheActionWithANoticeWhenAllowed() {
        val result = RewardedResult.Unavailable(AdFailureReason.NO_FILL)
        val decision = RewardedOutcomePolicy.decide(result, AdsConfig(), "copied")
        assertTrue(decision is GateDecision.RunActionWithNotice)
    }

    @Test
    fun technicalFailureBlocksTheActionWhenFallbackIsOff() {
        val result = RewardedResult.Unavailable(AdFailureReason.TIMEOUT)
        val decision = RewardedOutcomePolicy.decide(result, AdsConfig(allowActionWhenAdUnavailable = false), "copied")
        assertTrue(decision is GateDecision.Blocked)
    }

    @Test
    fun anAlreadyRunningAdNeverStartsTheActionTwice() {
        val result = RewardedResult.Unavailable(AdFailureReason.ALREADY_SHOWING)
        val decision = RewardedOutcomePolicy.decide(result, AdsConfig(), "copied")
        assertTrue(decision is GateDecision.Blocked)
    }

    @Test
    fun consentDefaultsToNonPersonalizedAds() {
        val privacy = AdsConsent.toPrivacy(AdsConsentState.UNKNOWN)
        assertTrue(privacy.nonBehavioral)
        assertEquals(null, privacy.userConsent)
    }
}
