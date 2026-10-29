package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle

/** The published privacy policy, served from the project's GitHub Pages site. */
internal const val PRIVACY_POLICY_URL = "https://serverreset.github.io/Bloo/privacy.html"

/**
 * What happens to a person's credentials, said the same way wherever it is said: the sign-in screen and the Accounts card both use this
 * note, so the promise cannot drift apart. [brandLabel] names who the password is sent to (or null for "your manufacturer").
 */
@Composable
internal fun CredentialsPrivacyNote(brandLabel: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
            Icon(AppIcons.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            RevealText(
                "Your password never leaves this phone, except to sign in",
                style = PrimaryTextStyle(),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        }
        RevealText(
            "Your password and service PIN are encrypted on this phone with AES-256-GCM, a widely trusted standard. The key is sealed inside the Android Keystore, the phone's hardware security chip, so the app can read the " +
                "secrets back but nobody else can. Bloo has no server, so no copy of your data is kept anywhere but this phone. " +
                "They go only to ${brandLabel ?: "your manufacturer"}, to sign in and send commands.",
            style = SecondaryTextStyle(),
            color = scheme.onSurfaceVariant,
        )
        MorphTextButton(
            text = "Read the privacy policy",
            onClick = { openUrl(context, PRIVACY_POLICY_URL) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            fillOnPress = false,
        )
    }
}
