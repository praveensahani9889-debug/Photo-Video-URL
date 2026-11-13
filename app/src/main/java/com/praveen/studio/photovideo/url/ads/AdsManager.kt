package com.praveen.studio.photovideo.url.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.praveen.studio.photovideo.url.BuildConfig
import java.lang.ref.WeakReference

/** One request for a rewarded ad. Created by [AdsManager.requestRewardedAd]. */
class RewardedRequest internal constructor(internal val callback: (RewardedResult) -> Unit) {
    internal var activity: WeakReference<Activity>? = null
    internal var cancelled = false
    internal var delivered = false
    internal var started = false
    internal var rewarded = false
    internal var timeout: Runnable? = null
    internal var settle: Runnable? = null
}

/**
 * Loads and shows rewarded ads. All state lives on the main thread; the ad network callbacks are moved there.
 *
 *  - the SDK starts once (and only when [AdsConfig] allows ads);
 *  - one ad at a time, one reward per ad;
 *  - the next ad is loaded in the background after an ad was used;
 *  - leaving the screen cancels the request (nothing is executed later);
 *  - every technical problem becomes [RewardedResult.Unavailable] (never a reward, never a crash).
 */
object AdsManager {
    private const val TAG = "AdsManager"
    private const val REQUEST_TIMEOUT_MS = 15_000L      // start of SDK + load of the ad
    private const val SHOW_START_TIMEOUT_MS = 10_000L   // ad accepted but never started
    private const val REWARD_SETTLE_MS = 30_000L        // safety: wait for "ad closed" after the reward callback
    private const val AD_MAX_AGE_MS = 15 * 60_000L      // loaded ads expire after a while
    private const val LOAD_RETRY_DELAY_MS = 30_000L     // pause after a failed preload

    private enum class InitState { NOT_STARTED, INITIALIZING, READY, FAILED }
    private enum class LoadState { IDLE, LOADING, LOADED }

    private val main = Handler(Looper.getMainLooper())
    private var backend: RewardedAdBackend = UnityRewardedBackend()
    private var appContext: Context? = null

    private var initState = InitState.NOT_STARTED
    private var initError: BackendError? = null
    private var loadState = LoadState.IDLE
    private var loadedAt = 0L
    private var lastLoadFailureAt = 0L
    private var lastLoadError: BackendError? = null
    private var showing = false
    private var pending: RewardedRequest? = null

    /** Call once from Application.onCreate. Does nothing when ads are switched off. */
    fun initialize(application: Application) {
        appContext = application.applicationContext
        val config = AdsConfigProvider.current
        if (!config.shouldInitializeSdk) {
            Log.d(TAG, "Ads are switched off, the ad SDK is not started")
            return
        }
        if (!backend.isAvailable) {
            Log.w(TAG, "Ad SDK is not part of this build")
            return
        }
        startInitIfNeeded()
    }

    /** Loads an ad in the background so it is ready when the user taps Copy / Share. */
    fun preloadRewardedAd() {
        runOnMain { preloadIfNeeded() }
    }

    /**
     * Asks for a rewarded ad. The [callback] is called once, on the main thread, unless the request is cancelled.
     */
    fun requestRewardedAd(activity: Activity, callback: (RewardedResult) -> Unit): RewardedRequest {
        val request = RewardedRequest(callback)
        runOnMain { startRequest(activity, request) }
        return request
    }

