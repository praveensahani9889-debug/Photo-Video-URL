package com.praveen.studio.photovideo.url.ads

import android.app.Activity
import android.content.Context

/** Connects [AdsManager] to [UnityAdsBridge] (Java, the only code that uses the Unity SDK). */
class UnityAdsBackend : AdsBackend {

    override val isAvailable: Boolean get() = UnityAdsBridge.AVAILABLE

    override fun initialize(
        context: Context,
        gameId: String,
        testMode: Boolean,
        privacy: AdPrivacy,
        onComplete: (BackendInit) -> Unit
    ) {
        UnityAdsBridge.initialize(
            gameId,
            testMode,
            privacy.nonBehavioral,
            privacy.userConsent,
            UnityAdsBridge.InitCallback { success, code, message ->
                onComplete(if (success) BackendInit.Success else BackendInit.Failure(BackendError(code, message)))
            }
        )
    }

    override fun loadRewarded(placementId: String, onComplete: (BackendLoad) -> Unit) {
        UnityAdsBridge.loadRewarded(placementId, loadCallback(onComplete))
    }

    override fun showRewarded(activity: Activity, callbacks: BackendShowCallbacks) {
        UnityAdsBridge.showRewarded(activity, showCallback(callbacks))
    }

    override fun loadInterstitial(placementId: String, onComplete: (BackendLoad) -> Unit) {
        UnityAdsBridge.loadInterstitial(placementId, loadCallback(onComplete))
    }

    override fun showInterstitial(activity: Activity, callbacks: BackendShowCallbacks) {
        UnityAdsBridge.showInterstitial(activity, showCallback(callbacks))
    }

    private fun loadCallback(onComplete: (BackendLoad) -> Unit) =
        UnityAdsBridge.LoadCallback { loaded, code, message ->
            onComplete(if (loaded) BackendLoad.Loaded else BackendLoad.Failed(BackendError(code, message)))
        }

    private fun showCallback(callbacks: BackendShowCallbacks) = object : UnityAdsBridge.ShowCallback {
        override fun onStarted() = callbacks.onStarted()
        override fun onRewarded() = callbacks.onRewardEarned()
        override fun onFinished(completed: Boolean) = callbacks.onFinished(completed)
        override fun onFailed(code: Int, message: String?) = callbacks.onFailed(BackendError(code, message ?: ""))
    }
}
