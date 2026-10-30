package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.wear.WatchSignIn
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton

/**
 * Signs the watch in so it works on its own. Shows whatever step [WatchSignIn] is on; renders
 * nothing while idle. The confirm step is the security check: the code here must match the one the
 * watch is showing before the accounts are sent to it.
 */
@Composable
internal fun WatchSignInDialog() {
    val context = LocalContext.current
    val state by WatchSignIn.state.collectAsStateWithLifecycle()
    if (state == WatchSignIn.State.Idle) return
    val current = state
    GlassAlertDialog(
        onDismissRequest = { WatchSignIn.dismiss(context) },
        icon = Icons.Filled.Watch,
        title = "Sign watch in",
        text = {
            when (current) {
                WatchSignIn.State.WaitingForWatch -> BodyMediumText("Waiting for the watch. Keep Bloo open on it.")
                is WatchSignIn.State.Confirm -> {
                    BodyMediumText("Does the watch show this code?")
                    RevealText(current.code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    BodyMediumText("Accounts are encrypted for that watch only. It then works without this phone.")
                }
                WatchSignIn.State.Sending -> BodyMediumText("Sending securely…")
                WatchSignIn.State.Done -> BodyMediumText("The watch is signed in.")
                is WatchSignIn.State.Failed -> BodyMediumText(current.message, color = MaterialTheme.colorScheme.error)
                WatchSignIn.State.Idle -> Unit
            }
        },
        buttons = {
            if (current is WatchSignIn.State.Confirm) {
                MorphActionButton("They match", Icons.Filled.Watch, { WatchSignIn.confirm(context) }, Modifier.fillMaxWidth(), emphasis = ButtonEmphasis.Confirm)
            }
            MorphTextButton(
                if (current == WatchSignIn.State.Done) "Done" else "Cancel",
                onClick = { WatchSignIn.dismiss(context) },
                modifier = Modifier.fillMaxWidth(),
                emphasis = if (current == WatchSignIn.State.Done) ButtonEmphasis.Confirm else ButtonEmphasis.Deny,
            )
        },
    )
}
