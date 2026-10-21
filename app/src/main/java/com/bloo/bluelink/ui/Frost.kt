package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonIconSize
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection


/**
 * The pill that says why something is frozen: a snowflake-cool lock and the reason, in the app's
 * standard [GlassSurface] chrome.
 */
@Composable
internal fun FrostMessage(message: String, modifier: Modifier = Modifier) {
    GlassSurface(
        shape = CircleShape,
        modifier = modifier,
        hazeState = LocalBackdropHaze.current,
    ) {
        Row(
            Modifier.padding(horizontal = GapSection, vertical = GapRow),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(ButtonIconSize))
            Spacer(Modifier.width(GapRow))
            RevealText(
                message,
                style = PrimaryTextStyle(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
