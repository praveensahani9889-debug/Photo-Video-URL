package com.praveen.studio.photovideo.url.ads

import android.app.Activity
import android.content.Context

/**
 * The ad network behind [AdsManager]. Only [UnityAdsBridge] (Java) knows the Unity SDK, so the rest of the app
 * never depends on it.
 */
interface AdsBackend {
    /** false when the SDK is not part of this build. */
    val isAvailable: Boolean

    fun initialize(
        context: Context,
        gameId: String,
        testMode: Boolean,
        privacy: AdPrivacy,
        onComplete: (BackendInit) -> Unit
    )

    fun loadRewarded(placementId: String, onComplete: (BackendLoad) -> Unit)

    /** Shows the ad that [loadRewarded] delivered (an ad can be shown only once). */
    fun showRewarded(activity: Activity, callbacks: BackendShowCallbacks)

    fun loadInterstitial(placementId: String, onComplete: (BackendLoad) -> Unit)

    /** Shows the ad that [loadInterstitial] delivered (an ad can be shown only once). */
    fun showInterstitial(activity: Activity, callbacks: BackendShowCallbacks)
}

data class BackendError(val code: Int, val message: String)

sealed interface BackendInit {
    data object Success : BackendInit
    data class Failure(val error: BackendError) : BackendInit
}

sealed interface BackendLoad {
    data object Loaded : BackendLoad
    data class Failed(val error: BackendError) : BackendLoad
}

interface BackendShowCallbacks {
    fun onStarted()

    /** The network reports that the reward was earned (ad watched to the end). */
    fun onRewardEarned()

    /** The ad is closed. [completed] is true only when it was watched to the end (not skipped). */
    fun onFinished(completed: Boolean)

    fun onFailed(error: BackendError)
}

enum class AdFailureReason {
    ADS_DISABLED,
    SDK_UNAVAILABLE,
    NOT_INITIALIZED,
    NO_NETWORK,
    POSSIBLY_BLOCKED,
    NO_FILL,
    TIMEOUT,
    LOAD_FAILED,
    SHOW_FAILED,
    ALREADY_SHOWING
}

sealed interface RewardedResult {
    /** Watched to the end: the action may run. */
    data object Rewarded : RewardedResult

    /** Skipped or closed: no reward. */
    data object NotCompleted : RewardedResult

    /** Technical problem (no ad could be shown). Never reported as a reward. */
    data class Unavailable(val reason: AdFailureReason, val detail: String? = null) : RewardedResult
}
