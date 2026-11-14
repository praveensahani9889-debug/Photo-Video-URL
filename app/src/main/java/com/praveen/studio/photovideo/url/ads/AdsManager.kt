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

/** One "show an ad, then continue" request (interstitial). */
internal class InterstitialRequest(val activity: WeakReference<Activity>, val onDone: () -> Unit) {
    var done = false
    var wait: Runnable? = null
    var safety: Runnable? = null
}

/**
 * Loads and shows ads. All state lives on the main thread; the ad network callbacks are moved there.
 *
 *  - the SDK starts once (and only when [AdsConfig] allows ads);
 *  - one ad at a time; the next ad is loaded in the background;
 *  - [showInterstitialThenContinue]: an ad shows by itself and the app continues afterwards (also when no ad can be
 *    shown: it never keeps the user waiting for long and never blocks the app);
 *  - rewarded ads (Copy / Share) are ready but switched off by default (see [AdsConfig]);
 *  - every technical problem is handled (never a crash, never a fake reward).
 */
object AdsManager {
    private const val TAG = "AdsManager"
    private const val REQUEST_TIMEOUT_MS = 15_000L      // rewarded: start of SDK + load of the ad
    private const val SHOW_START_TIMEOUT_MS = 10_000L   // ad accepted but never started
    private const val REWARD_SETTLE_MS = 30_000L        // safety: wait for "ad closed" after the reward callback
    private const val INTERSTITIAL_SAFETY_MS = 120_000L // safety: an ad that never reports "closed"
    private const val AD_MAX_AGE_MS = 15 * 60_000L      // loaded ads expire after a while
    private const val LOAD_RETRY_DELAY_MS = 30_000L     // pause after a failed preload

    private enum class InitState { NOT_STARTED, INITIALIZING, READY, FAILED }
    private enum class LoadState { IDLE, LOADING, LOADED }

    private val main = Handler(Looper.getMainLooper())
    private var backend: AdsBackend = UnityAdsBackend()
    private var appContext: Context? = null

    private var initState = InitState.NOT_STARTED
    private var initError: BackendError? = null
    private var showing = false

    // rewarded
    private var loadState = LoadState.IDLE
    private var loadedAt = 0L
    private var lastLoadFailureAt = 0L
    private var pending: RewardedRequest? = null

    // interstitial
    private var interstitialState = LoadState.IDLE
    private var interstitialLoadedAt = 0L
    private var lastInterstitialFailureAt = 0L
    private var interstitialError: BackendError? = null
    private var interstitialWaiter: InterstitialRequest? = null

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

    /** Loads ads in the background so they are ready when needed. */
    fun preloadAds() {
        runOnMain { preloadIfNeeded() }
    }

    fun preloadRewardedAd() = preloadAds()

    /** Short text about the state of the ads (shown in Settings to find out why no ad appears). */
    fun statusSummary(): String {
        val mode = if (BuildConfig.UNITY_TEST_MODE) "test ads" else "real ads"
        val sdk = when {
            !AdsConfigProvider.current.shouldInitializeSdk -> "ads are switched off"
            !backend.isAvailable -> "SDK not in this build"
            else -> when (initState) {
                InitState.NOT_STARTED -> "SDK not started"
                InitState.INITIALIZING -> "SDK starting"
                InitState.READY -> "SDK ready"
                InitState.FAILED -> "SDK failed (${initError?.code}: ${initError?.message})"
            }
        }
        val ad = when (interstitialState) {
            LoadState.IDLE -> interstitialError?.let { "no ad (${it.code}: ${it.message})" } ?: "no ad loaded yet"
            LoadState.LOADING -> "loading an ad"
            LoadState.LOADED -> "ad ready"
        }
        return "Ads: $mode, $sdk, $ad"
    }

    // ---------------------------------------------------------------------------------------------
    // Automatic ad (interstitial)
    // ---------------------------------------------------------------------------------------------

    /**
     * Shows an ad now (waiting up to [maxWaitMs] for it to load) and calls [onDone] exactly once, on the main thread,
     * when the ad was closed - or at once / after the wait when no ad can be shown.
     */
    fun showInterstitialThenContinue(activity: Activity, maxWaitMs: Long = 4_000L, onDone: () -> Unit) {
        runOnMain { startInterstitial(InterstitialRequest(WeakReference(activity), onDone), maxWaitMs) }
    }

    private fun startInterstitial(request: InterstitialRequest, maxWaitMs: Long) {
        val config = AdsConfigProvider.current
        when {
            !config.shouldShowInterstitialAds || !backend.isAvailable -> finishInterstitial(request)
            showing || interstitialWaiter != null -> finishInterstitial(request)
            !isOnline() -> finishInterstitial(request)
            else -> {
                if (appContext == null) request.activity.get()?.let { appContext = it.applicationContext }
                interstitialWaiter = request
                request.wait = Runnable { finishInterstitial(request) }.also { main.postDelayed(it, maxWaitMs) }
                if (initState == InitState.FAILED) initState = InitState.NOT_STARTED   // try again
                if (initState == InitState.NOT_STARTED) startInitIfNeeded() else proceedInterstitial(request)
            }
        }
    }

