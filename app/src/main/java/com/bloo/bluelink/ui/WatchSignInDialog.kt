package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.wear.WatchSignIn

/**
 * Signs the watch in so it works on its own. Shows whatever step [WatchSignIn] is on; renders
 * nothing while idle.
 */
@Composable
internal fun WatchSignInDialog() {
    val context = LocalContext.current
    val state by WatchSignIn.state.collectAsStateWithLifecycle()
    if (state == WatchSignIn.State.Idle) return
    val current = state
    GlassAlertDialog(
        onDismissRequest = WatchSignIn::dismiss,
        icon = Icons.Filled.Watch,
        title = "Sign watch in",
        text = {
            when (current) {
                WatchSignIn.State.WaitingForWatch -> BodyMediumText("Waiting for the watch. Keep Bloo open on it.")
                is WatchSignIn.State.Confirm -> {
                    BodyMediumText("Does the watch show this code?")
                    Text(current.code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    BodyMediumText("Your accounts are encrypted so only that watch can read them, then sent. It will work without this phone afterwards.")
                }
                WatchSignIn.State.Sending -> BodyMediumText("Sending securely…")
                WatchSignIn.State.Done -> BodyMediumText("The watch is signed in and can run your car on its own.")
                is WatchSignIn.State.Failed -> BodyMediumText(current.message, color = MaterialTheme.colorScheme.error)
                WatchSignIn.State.Idle -> Unit
            }
        },
        buttons = {
            if (current is WatchSignIn.State.Confirm) {
                MorphActionButton("They match", Icons.Filled.Watch, { WatchSignIn.confirm(context) }, Modifier.fillMaxWidth())
            }
            MorphTextButton(
                if (current == WatchSignIn.State.Done) "Done" else "Cancel",
                onClick = WatchSignIn::dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
