package com.praveen.studio.photovideo.url.ads

import android.app.Activity
import android.content.Context
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.LoadConfiguration
import com.unity3d.ads.RewardedAd
import com.unity3d.ads.RewardedShowListener
import com.unity3d.ads.ShowConfiguration
import com.unity3d.ads.ShowFinishState
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsError
import com.unity3d.ads.UnityAdsExperimental

/**
 * Unity Ads SDK 4.19.0, current (non deprecated) API:
 * InitializationConfiguration + UnityAds.initialize, RewardedAd.load(LoadConfiguration), RewardedAd.show(activity,
 * ShowConfiguration, RewardedShowListener). This is the only file that touches the Unity SDK.
 */
@OptIn(UnityAdsExperimental::class)
class UnityRewardedBackend : RewardedAdBackend {

    private var rewardedAd: RewardedAd? = null

    override val isAvailable: Boolean = true

    override fun initialize(
        context: Context,
        gameId: String,
        testMode: Boolean,
        privacy: AdPrivacy,
        onComplete: (BackendInit) -> Unit
    ) {
        // privacy choices must be set before the SDK starts
        UnityAds.nonBehavioral = privacy.nonBehavioral
        privacy.userConsent?.let { UnityAds.userConsent = it }

        val configuration = InitializationConfiguration.Builder(gameId)
            .withTestMode(testMode)
            .build()
        UnityAds.initialize(configuration, InitializationListener { error ->
            if (error == null) {
                onComplete(BackendInit.Success)
            } else {
                onComplete(BackendInit.Failure(BackendError(error.code, error.message)))
            }
        })
    }

    override fun load(placementId: String, onComplete: (BackendLoad) -> Unit) {
        val loadConfiguration = LoadConfiguration.Builder(placementId).build()
        RewardedAd.load(loadConfiguration) { ad, error ->
            if (ad != null) {
                rewardedAd = ad
                onComplete(BackendLoad.Loaded)
            } else {
                rewardedAd = null
                onComplete(BackendLoad.Failed(BackendError(error?.code ?: -1, error?.message ?: "unknown load error")))
            }
        }
    }

    override fun show(activity: Activity, callbacks: BackendShowCallbacks) {
        val ad = rewardedAd
        if (ad == null) {
            callbacks.onFailed(BackendError(-1, "no ad is loaded"))
            return
        }
        rewardedAd = null   // an ad can be shown only once

        ad.show(activity, ShowConfiguration.Builder().build(), object : RewardedShowListener {
            override fun onStarted(unityAd: RewardedAd) {
                callbacks.onStarted()
            }

            override fun onClicked(unityAd: RewardedAd) {
                // not needed
            }

            override fun onRewarded(unityAd: RewardedAd) {
                callbacks.onRewardEarned()
            }

            override fun onCompleted(unityAd: RewardedAd, state: ShowFinishState) {
                callbacks.onFinished(state == ShowFinishState.COMPLETED)
            }

            override fun onFailed(unityAd: RewardedAd, error: UnityAdsError) {
                callbacks.onFailed(BackendError(error.code, error.message))
            }
        })
    }
}
