package com.praveen.studio.photovideo.url.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.praveen.studio.photovideo.url.ads.AdsConfigProvider
import com.praveen.studio.photovideo.url.ads.AdsManager
import com.praveen.studio.photovideo.url.ads.AdFailureReason
import com.praveen.studio.photovideo.url.ads.GateDecision
import com.praveen.studio.photovideo.url.ads.RewardedOutcomePolicy
import com.praveen.studio.photovideo.url.ads.RewardedRequest
import com.praveen.studio.photovideo.url.ads.RewardedResult
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import java.lang.ref.WeakReference

/** The actions that ask for a rewarded ad first. */
enum class GatedAction(val question: String, val doneText: String) {
    COPY_LINK("Watch a short ad to copy this link?", "copied"),
    SHARE_LINK("Watch a short ad to share this link?", "shared")
}

internal sealed interface GateUi {
    data object Idle : GateUi
    data class Confirm(val action: GatedAction) : GateUi
    data class Loading(val action: GatedAction) : GateUi
}

/**
 * Sits between a button (Copy / Share) and the original action. The original action runs only after the rewarded ad
 * was completed, or when no ad can be shown for a technical reason (see [RewardedOutcomePolicy]).
 * It is separate from the viewer UI: the viewer only calls [request] and shows [RewardedActionDialogs].
 */
@Stable
class RewardedActionGate internal constructor(private val appContext: Context) {
    internal var ui: GateUi by mutableStateOf(GateUi.Idle)
        private set

    internal var activityRef: WeakReference<Activity>? = null

    private var pendingAction: (() -> Unit)? = null
    private var request: RewardedRequest? = null
    private var disposed = false

    /** Called by the button. [onAllowed] is the ORIGINAL action (copy / share); it runs at most once. */
    fun request(action: GatedAction, onAllowed: () -> Unit) {
        if (ui != GateUi.Idle) return   // a dialog or an ad is already open: no duplicates
        if (!AdsConfigProvider.current.shouldShowRewardedAds) {
            onAllowed()
            return
        }
        pendingAction = onAllowed
        ui = GateUi.Confirm(action)
    }

    internal fun watchAd(action: GatedAction) {
        val activity = activityRef?.get()
        if (activity == null || activity.isFinishing) {
            // no screen to show an ad on: treat like "ad unavailable"
            handle(action, RewardedResult.Unavailable(AdFailureReason.SHOW_FAILED, "no activity"))
            return
        }
        ui = GateUi.Loading(action)
        request = AdsManager.requestRewardedAd(activity) { result -> handle(action, result) }
    }

    internal fun cancel() {
        AdsManager.cancel(request)
        request = null
        pendingAction = null
        ui = GateUi.Idle
    }

    internal fun dispose() {
        disposed = true
        AdsManager.cancel(request)
        request = null
        pendingAction = null
    }

    private fun handle(action: GatedAction, result: RewardedResult) {
        if (disposed) return
        val allowedAction = pendingAction
        pendingAction = null          // makes sure the original action can never run twice
        request = null
        ui = GateUi.Idle

        when (val decision = RewardedOutcomePolicy.decide(result, AdsConfigProvider.current, action.doneText)) {
            GateDecision.RunAction -> allowedAction?.invoke()
            is GateDecision.Blocked -> toast(decision.message)
            is GateDecision.RunActionWithNotice -> {
                toast(decision.message)
                allowedAction?.invoke()
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun rememberRewardedActionGate(): RewardedActionGate {
    val context = LocalContext.current
    val gate = remember { RewardedActionGate(context.applicationContext) }
    gate.activityRef = WeakReference(context.findActivity())

    DisposableEffect(gate) {
        AdsManager.preloadRewardedAd()   // have an ad ready when the user taps
        onDispose { gate.dispose() }     // leaving the screen: nothing runs later
    }
    return gate
}

/** The small confirmation dialog and the "loading ad" dialog. Uses the app theme. */
@Composable
fun RewardedActionDialogs(gate: RewardedActionGate) {
    when (val state = gate.ui) {
        GateUi.Idle -> Unit

        is GateUi.Confirm -> AlertDialog(
            onDismissRequest = { gate.cancel() },
            text = { Text(state.action.question, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = { gate.watchAd(state.action) },
                    modifier = Modifier.testTag("ad_watch_btn")
                ) { Text("Watch Ad") }
            },
            dismissButton = {
                TextButton(
                    onClick = { gate.cancel() },
                    modifier = Modifier.testTag("ad_cancel_btn")
                ) { Text("Cancel") }
            }
        )

        is GateUi.Loading -> AlertDialog(
            onDismissRequest = { gate.cancel() },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CyanPrimary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Loading ad...", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { gate.cancel() }) { Text("Cancel") } }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