    /** The screen was closed: nothing may run for this request any more. */
    fun cancel(request: RewardedRequest?) {
        if (request == null) return
        runOnMain {
            request.cancelled = true
            if (pending === request && !showing) {
                clearTimers(request)
                pending = null
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun startRequest(activity: Activity, request: RewardedRequest) {
        val config = AdsConfigProvider.current
        when {
            !config.shouldShowRewardedAds ->
                deliverNow(request, RewardedResult.Unavailable(AdFailureReason.ADS_DISABLED))
            !backend.isAvailable ->
                deliverNow(request, RewardedResult.Unavailable(AdFailureReason.SDK_UNAVAILABLE))
            showing || pending != null ->
                deliverNow(request, RewardedResult.Unavailable(AdFailureReason.ALREADY_SHOWING))
            !isOnline() ->
                deliverNow(request, RewardedResult.Unavailable(AdFailureReason.NO_NETWORK))
            else -> {
                request.activity = WeakReference(activity)
                pending = request
                request.timeout = Runnable {
                    if (!showing) finish(request, RewardedResult.Unavailable(AdFailureReason.TIMEOUT))
                }.also { main.postDelayed(it, REQUEST_TIMEOUT_MS) }

                if (appContext == null) appContext = activity.applicationContext
                if (initState == InitState.FAILED) initState = InitState.NOT_STARTED   // try again (maybe online now)
                if (initState == InitState.NOT_STARTED) startInitIfNeeded() else proceed(request)
            }
        }
    }

    private fun startInitIfNeeded() {
        if (initState != InitState.NOT_STARTED) return
        val context = appContext ?: return
        if (BuildConfig.UNITY_TEST_MODE && !BuildConfig.DEBUG) {
            Log.w(TAG, "WARNING: test ads are on in a non-debug build")
        }
        initState = InitState.INITIALIZING
        val privacy = AdsConsent.toPrivacy(AdsConsent(context).state())
        try {
            backend.initialize(context, BuildConfig.UNITY_GAME_ID, BuildConfig.UNITY_TEST_MODE, privacy) { outcome ->
                runOnMain { onInitFinished(outcome) }
            }
        } catch (t: Throwable) {
            runOnMain { onInitFinished(BackendInit.Failure(BackendError(-1, t.message ?: "init error"))) }
        }
    }

    private fun onInitFinished(outcome: BackendInit) {
        when (outcome) {
            BackendInit.Success -> {
                initState = InitState.READY
                initError = null
                val request = pending
                if (request != null) proceed(request) else preloadIfNeeded()
            }
            is BackendInit.Failure -> {
                initState = InitState.FAILED
                initError = outcome.error
                Log.w(TAG, "Ad SDK start failed: ${outcome.error.code} ${outcome.error.message}")
                pending?.let { finish(it, RewardedResult.Unavailable(classify(outcome.error), outcome.error.message)) }
            }
        }
    }

    private fun proceed(request: RewardedRequest) {
        if (pending !== request) return
        if (request.cancelled) {
            clearTimers(request)
            pending = null
            return
        }
        when (initState) {
            InitState.NOT_STARTED -> startInitIfNeeded()
            InitState.INITIALIZING -> Unit
            InitState.FAILED -> finish(
                request,
                RewardedResult.Unavailable(classify(initError), initError?.message)
            )
            InitState.READY -> {
                if (loadState == LoadState.LOADED && System.currentTimeMillis() - loadedAt > AD_MAX_AGE_MS) {
                    loadState = LoadState.IDLE   // too old, load a fresh one
                }
                when (loadState) {
                    LoadState.IDLE -> startLoad()
                    LoadState.LOADING -> Unit
                    LoadState.LOADED -> showAd(request)
                }
            }
        }
    }

    private fun startLoad() {
        if (loadState == LoadState.LOADING) return
        loadState = LoadState.LOADING
        try {
            backend.load(BuildConfig.UNITY_REWARDED_PLACEMENT) { outcome -> runOnMain { onLoadFinished(outcome) } }
        } catch (t: Throwable) {
            runOnMain { onLoadFinished(BackendLoad.Failed(BackendError(-1, t.message ?: "load error"))) }
        }
    }

    private fun onLoadFinished(outcome: BackendLoad) {
        when (outcome) {
            BackendLoad.Loaded -> {
                loadState = LoadState.LOADED
                loadedAt = System.currentTimeMillis()
                pending?.let { proceed(it) }
            }
            is BackendLoad.Failed -> {
                loadState = LoadState.IDLE
                lastLoadFailureAt = System.currentTimeMillis()
                lastLoadError = outcome.error
                Log.w(TAG, "Ad load failed: ${outcome.error.code} ${outcome.error.message}")
                pending?.let { finish(it, RewardedResult.Unavailable(classify(outcome.error), outcome.error.message)) }
            }
        }
    }

    private fun showAd(request: RewardedRequest) {
        val activity = request.activity?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            finish(request, RewardedResult.Unavailable(AdFailureReason.SHOW_FAILED, "screen closed"))
            return
        }
        showing = true
        loadState = LoadState.IDLE   // the loaded ad is used up

        request.timeout?.let { main.removeCallbacks(it) }
        request.timeout = Runnable {
            if (!request.started) {
                showing = false
                // delivered once here; a late "closed" / "reward" callback of the network is ignored
                finish(request, RewardedResult.Unavailable(AdFailureReason.TIMEOUT))
            }
        }.also { main.postDelayed(it, SHOW_START_TIMEOUT_MS) }

        try {
            backend.show(activity, object : BackendShowCallbacks {
                override fun onStarted() = runOnMain {
                    request.started = true
                    request.timeout?.let { main.removeCallbacks(it) }
                }

                override fun onRewardEarned() = runOnMain {
                    request.rewarded = true
                    // the "ad closed" callback follows when the user closes the ad; this is only a safety net
                    request.settle = Runnable { onAdEnded(request) }.also { main.postDelayed(it, REWARD_SETTLE_MS) }
                }

                override fun onFinished(completed: Boolean) = runOnMain {
                    if (completed) request.rewarded = true
                    onAdEnded(request)
                }

                override fun onFailed(error: BackendError) = runOnMain {
                    Log.w(TAG, "Ad show failed: ${error.code} ${error.message}")
                    if (request.rewarded) {
                        onAdEnded(request)
                    } else {
                        showing = false
                        finish(request, RewardedResult.Unavailable(AdFailureReason.SHOW_FAILED, error.message))
                        afterAd()
                    }
                }
            })
        } catch (t: Throwable) {
            showing = false
            finish(request, RewardedResult.Unavailable(AdFailureReason.SHOW_FAILED, t.message))
            afterAd()
        }
    }

    private fun onAdEnded(request: RewardedRequest) {
        if (request.delivered) {
            showing = false
            return
        }
        showing = false
        finish(request, if (request.rewarded) RewardedResult.Rewarded else RewardedResult.NotCompleted)
        afterAd()
    }

    private fun afterAd() {
        main.postDelayed({ preloadIfNeeded() }, 500L)
    }

    private fun preloadIfNeeded() {
        val config = AdsConfigProvider.current
        if (!config.shouldShowRewardedAds || !backend.isAvailable) return
        if (initState != InitState.READY || showing || loadState != LoadState.IDLE) return
        if (System.currentTimeMillis() - lastLoadFailureAt < LOAD_RETRY_DELAY_MS) return
        if (!isOnline()) return
        startLoad()
    }

    /** Delivers a result exactly once; nothing is delivered for a cancelled request. */
    private fun finish(request: RewardedRequest, result: RewardedResult) {
        if (request.delivered) return
        request.delivered = true
        clearTimers(request)
        if (pending === request) pending = null
        if (request.cancelled) return
        try {
            request.callback(result)
        } catch (t: Throwable) {
            Log.w(TAG, "Result handler notice: ${t.message}")
        }
    }

    private fun deliverNow(request: RewardedRequest, result: RewardedResult) {
        if (request.delivered) return
        request.delivered = true
        if (request.cancelled) return
        try {
            request.callback(result)
        } catch (t: Throwable) {
            Log.w(TAG, "Result handler notice: ${t.message}")
        }
    }

    private fun clearTimers(request: RewardedRequest) {
        request.timeout?.let { main.removeCallbacks(it) }
        request.settle?.let { main.removeCallbacks(it) }
        request.timeout = null
        request.settle = null
    }

    private fun classify(error: BackendError?): AdFailureReason {
        if (error == null) return AdFailureReason.LOAD_FAILED
        val text = error.message.lowercase()
        val online = isOnline()
        return when {
            error.code == 52100 || text.contains("no fill") -> AdFailureReason.NO_FILL
            error.code == 2 || text.contains("timeout") || text.contains("timed out") -> AdFailureReason.TIMEOUT
            !online -> AdFailureReason.NO_NETWORK
            text.contains("network") || text.contains("connection") || text.contains("unable to resolve") ||
                text.contains("unreachable") || text.contains("blocked") -> AdFailureReason.POSSIBLY_BLOCKED
            else -> AdFailureReason.LOAD_FAILED
        }
    }

    private fun isOnline(): Boolean {
        val context = appContext ?: return true
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            true   // cannot tell: let the ad SDK find out
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
