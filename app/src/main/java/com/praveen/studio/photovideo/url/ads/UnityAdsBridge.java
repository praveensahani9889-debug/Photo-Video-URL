package com.praveen.studio.photovideo.url.ads;

import android.app.Activity;

import com.unity3d.ads.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The only file that touches the Unity Ads SDK 4.19.0 (current, non deprecated API).
 * It is written in Java on purpose: the SDK documents the same calls for Java, and Java code does not depend on the
 * Kotlin opt-in marker of the SDK. The rest of the app only sees the small callbacks below.
 */
public final class UnityAdsBridge {
    private UnityAdsBridge() {
    }

    /** true when the Unity SDK is part of this build. */
    public static final boolean AVAILABLE = true;

    public interface InitCallback {
        void onResult(boolean success, int code, String message);
    }

    public interface LoadCallback {
        void onResult(boolean loaded, int code, String message);
    }

    public interface ShowCallback {
        void onStarted();

        void onRewarded();

        void onFinished(boolean completed);

        void onFailed(int code, String message);
    }

    private static RewardedAd rewardedAd;
    private static InterstitialAd interstitialAd;

    // ---------------------------------------------------------------------------------------------
    // Start
    // ---------------------------------------------------------------------------------------------

    public static void initialize(String gameId, boolean testMode, boolean nonBehavioral, Boolean userConsent,
                                  final InitCallback callback) {
        // privacy choices are set before the SDK starts
        UnityAds.setNonBehavioral(nonBehavioral);
        if (userConsent != null) {
            UnityAds.setUserConsent(userConsent.booleanValue());
        }

        InitializationConfiguration configuration = new InitializationConfiguration.Builder(gameId)
                .withTestMode(testMode)
                .build();
        InitializationListener listener = error -> {
            if (error == null) {
                callback.onResult(true, 0, "");
            } else {
                callback.onResult(false, errorCode(error), errorMessage(error));
            }
        };
        UnityAds.initialize(configuration, listener);
    }

    // ---------------------------------------------------------------------------------------------
    // Rewarded
    // ---------------------------------------------------------------------------------------------

    public static void loadRewarded(String placementId, final LoadCallback callback) {
        LoadConfiguration configuration = new LoadConfiguration.Builder(placementId).build();
        RewardedAd.load(configuration, (ad, error) -> {
            if (ad != null) {
                rewardedAd = ad;
                callback.onResult(true, 0, "");
            } else {
                rewardedAd = null;
                callback.onResult(false, errorCode(error), errorMessage(error));
            }
        });
    }

    public static void showRewarded(Activity activity, final ShowCallback callback) {
        final RewardedAd ad = rewardedAd;
        if (ad == null) {
            callback.onFailed(-1, "no rewarded ad is loaded");
            return;
        }
        rewardedAd = null; // an ad can be shown only once

        RewardedShowListener listener = new RewardedShowListener() {
            @Override
            public void onStarted(RewardedAd unityAd) {
                callback.onStarted();
            }

            @Override
            public void onClicked(RewardedAd unityAd) {
                // not needed
            }

            @Override
            public void onRewarded(RewardedAd unityAd) {
                callback.onRewarded();
            }

            @Override
            public void onCompleted(RewardedAd unityAd, ShowFinishState state) {
                callback.onFinished(state == ShowFinishState.COMPLETED);
            }

            @Override
            public void onFailed(RewardedAd unityAd, UnityAdsError error) {
                callback.onFailed(errorCode(error), errorMessage(error));
            }
        };
        try {
            invokeShow(ad, activity, new ShowConfiguration.Builder().build(), listener);
        } catch (Throwable t) {
            callback.onFailed(-1, String.valueOf(t.getMessage()));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Interstitial (shown by itself, e.g. when a link was created)
    // ---------------------------------------------------------------------------------------------

    public static void loadInterstitial(String placementId, final LoadCallback callback) {
        LoadConfiguration configuration = new LoadConfiguration.Builder(placementId).build();
        InterstitialAd.load(configuration, (ad, error) -> {
            if (ad != null) {
                interstitialAd = ad;
                callback.onResult(true, 0, "");
            } else {
                interstitialAd = null;
                callback.onResult(false, errorCode(error), errorMessage(error));
            }
        });
    }

    public static void showInterstitial(Activity activity, final ShowCallback callback) {
        final InterstitialAd ad = interstitialAd;
        if (ad == null) {
            callback.onFailed(-1, "no interstitial ad is loaded");
            return;
        }
        interstitialAd = null; // an ad can be shown only once

        InterstitialShowListener listener = new InterstitialShowListener() {
            @Override
            public void onStarted(InterstitialAd unityAd) {
                callback.onStarted();
            }

            @Override
            public void onClicked(InterstitialAd unityAd) {
                // not needed
            }

            @Override
            public void onCompleted(InterstitialAd unityAd, ShowFinishState state) {
                callback.onFinished(true);
            }

            @Override
            public void onFailed(InterstitialAd unityAd, UnityAdsError error) {
                callback.onFailed(errorCode(error), errorMessage(error));
            }
        };
        try {
            invokeShow(ad, activity, new ShowConfiguration.Builder().build(), listener);
        } catch (Throwable t) {
            callback.onFailed(-1, String.valueOf(t.getMessage()));
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * The 4.19.0 guide shows show(activity, config, listener) while the API reference shows show(config, listener).
     * Both are looked up, so the build works with either form.
     */
    private static void invokeShow(Object ad, Activity activity, ShowConfiguration configuration, Object listener)
            throws Throwable {
        Method withActivity = null;
        Method withoutActivity = null;
        for (Method method : ad.getClass().getMethods()) {
            if (!"show".equals(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0].isInstance(activity) && types[1].isInstance(configuration)
                    && types[2].isInstance(listener)) {
                withActivity = method;
            } else if (types.length == 2 && types[0].isInstance(configuration) && types[1].isInstance(listener)) {
                withoutActivity = method;
            }
        }
        try {
            if (withActivity != null) {
                withActivity.invoke(ad, activity, configuration, listener);
            } else if (withoutActivity != null) {
                withoutActivity.invoke(ad, configuration, listener);
            } else {
                throw new NoSuchMethodException("show() was not found on " + ad.getClass().getName());
            }
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static int errorCode(UnityAdsError error) {
        return error == null ? -1 : error.getCode();
    }

    private static String errorMessage(UnityAdsError error) {
        return error == null ? "unknown error" : String.valueOf(error.getMessage());
    }
}
