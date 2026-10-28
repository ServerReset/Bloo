package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.openDeveloperOptions
import com.bloo.bluelink.data.requestBackgroundUnrestricted
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.SecondaryTextStyle

/**
 * Ordered troubleshooting steps covering the two different ways this bar can fail to show
 * correctly: not starting/updating reliably AT ALL (steps 1-2, background execution), and showing
 * but never promoting to a status-bar/lock-screen chip (steps 3-5) -- the second half is a failure
 * mode this app can neither detect nor fix from code past the first two steps, because every cause
 * after that lives outside the documented Android APIs (see [LiveCharge]'s class doc: all nine
 * code-checkable promotion conditions are satisfied unconditionally by [LiveCharge.update]).
 */
@Composable
internal fun LiveUpdateTroubleshootDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = AppIcons.Info,
        title = "Live update not showing?",
        text = {
            RevealText("A few things to check, in order:", style = SecondaryTextStyle())
            Spacer(Modifier.height(GapGroup))
            TroubleshootStep(
                1,
                "Not reliable, especially after charging starts? Tap \"Tap to fix\" above to allow background updates." +
                    if (isSamsung) " On Samsung, also allow Bloo under Settings → Battery → Background usage limits." else "",
            )
            TroubleshootStep(2, "Check \"Live charging updates\" is on and the car is charging.")
            TroubleshootStep(3, "Below Android 16 only the shade progress bar shows.")
            TroubleshootStep(4, "On Android 16+, tap \"Tap to fix\" above to allow Live Updates.")
            if (isSamsung) {
                TroubleshootStep(
                    5,
                    "Samsung phones have a SECOND, separate switch this app can't see or set: " +
                        "Settings → Developer options → a \"Live notifications\" toggle " +
                        "(exact wording varies by One UI version). If Developer options aren't " +
                        "enabled yet: Settings → About phone → tap \"Build number\" 7 times.",
                )
            }
        },
        buttons = {
            DialogButton("Allow background activity", { LiveCharge.requestBackgroundUnrestricted(context) }, ButtonEmphasis.Confirm)
            if (isSamsung) DialogButton("Open Developer options", { LiveCharge.openDeveloperOptions(context) })
            DialogButton("Close", onDismiss)
        },
    )
}

@Composable
internal fun TroubleshootStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(bottom = GapGroup)) {
        RevealText(
            "$number.",
            style = SecondaryTextStyle(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp),
        )
        RevealText(text, style = SecondaryTextStyle())
    }
}
