package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.update.UpdateRuntime
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton

/**
 * The two Google Play links, wherever the app points at Play: signing up for the testing (the testers' group,
 * which is what puts an account on the list), and the app's own Play Store listing. Both, always -- someone who
 * has joined wants the listing, and someone who has not needs the sign-up first.
 */
@Composable
internal fun PlayStoreLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ActionRow(modifier.fillMaxWidth()) {
        MorphTextButton(
            "Join testing",
            onClick = { openUrl(context, UpdateRuntime.JOIN_TESTING_URL) },
            icon = Icons.Filled.GroupAdd,
        )
        MorphTextButton(
            "Play Store",
            onClick = { openUrl(context, UpdateRuntime.PLAY_LISTING_URL) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            emphasis = ButtonEmphasis.Confirm,
        )
    }
}
