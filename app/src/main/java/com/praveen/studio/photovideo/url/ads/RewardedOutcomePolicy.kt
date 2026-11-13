package com.praveen.studio.photovideo.url.ads

/** What Copy / Share do after the ad flow ended. */
sealed interface GateDecision {
    /** Run the original action. */
    data object RunAction : GateDecision

    /** Do not run the action; tell the user why. */
    data class Blocked(val message: String) : GateDecision

    /** The ad could not be shown for a technical reason: tell the user and run the action anyway. */
    data class RunActionWithNotice(val message: String) : GateDecision
}

/** Pure decision logic (no Android classes), so it can be unit tested. */
object RewardedOutcomePolicy {

    /** [doneText] is the past tense of the action, e.g. "copied" or "shared". */
    fun decide(result: RewardedResult, config: AdsConfig, doneText: String): GateDecision = when (result) {
        RewardedResult.Rewarded -> GateDecision.RunAction

        RewardedResult.NotCompleted ->
            GateDecision.Blocked("The ad was not completed, so the link was not $doneText.")

        is RewardedResult.Unavailable -> when (result.reason) {
            AdFailureReason.ADS_DISABLED -> GateDecision.RunAction
            AdFailureReason.ALREADY_SHOWING -> GateDecision.Blocked("An ad is already loading. Please wait a moment.")
            else -> {
                val reasonText = unavailableText(result.reason)
                if (config.allowActionWhenAdUnavailable) {
                    GateDecision.RunActionWithNotice("$reasonText Continuing without an ad.")
                } else {
                    GateDecision.Blocked("$reasonText Please try again later.")
                }
            }
        }
    }

    fun unavailableText(reason: AdFailureReason): String = when (reason) {
        AdFailureReason.NO_NETWORK -> "There is no internet connection for the ad."
        AdFailureReason.POSSIBLY_BLOCKED ->
            "The ad could not be loaded (an ad blocker or private DNS may be blocking ads)."
        AdFailureReason.NO_FILL -> "No ad is available right now."
        AdFailureReason.TIMEOUT -> "The ad took too long to load."
        else -> "Ads are not available right now."
    }
}