    private fun proceedInterstitial(request: InterstitialRequest) {
        if (interstitialWaiter !== request || request.done) return
        when (initState) {
            InitState.NOT_STARTED -> startInitIfNeeded()
            InitState.INITIALIZING -> Unit
            InitState.FAILED -> finishInterstitial(request)
            InitState.READY -> {
                if (interstitialState == LoadState.LOADED &&
                    System.currentTimeMillis() - interstitialLoadedAt > AD_MAX_AGE_MS
                ) {
                    interstitialState = LoadState.IDLE
                }
                when (interstitialState) {
                    LoadState.IDLE -> startInterstitialLoad()
                    LoadState.LOADING -> Unit
                    LoadState.LOADED -> showInterstitialAd(request)
                }
            }
        }
    }

    private fun startInterstitialLoad() {
        if (interstitialState == LoadState.LOADING) return
        interstitialState = LoadState.LOADING
        try {
            backend.loadInterstitial(BuildConfig.UNITY_INTERSTITIAL_PLACEMENT) { outcome ->
                runOnMain { onInterstitialLoaded(outcome) }
            }
        } catch (t: Throwable) {
            runOnMain { onInterstitialLoaded(BackendLoad.Failed(BackendError(-1, t.message ?: "load error"))) }
        }
    }

    private fun onInterstitialLoaded(outcome: BackendLoad) {
        when (outcome) {
            BackendLoad.Loaded -> {
                interstitialState = LoadState.LOADED
                interstitialLoadedAt = System.currentTimeMillis()
                interstitialError = null
                interstitialWaiter?.let { proceedInterstitial(it) }
            }
            is BackendLoad.Failed -> {
                interstitialState = LoadState.IDLE
                lastInterstitialFailureAt = System.currentTimeMillis()
                interstitialError = outcome.error
                Log.w(TAG, "Interstitial load failed: ${outcome.error.code} ${outcome.error.message}")
                interstitialWaiter?.let { finishInterstitial(it) }
            }
        }
    }

    private fun showInterstitialAd(request: InterstitialRequest) {
        val activity = request.activity.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            finishInterstitial(request)
            return
        }
        showing = true
        interstitialState = LoadState.IDLE   // the loaded ad is used up
        request.wait?.let { main.removeCallbacks(it) }
        request.safety = Runnable {
            showing = false
            finishInterstitial(request)
        }.also { main.postDelayed(it, INTERSTITIAL_SAFETY_MS) }

        try {
            backend.showInterstitial(activity, object : BackendShowCallbacks {
                override fun onStarted() = Unit
                override fun onRewardEarned() = Unit

                override fun onFinished(completed: Boolean) = runOnMain {
                    showing = false
                    finishInterstitial(request)
                    afterAd()
                }

                override fun onFailed(error: BackendError) = runOnMain {
                    Log.w(TAG, "Interstitial show failed: ${error.code} ${error.message}")
                    interstitialError = error
                    showing = false
                    finishInterstitial(request)
                    afterAd()
                }
            })
        } catch (t: Throwable) {
            showing = false
            finishInterstitial(request)
            afterAd()
        }
    }

    /** Continues the app exactly once. */
    private fun finishInterstitial(request: InterstitialRequest) {
        if (request.done) return
        request.done = true
        request.wait?.let { main.removeCallbacks(it) }
        request.safety?.let { main.removeCallbacks(it) }
        if (interstitialWaiter === request) interstitialWaiter = null
        try {
            request.onDone()
        } catch (t: Throwable) {
            Log.w(TAG, "Continue handler notice: ${t.message}")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rewarded ads (Copy / Share), off by default
    // ---------------------------------------------------------------------------------------------

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
            backend.loadRewarded(BuildConfig.UNITY_REWARDED_PLACEMENT) { outcome ->
                runOnMain { onLoadFinished(outcome) }
            }
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
                Log.w(TAG, "Rewarded load failed: ${outcome.error.code} ${outcome.error.message}")
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
            backend.showRewarded(activity, object : BackendShowCallbacks {
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
                    Log.w(TAG, "Rewarded show failed: ${error.code} ${error.message}")
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
        showing = false
        if (request.delivered) return
        finish(request, if (request.rewarded) RewardedResult.Rewarded else RewardedResult.NotCompleted)
        afterAd()
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

    // ---------------------------------------------------------------------------------------------
    // SDK start and preloading
    // ---------------------------------------------------------------------------------------------

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
                val rewardedRequest = pending
                val interstitialRequest = interstitialWaiter
                if (rewardedRequest != null) proceed(rewardedRequest)
                if (interstitialRequest != null) proceedInterstitial(interstitialRequest)
                if (rewardedRequest == null && interstitialRequest == null) preloadIfNeeded()
            }
            is BackendInit.Failure -> {
                initState = InitState.FAILED
                initError = outcome.error
                Log.w(TAG, "Ad SDK start failed: ${outcome.error.code} ${outcome.error.message}")
                pending?.let { finish(it, RewardedResult.Unavailable(classify(outcome.error), outcome.error.message)) }
                interstitialWaiter?.let { finishInterstitial(it) }
            }
        }
    }

    private fun afterAd() {
        main.postDelayed({ preloadIfNeeded() }, 500L)
    }

    private fun preloadIfNeeded() {
        val config = AdsConfigProvider.current
        if (!config.shouldInitializeSdk || !backend.isAvailable) return
        if (initState != InitState.READY || showing) return
        if (!isOnline()) return
        val now = System.currentTimeMillis()
        if (config.shouldShowRewardedAds && loadState == LoadState.IDLE && now - lastLoadFailureAt >= LOAD_RETRY_DELAY_MS) {
            startLoad()
        }
        if (config.shouldShowInterstitialAds && interstitialState == LoadState.IDLE &&
            now - lastInterstitialFailureAt >= LOAD_RETRY_DELAY_MS
        ) {
            startInterstitialLoad()
        }
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
